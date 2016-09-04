/*
 * 游戏服务器，作为信息中转站
 */
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import mine.terminate.Terminable;
import mine.terminate.TerminationToken;

public class GameServer {
	//颜色常量
	private static final String BLACK = "black";
	private static final String WHITE = "white";
	
	//棋盘标志常量
	static final int BLACK_AVAILABLE = 1;
	static final int WHITE_AVAILABLE = 2;
	static final int BOTH_AVAILABLE = 3;
	static final int BLACK_FLAG = 4;
	static final int WHITE_FLAG = 5;
	static final int NULL_FLAG = 6;
	
	//所有客户端进程
	static private ArrayList<ClientRunner> all_client = new ArrayList<ClientRunner>();
	
	//计时任务
	Timer timer;
	MyTimerTask timer_task;
	
	//全局的statement
	static private Statement stmt;
	
	//大厅桌子数
	static final int table_sum = 4;
	
	//对战桌数组
	static private ArrayList<Table> all_table = new ArrayList<Table>();
	
	//for NIO
	private final static Logger logger = Logger.getLogger(GameServer.class.getName());
	private static Selector selector;
	
	
	
	//构造方法：各种初始化
	public GameServer() {
		//初始化对战桌
		for(int i = 0; i < table_sum; i++)
			all_table.add(new Table());		//注意：编号从0开始
	}
	
	// 向所有客户端进程发送消息
	public void notifyAllClient(Message message) {
		System.out.println("all_client.size(): " + all_client.size());
		for (ClientRunner client : all_client) {
			client.NIOSendMessage(message);
		}
	}
	
	public void connectDatabase() {
		String url = "jdbc:mysql://localhost:3306/test_1?"
				+ "user=root&useUnicode=true&characterEncoding=UTF8";
		try {
			// 连接
			Class.forName("com.mysql.jdbc.Driver");
			System.out.println("成功加载MySQL驱动程序");
			Connection con = DriverManager.getConnection(url);
			stmt = con.createStatement();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/*
	 * 内部类，等待连接线程
	 */
	
//	//等待连接线程
//	class AcceptRunner implements Runnable {
//		
//		public void run() {
//			try {
//				ServerSocket ss = new ServerSocket(5555);
//				System.out.println("accepter running !!!");
//
//				while (true) {
//					Socket s = ss.accept();
//					System.out.println("a client connected !!!");
//					ClientRunner new_client = new ClientRunner(s);
//					new Thread(new_client).start();
//					all_client.add(new_client);
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}
//
//	}

	//消息监视线程：①监视&处理各个client消息字节队列 ②生成消息对象加入client的消息队列并处理
	public class MessageMonitor implements Runnable {
		public boolean watching = true;

		public void run() {
			while(watching) {
				synchronized(all_client) {
					for(ClientRunner client : all_client) {
						tryTosetNextMessageLength(client);
						tryToReceiveNextMessage(client);
						tryToHandleNextMessage(client);
					}
				}
				try {
					TimeUnit.MICROSECONDS.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		//设置下一条待接收的message的长度
		public void tryTosetNextMessageLength(ClientRunner client) {
			//上一条设置的还未处理 || 新的4字节length未到达，直接返回
			if(client.next_message_length.get() > 0 || client.message_byte_q.size() < 4)
				return;
			//取对象长度
			byte[] length_bytes = new byte[4];
			for(int i = 0; i < 4; i++)
				length_bytes[i] = client.message_byte_q.poll();
			int length = BytesIntTransfer.Bytes2Int(length_bytes);
			System.out.println("下个对象长度： " + length);
			client.next_message_length.set(length);
		}
		
		//消息字节数据 ——>消息对象，放入client的消息队列
		public void tryToReceiveNextMessage(ClientRunner client) {
			//4字节长度标志 或 消息体未到达，直接返回
			if(client.next_message_length.get() < 0 
					|| client.message_byte_q.size() < client.next_message_length.get())
				return;
			
			//取出对象字节流&反序列化&分发
			byte[] obj_bytes = new byte[client.next_message_length.get()];
			for(int i = 0; i < client.next_message_length.get(); i++)
				obj_bytes[i] = client.message_byte_q.poll();
			Message new_message = (Message) SerializableUtil.toObject(obj_bytes);
			client.message_to_handle.add(new_message);	//加入待处理消息队列
			System.out.println("new_message.type: " + new_message.getType());
			//注意：恢复-1准备下个消息的接收
			client.next_message_length.set(-1);
		}
		
		//处理消息
		public void tryToHandleNextMessage(ClientRunner client) {
			if(client.message_to_handle.size() <= 0)
				return;
			//唤醒client线程进行处理（注意：notify时要获取对象锁）
			synchronized(client) {
				//client.notify();
				client.handleMessage();
			}
		}
	}
	
	
	//获取seat_num对应的client
	public ClientRunner getClientById(String client_id) {
		for(ClientRunner client : all_client)
			if(client.id.equals(client_id))
				return client;
		assert(false);
		return null;
	}
	
	//获取seat_num对应的client
	public ClientRunner getClientBySeatNum(int num) {
		assert(num >= 0 && num < table_sum * 2);
		if(num % 2 == 0)
			return getTableBySeatNum(num).left_client;
		return getTableBySeatNum(num).right_client;
	}
	
	//获取seat_num对应的table
	public Table getTableBySeatNum(int num) {
		assert(num >= 0 && num < table_sum * 2);
		return all_table.get(num / 2);
	}
	
	
	//负责所有消息的接收
	class NIOReader implements Runnable {
		//注意：仅负责收消息
		public void run() {
			//消息监视线程
			selector = null;
			ServerSocketChannel serverSocketChannel = null;
			SelectionKey readyKey = null;
			try {
				// Selector for incoming time requests
				selector = Selector.open();

				serverSocketChannel = ServerSocketChannel.open();
				serverSocketChannel.configureBlocking(false);
				serverSocketChannel.socket().setReuseAddress(true);
				serverSocketChannel.socket().bind(new InetSocketAddress(5555));
				serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		
				while (true) {
					System.out.println("wait !!!");
					int count = selector.select();
					if(count < 0)
						break;
					System.out.println("count: " + count);
					Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		
					while (it.hasNext()) {					
						readyKey = it.next();
						it.remove();
						if(readyKey.isAcceptable())
							acceptConnect(readyKey);
						else if(readyKey.isReadable())
							read(readyKey);
						else if(readyKey.isConnectable())
							System.out.println("isConnectable() !!!");
						else if(readyKey.isValid())
							System.out.println("isValid() !!!");
						else if(readyKey.isWritable())
							System.out.println("isWritable() !!!");
						else
							System.out.println("other !!!");
					}
				}
			} catch (ClosedChannelException ex) {
				logger.log(Level.SEVERE, null, ex);
			} catch (IOException ex) {
				((ClientRunner)readyKey.attachment()).terminate();

//				System.out.println("key.attachment(): " + ((ClientRunner)readyKey.attachment()).id);
//				ex.printStackTrace();
//				System.out.println("Client closed !!!");
//				try {
//					readyKey.channel().close();
//					readyKey.cancel();
//					//logger.log(Level.SEVERE, null, ex);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
			} finally {
//				try {
//					selector.close();
//				} catch(Exception ex) {}
//				try {
//					serverSocketChannel.close();
//				} catch(Exception ex) {}
			}
		}

		//接收客户端连接，启动新的ClientRunner
		private void acceptConnect(SelectionKey key) throws IOException {
			System.out.println("accept connect !!!");
			ServerSocketChannel server_socket_channel = (ServerSocketChannel) key.channel();
			SocketChannel socket_channel = null;
			ClientRunner new_client = new ClientRunner();

			socket_channel = server_socket_channel.accept();
			socket_channel.configureBlocking(false);
			SelectionKey client_key = socket_channel.register(selector, SelectionKey.OP_READ);
			client_key.attach(new_client);	//SelectionKey附加对象

			new_client.socket_channel = socket_channel;
			
			/***ClientRunner不再implements Runnable***/
			//Thread new_client_thread = new Thread(new_client);
			//new_client.in_client_thread = new_client_thread;
			//new_client_thread.start();
			
			synchronized(all_client) {
				all_client.add(new_client);
			}
		}

		//接收消息字节流，根据key附加的ClientRunner放入响应client的消息字节流中
		private Message read(SelectionKey key) throws IOException {
			System.out.println("read !!!");
			SocketChannel socket_channel = (SocketChannel) key.channel();
			Message message_obj = null;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			ClientRunner client = (ClientRunner) key.attachment();
			
			try {
				byte[] bytes;
				int size = socket_channel.read(buffer);
				
				//key对应的client调用了关闭了socket_channel
				if(size == -1) {
					//((ClientRunner)key.attachment()).terminate();
					//抛出异常，交由上层处理（调用terminate()）
					throw new IOException("Client closed !!!");
				}

				if(size > 0) {
					while (size > 0) {
						buffer.flip();
						bytes = new byte[size];
						buffer.get(bytes);
						baos.write(bytes);
						buffer.clear();
						size = socket_channel.read(buffer);
						System.out.println("size: " + size);
					}
					System.out.println("size: " + size);

					bytes = baos.toByteArray();
					//加入byte队列
					for(byte b : bytes)
						client.message_byte_q.add(b);
					System.out.println("添加" + bytes.length + "到byte队列中");
					System.out.println("目前byte队列长度： " + client.message_byte_q.size());
				}
			} finally {
				try {
					baos.close();
				} catch(Exception ex) {}
			}
			return message_obj;
		}
		

	}
	
	
	
	
	public void serverStart() {
		//连接数据库
		connectDatabase();
		//启动IO线程
		new Thread(new NIOReader()).start();
		//启动消息监视线程
		new Thread(new MessageMonitor()).start();
		//启动计时任务（线程）
		timer = new Timer();
		timer_task = new MyTimerTask();
		timer.schedule(timer_task, 0, 1000);	//0秒后每隔1秒执行一次timer_task
	}

	//开始游戏
	public void launchGameWindow(Table game_table) {
		assert(game_table.isFull());
		String message = game_table.left_client.id + " " + game_table.right_client.id;
		Message game_begin_message = Message.newMessage(Message.MessageType.OPPONENT_SEATED, message);
		game_table.notifyAllTableMember(game_begin_message);
		//初始化table的chess_board_status
		game_table.initializeChessBoardStatus();
	}

	public static void main(String[] args) {
		new GameServer().serverStart();
	}



	/*
	 * 内部类：计时任务线程
	 */

	class MyTimerTask extends java.util.TimerTask {
		//维护一个正在对战的Table列表
		private ArrayList<Table> all_playing_table = new ArrayList<Table>();

		//新加一个正在对战的对战桌
		public synchronized void addTable(Table table) {
			for(Table t : all_playing_table)
				assert(t != table);
			all_playing_table.add(table);
		}

		//移除一个正在对战的对战桌
		public synchronized void removeTable(Table table) {
			all_playing_table.remove(table);
		}
		
		public synchronized void run() {
			//正在对战的每个线程都通知一下一秒过去
			//注意迭代过程中同时又修改操作的处理（迭代器删除）
			Iterator<Table> it = all_playing_table.iterator();
			while(it.hasNext()) {
				Table table = it.next();
				try {
					table.left_client.oneSecond();
					table.right_client.oneSecond();
					for(ClientRunner spectator : table.spectator_list)
						spectator.oneSecond();
				} catch (Exception e) {
					//异常，说明对战client退出了，清除该对战桌
					it.remove();
				}
			}
		}
	}


	/*
	 * 内部类：Table
	 */
	class Table {
		//胜负统计
		int left_win_count = 0, right_win_count = 0;
		int black_piece_count = 0, white_piece_count = 0;
		
		//棋盘局面
		int[][] chess_board_status = new int[8][8];
		
		//八个方向
		ArrayList<Map.Entry<Integer, Integer>> all_direction = new ArrayList<Map.Entry<Integer, Integer>>();
		
		//对战双方
		private ClientRunner left_client = null, right_client = null;
		//游客列表
		private ArrayList<ClientRunner> spectator_list = new ArrayList<ClientRunner>();
		//行棋方
		ClientRunner turn_client = null;
		
		//构造函数：初始化方向，但不能初始化chess_board_status
		public Table() {
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(1,0));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(1,1));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(1,-1));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(0,1));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(0,-1));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(-1,0));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(-1,1));
			all_direction.add(new AbstractMap.SimpleEntry<Integer, Integer>(-1,-1));
		}
		
		//获取table当前统计信息（双方时间，胜负统计
		
		
		//重置对战桌，清空上次对战消息（注意，是“上一届”信息，不是上一局信息）
		public void reset()	 {
			initializeChessBoardStatus();
			left_win_count = right_win_count = 0;
			left_client = right_client = null;
			turn_client = null;
		}
		
		
		//刷新棋子数目统计
		public void refreshPieceCount()	{
			black_piece_count = white_piece_count = 0;
			for(int i = 0; i < 64; i++) {
				if(chess_board_status[i/8][i%8] == BLACK_FLAG)
					black_piece_count++;
				if(chess_board_status[i/8][i%8] == WHITE_FLAG)
					white_piece_count++;
			}
		}
		
		//一局开始的必要处理
		public void gameBegin() {
			//重新初始化棋盘
			initializeChessBoardStatus();

			//游戏开始（left_client先手执黑）
			left_client.color = BLACK;
			right_client.color = WHITE;
			turn_client = left_client;
			
			//棋盘格局消息
			Message chess_board_status_message = getChessBoardStatusMessage();
			notifyAllTableMember(chess_board_status_message);
			
			String message = left_client.id + " " + right_client.id;
			Message game_begin_message = Message.newMessage(Message.MessageType.GAME_BEGIN_TYPE, message);
			notifyAllTableMember(game_begin_message);
			
			timer_task.addTable(this);
		}
		
		
		//一局结束的必要处理，传入的是失败一方
		public void gameOver(ClientRunner lose_client) {
			//不再对此桌计时，
			timer_task.removeTable(this);
			//不再有行棋方
			turn_client = null;
			left_client.ready = right_client.ready = false;
			//刷新胜负统计，
			if(lose_client == left_client)
				right_win_count++;
			else left_win_count++;

		}
		
		//初始化chess_board_status
		public void initializeChessBoardStatus() {
			//清零
			for(int i = 0; i < 64; i++)
				chess_board_status[i/8][i%8] = NULL_FLAG;
			chess_board_status[3][3] = chess_board_status[4][4] = BLACK_FLAG;	//俩黑子
			chess_board_status[3][4] = chess_board_status[4][3] = WHITE_FLAG;	//俩白子
			chess_board_status[2][4] = chess_board_status[3][5] = chess_board_status[4][2] = chess_board_status[5][3] = BLACK_AVAILABLE; //黑子可下
			chess_board_status[2][3] = chess_board_status[4][5] = chess_board_status[5][4] = chess_board_status[3][2] = WHITE_AVAILABLE; //白子可下
		}
		
		//返回棋盘状态消息
		public Message getChessBoardStatusMessage() {
			String message = "";
			for(int i = 0; i < 8; i++)
				for(int j = 0; j < 8; j++)
					message += Integer.toString(chess_board_status[i][j]) + " ";
			return Message.newMessage(Message.MessageType.CHESS_BOARD_STATUS_TYPE, message);			
		}
		
		//调整chess_board_status
		public void adjustChessBoardStatus(int hit_index, String hit_client_color) {
			int i = hit_index / 8; int j = hit_index % 8;	//点击坐标
			int hit_client_flag = hit_client_color.equals(BLACK) ? BLACK_FLAG : WHITE_FLAG;		//行棋方
			int another_client_flag = hit_client_color.equals(BLACK) ? WHITE_FLAG : BLACK_FLAG;	//另一方

			//每个方向上的检查
			for(Map.Entry<Integer, Integer> direction : all_direction) {
				int new_i = i;
				int new_j = j;
				while(true) {
					new_i += direction.getKey();
					new_j += direction.getValue();
					if(new_i > 7 || new_i < 0 || new_j >7 || new_j < 0)	//越界了
						break;
					if(chess_board_status[new_i][new_j] == another_client_flag)
						continue;
					if(chess_board_status[new_i][new_j] == hit_client_flag) {		//与起点之间格子变色
						System.out.println("direction: " + direction.getKey() + ", " + direction.getValue());
						while(new_i != i || new_j != j) {		//最后一次循环会改变hit_index
							System.out.println("!!!");
							new_i -= direction.getKey();
							new_j -= direction.getValue();
							//assert(chess_board_status[new_i][new_j] == another_client_flag);
							System.out.println(new_i +","+new_j + " changed");
							chess_board_status[new_i][new_j] = hit_client_flag;
						}
					}
					break;		//遇到空格子由此退出
				}
			}
			
			//对每个位置检查并设置available
			for(int index = 0; index < 64; index++)
				checkAndSetAvailable(index);
		}
		
		//检查并设置available标志
		public void checkAndSetAvailable(int index) {
			int i = index / 8; int j = index % 8;
			//当前位置有棋子，退出
			if(chess_board_status[i][j] == BLACK_FLAG || chess_board_status[i][j] == WHITE_FLAG)
				return;
			chess_board_status[i][j] = NULL_FLAG;	//清空原来的
			
			boolean black_available = false;
			boolean white_available = false;
			
			//每个方向上的检查
			for(Map.Entry<Integer, Integer> direction : all_direction) {
				int new_i = i + direction.getKey();
				int new_j = j + direction.getValue();
				if(new_i > 7 || new_i < 0 || new_j > 7 || new_j < 0)	//越界了
					continue;
				//邻居没有棋子，退出本次循环
				if(chess_board_status[new_i][new_j] != BLACK_FLAG && chess_board_status[new_i][new_j] != WHITE_FLAG)
					continue;
				int neighbor_color_flag = chess_board_status[new_i][new_j];	//邻居颜色
				int another_color_flag = neighbor_color_flag == BLACK_FLAG ? WHITE_FLAG : BLACK_FLAG;
				
				while(true) {
					new_i += direction.getKey();
					new_j += direction.getValue();
					if(new_i > 7 || new_i < 0 || new_j > 7 || new_j < 0)	//越界了
						break;
					if(chess_board_status[new_i][new_j] == neighbor_color_flag)
						continue;
					if(chess_board_status[new_i][new_j] == another_color_flag) {
						if(another_color_flag == BLACK_FLAG)
							black_available = true;
						else
							white_available = true;
					}
					break;		//遇到空格子由此退出
				}
				if(black_available && white_available)	//both_available了，没必要再检查
					break;
			}
			chess_board_status[i][j] = black_available && white_available ? BOTH_AVAILABLE :
				black_available ? BLACK_AVAILABLE : white_available ? WHITE_AVAILABLE : NULL_FLAG;
		}
		
		//获取对手
		public ClientRunner getOpponentClient(ClientRunner c) {
			return left_client == c ? right_client : right_client == c ? left_client: null;	
		}
		
		//加入新client
		public void addClient(int seat_num, ClientRunner c) {
			assert(seat_num >= 0 && seat_num < table_sum * 2);
			if(seat_num % 2 == 0)
				left_client = c;
			else
				right_client = c;
		}
		
		//删除client
		public void removeClient(ClientRunner c) {
			if(left_client == c)
				left_client = null;
			else if(right_client == c)
				right_client = null;
		}

		//判断桌是否已满（对战双方都已入座）
		public boolean isFull() {
			return left_client != null && right_client != null;
		}
		
		//发消息给所有table成员（对战双方、游客）
		public void notifyAllTableMember(Message message)  {
			assert(isFull());
			left_client.NIOSendMessage(message);
			right_client.NIOSendMessage(message);
			for(ClientRunner spectator : spectator_list)
				spectator.NIOSendMessage(message);
		}
	}
	
	/*
	 * 内部类：Client
	 */
	class ClientRunner {
		//由MessageMonitor分发的message，notify唤醒之后处理它们
		public BlockingQueue<Message> message_to_handle = new LinkedBlockingQueue<Message>();

		//message字节队列中下一个message的长度，处理message之后立即设置为-1
		public AtomicInteger next_message_length;
		
		//负责NIO的socket_channel
		public SocketChannel socket_channel = null;
		public BlockingQueue<Byte> message_byte_q = new LinkedBlockingQueue<Byte>();
		
		
		private String id, password;
		private int seat_num;		//标志Client位置的唯一变量
		
		private boolean ready = false;	//是否准备好开始游戏
		
		private String color;	//持方（颜色）
		
		
		public ClientRunner() {
			System.out.println("new ClientRunner !!!");
			initialize();
		}
		
		private void initialize() {
			next_message_length = new AtomicInteger(-1);
		}

		//发送数据对象
		public void NIOSendMessage(Message message) {
			byte[] message_bytes = SerializableUtil.toBytes(message);

			byte[] length_bytes = BytesIntTransfer.Int2Bytes(message_bytes.length);
			ByteBuffer length_buffer = ByteBuffer.wrap(length_bytes);
			ByteBuffer message_buffer = ByteBuffer.wrap(message_bytes);

			try {
				//message长度先行
				while(length_buffer.hasRemaining()) {
					System.out.println("ID: " + id + " sending " + message);
					socket_channel.write(length_buffer);
				}
				//真正message
				socket_channel.write(message_buffer);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		
		
		//检查用户名密码是否合法
		public Boolean checkIdPassword(String buf) {
			Boolean result = false;
			String[] id_password = buf.split(" ");
			id = id_password[0];
			password = id_password[1];
			try {
				String sql_check = "select password from table_test_1 where id = '" + id + "'";
				ResultSet rs = stmt.executeQuery(sql_check);
				result = rs.next() && rs.getString("password").equals(password);
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return result;
		}
		
		//检查id是否未被注册
		public Boolean checkIdAvailable(String buf) {
			Boolean result = false;
			String[] id_password = buf.split(" ");
			id = id_password[0];
			password = id_password[1];
			try {
				String sql_check_already = "select password from table_test_1 where id = '" + id + "'";
				ResultSet rs = stmt.executeQuery(sql_check_already);
				result = !rs.next();
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return result;
		}
		
		//注册新用户
		public int registerNewId() {
			int lines_modify = 0;
			try {
				String sql_register = "insert into table_test_1(id, password)"
					+ "values('" + id + "','" + password + "')";
				lines_modify = stmt.executeUpdate(sql_register);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return lines_modify;
		}	
		
		//离座
		public void standUp() {
			if(seat_num < 0)
				return;
			Table in_table = getTableBySeatNum(seat_num);
			in_table.removeClient(this);
			//对战桌数组（需要计时的对战桌数组）清除此桌
			timer_task.removeTable(in_table);
			seat_num = -1;
		}
		
		//入座
		public void sitDown(int num) {
			assert(num >= 0 && num < table_sum * 2);
			seat_num = num;
			getTableBySeatNum(num).addClient(num, this);
		}
		
		
		//获取一个关于全部座位信息的seat_status_type消息对象
 		public Message getSeatStatusMessage() {
			String seat_status = "";
			for(Table table : all_table) {
				if(table.left_client != null)
					seat_status += table.left_client.id + " ";	//空格是间隔符
				else
					seat_status += "#" + " ";
				
				if(table.right_client != null)
					seat_status += table.right_client.id + " ";	//空格是间隔符
				else
					seat_status +=  "#" + " ";
			}
			
			return Message.newMessage(Message.MessageType.SEAT_STATUS_TYPE, seat_status);
		}
		
 		
 		//计时任务，一秒过去，通知自己所属client
 		public void oneSecond() throws IOException {
 			//注意：这里throws处理，由timer_task捕获异常，然后删掉相应table
			NIOSendMessage(Message.newMessage(Message.MessageType.ONE_SECOND, ""));
 		}
 		
 		
		//根据消息类型处理不同消息
		public void handleMessage() {
			while(message_to_handle.size() > 0) {
				Message message = message_to_handle.poll();
				Message.MessageType message_type = message.getType();

				System.out.println("handling " + message + " by client " + id); 
				
				if(message_type == Message.MessageType.LOGIN_TYPE)				//登录消息
					handleLoginMessage(message.getBuf());
				else if(message_type == Message.MessageType.REGISTER_TYPE)		//注册消息
					handleRegisterMessage(message.getBuf());
				else if(message_type == Message.MessageType.SEAT_TYPE)			//座位消息
					handleSeatMessage(message.getBuf());
				else if(message_type == Message.MessageType.REVERSI_TYPE)		//行棋消息
					handleReversiMessage(message.getBuf());
				else if(message_type == Message.MessageType.CHAT_MESSAGE_TYPE)	//聊天消息
					handleChatMessage(message.getBuf());
				else if(message_type == Message.MessageType.CLIENT_QUIT)		//client退出
					handleClientQuit();
				else if(message_type == Message.MessageType.GAME_READY)			//准备就绪
					handleGameReady();
				else if(message_type == Message.MessageType.LOSE_GAME)			//游戏输
					handleLoseGame();
				else if(message_type == Message.MessageType.SPECTATOR_TYPE)		//请求观战
					handleSpectator(message.getBuf());
			}

		}
		
		
		//新的spectator
		public void handleSpectator(String spectator_button_num) {
			int table_num = Integer.parseInt(spectator_button_num);
			Table table = all_table.get(table_num);
			//对战双方未入座，不允许观战
			if(!table.isFull())
				return;
			
		}
		
		//处理判负
		public void handleLoseGame() {
			Table game_table = getTableBySeatNum(seat_num);
			//一局结束，刷新胜负统计，传入的是失败方
			game_table.gameOver(this);

			int black_win = game_table.left_client.color.equals(BLACK)?game_table.left_win_count:game_table.right_win_count;
			int white_win = game_table.left_client.color.equals(WHITE)?game_table.left_win_count:game_table.right_win_count;
			
			//注意：一局结束只更新win_count，其余统计消息下局开始时再刷新
			Message win_count_message = 
					Message.newMessage(Message.MessageType.WIN_COUNT_TYPE, black_win + " " + white_win);
			game_table.notifyAllTableMember(win_count_message);
			
			//buf表示失败一方的id
			Message game_over_message = 
					Message.newMessage(Message.MessageType.GAME_OVER_TYPE, this.id);
			game_table.notifyAllTableMember(game_over_message);
		}
		
		
		//处理游戏开始请求
		public void handleGameReady() {
			synchronized(getTableBySeatNum(seat_num)) {
				this.ready = true;
				Table game_table = getTableBySeatNum(seat_num);
				ClientRunner opponent_client = game_table.getOpponentClient(this);
				if(opponent_client == null || opponent_client.ready == false)	//对手未入座或没准备好
					return;
				
				//游戏开始
				game_table.gameBegin();
			}
		}
		
		//处理聊天消息
		public void handleChatMessage(String buf) {
			String chat_content = id + ": " + buf;
			Message chat_message = Message.newMessage(Message.MessageType.CHAT_MESSAGE_TYPE, chat_content);
			getTableBySeatNum(seat_num).notifyAllTableMember(chat_message);
		}
		
		
		//处理client退出消息
		public void handleClientQuit() {
			synchronized(getTableBySeatNum(seat_num)) {
				//terminate(Thread.currentThread());
				terminate();
				//注意，有client退出时，都用seat_status_message来通知所有client
				notifyAllClient(getSeatStatusMessage());
				//System.out.println("client " + id + " is going to notifyAllClient !!!");

			}
		}
		
		//处理登录消息
		public void handleLoginMessage(String buf) {
			if (checkIdPassword(buf).equals(true)){ // 合法的用户名密码
				NIOSendMessage(Message.newMessage(Message.MessageType.LOGIN_SUCCESS, ""));
				NIOSendMessage(getSeatStatusMessage());
			}
			else
				NIOSendMessage(Message.newMessage(Message.MessageType.WRONG_ID_PASSWORD, ""));
		}
		
		//处理注册消息
		public void handleRegisterMessage(String buf) {
			if (checkIdAvailable(buf).equals(true)) { // 该用户名尚未注册
				registerNewId();
				NIOSendMessage(Message.newMessage(Message.MessageType.REGISTER_SUCCESS, ""));
			} else
				NIOSendMessage(Message.newMessage(Message.MessageType.ID_ALREADY_EXIST, ""));
		}
		
		//处理座位消息
		public void handleSeatMessage(String buf) {
			System.out.println("buf: " + buf);
			//获取座位号
			int num = Integer.parseInt(buf);
			//获取座位号上当前client
			ClientRunner client_in_the_seat = getClientBySeatNum(num);
			//获取座位号对应table
			Table going_table = getTableBySeatNum(num);

			synchronized (going_table) {
				//座上有自己，离座
				if(this.equals(client_in_the_seat)) {
					standUp();
					//离座成功回执
					NIOSendMessage(Message.newMessage(Message.MessageType.SEAT_OFF_SUCCESS, ""));
					//更新所有client的座位局面
					notifyAllClient(getSeatStatusMessage());
				}
				//座上有别人，入座失败
				else if (client_in_the_seat != null)
					NIOSendMessage(Message.newMessage(Message.MessageType.SEATED_FAIL, ""));
				//座上没人，入座或换座
				else {
					standUp(); sitDown(num);	//换座
					//通知客户端已入座
					NIOSendMessage(Message.newMessage(Message.MessageType.SEATED_SUCCESS, ""));
					System.out.println("going to sent seated_success to client !!!");
					//更新所有client的座位局面
					notifyAllClient(getSeatStatusMessage());
					//入座之后检查是否开局
					if(getTableBySeatNum(seat_num).isFull())
						GameServer.this.launchGameWindow(getTableBySeatNum(seat_num));
				}
			}
		}
		
		//处理行棋消息
		public void handleReversiMessage(String buf) {
			System.out.println("id: " + id);
			
			Table game_table = getTableBySeatNum(seat_num);
			System.out.println("game_table.turn_client.id: " + game_table.turn_client.id);

			if(game_table.turn_client != this)
				return;	//非行棋方，点击无效
			int index = Integer.parseInt(buf);	//棋盘点击位置
			int index_status = game_table.chess_board_status[index/8][index%8] ;
			
			System.out.println("index_status: " + index_status);
			//不可下子位置，点击无效
			if(index_status != (color.equals(BLACK) ? BLACK_AVAILABLE : WHITE_AVAILABLE)
					&& index_status != BOTH_AVAILABLE)
				return;
			
			System.out.println("888");
			
			game_table.turn_client = game_table.getOpponentClient(game_table.turn_client);	//行棋方转换
			game_table.adjustChessBoardStatus(index, color);	//调整棋盘格局
			
			game_table.refreshPieceCount();		//刷新棋子统计
			//注意：client端的棋子统计的刷新由他自己完成
			
			Message chess_board_status_message = game_table.getChessBoardStatusMessage();
			game_table.notifyAllTableMember(chess_board_status_message);	//棋盘格局消息
			
			Message turn_message = Message.newMessage(Message.MessageType.TURN_TYPE, game_table.getOpponentClient(this).id);
			game_table.notifyAllTableMember(turn_message);	//行棋方消息
			
			
		}

		public void terminate() {
			System.out.println("client " + id + "terminate !!!");
			standUp();
			//先关掉自己的socket，并将自己从all_client中移除，就不用向自己发了
			try {
				socket_channel.close();
				all_client.remove(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
			notifyAllClient(getSeatStatusMessage());
		}
		
		
		public void doCleanup(Exception cause) {
			//doNothing
		}
		
	}
	/******* end of ClientRunner ***********/
	

}





/********
 * 
 * 
 * 写到handleLoseGame()
 * 
 * 
 * 
 ********/
















