/*
 * ��Ϸ����������Ϊ��Ϣ��תվ
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
	//��ɫ����
	private static final String BLACK = "black";
	private static final String WHITE = "white";
	
	//���̱�־����
	static final int BLACK_AVAILABLE = 1;
	static final int WHITE_AVAILABLE = 2;
	static final int BOTH_AVAILABLE = 3;
	static final int BLACK_FLAG = 4;
	static final int WHITE_FLAG = 5;
	static final int NULL_FLAG = 6;
	
	//���пͻ��˽���
	static private ArrayList<ClientRunner> all_client = new ArrayList<ClientRunner>();
	
	//��ʱ����
	Timer timer;
	MyTimerTask timer_task;
	
	//ȫ�ֵ�statement
	static private Statement stmt;
	
	//����������
	static final int table_sum = 4;
	
	//��ս������
	static private ArrayList<Table> all_table = new ArrayList<Table>();
	
	//for NIO
	private final static Logger logger = Logger.getLogger(GameServer.class.getName());
	private static Selector selector;
	
	
	
	//���췽�������ֳ�ʼ��
	public GameServer() {
		//��ʼ����ս��
		for(int i = 0; i < table_sum; i++)
			all_table.add(new Table());		//ע�⣺��Ŵ�0��ʼ
	}
	
	// �����пͻ��˽��̷�����Ϣ
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
			// ����
			Class.forName("com.mysql.jdbc.Driver");
			System.out.println("�ɹ�����MySQL��������");
			Connection con = DriverManager.getConnection(url);
			stmt = con.createStatement();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/*
	 * �ڲ��࣬�ȴ������߳�
	 */
	
//	//�ȴ������߳�
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

	//��Ϣ�����̣߳��ټ���&�������client��Ϣ�ֽڶ��� ��������Ϣ�������client����Ϣ���в�����
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
		
		//������һ�������յ�message�ĳ���
		public void tryTosetNextMessageLength(ClientRunner client) {
			//��һ�����õĻ�δ���� || �µ�4�ֽ�lengthδ���ֱ�ӷ���
			if(client.next_message_length.get() > 0 || client.message_byte_q.size() < 4)
				return;
			//ȡ���󳤶�
			byte[] length_bytes = new byte[4];
			for(int i = 0; i < 4; i++)
				length_bytes[i] = client.message_byte_q.poll();
			int length = BytesIntTransfer.Bytes2Int(length_bytes);
			System.out.println("�¸����󳤶ȣ� " + length);
			client.next_message_length.set(length);
		}
		
		//��Ϣ�ֽ����� ����>��Ϣ���󣬷���client����Ϣ����
		public void tryToReceiveNextMessage(ClientRunner client) {
			//4�ֽڳ��ȱ�־ �� ��Ϣ��δ���ֱ�ӷ���
			if(client.next_message_length.get() < 0 
					|| client.message_byte_q.size() < client.next_message_length.get())
				return;
			
			//ȡ�������ֽ���&�����л�&�ַ�
			byte[] obj_bytes = new byte[client.next_message_length.get()];
			for(int i = 0; i < client.next_message_length.get(); i++)
				obj_bytes[i] = client.message_byte_q.poll();
			Message new_message = (Message) SerializableUtil.toObject(obj_bytes);
			client.message_to_handle.add(new_message);	//�����������Ϣ����
			System.out.println("new_message.type: " + new_message.getType());
			//ע�⣺�ָ�-1׼���¸���Ϣ�Ľ���
			client.next_message_length.set(-1);
		}
		
		//������Ϣ
		public void tryToHandleNextMessage(ClientRunner client) {
			if(client.message_to_handle.size() <= 0)
				return;
			//����client�߳̽��д���ע�⣺notifyʱҪ��ȡ��������
			synchronized(client) {
				//client.notify();
				client.handleMessage();
			}
		}
	}
	
	
	//��ȡseat_num��Ӧ��client
	public ClientRunner getClientById(String client_id) {
		for(ClientRunner client : all_client)
			if(client.id.equals(client_id))
				return client;
		assert(false);
		return null;
	}
	
	//��ȡseat_num��Ӧ��client
	public ClientRunner getClientBySeatNum(int num) {
		assert(num >= 0 && num < table_sum * 2);
		if(num % 2 == 0)
			return getTableBySeatNum(num).left_client;
		return getTableBySeatNum(num).right_client;
	}
	
	//��ȡseat_num��Ӧ��table
	public Table getTableBySeatNum(int num) {
		assert(num >= 0 && num < table_sum * 2);
		return all_table.get(num / 2);
	}
	
	
	//����������Ϣ�Ľ���
	class NIOReader implements Runnable {
		//ע�⣺����������Ϣ
		public void run() {
			//��Ϣ�����߳�
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

		//���տͻ������ӣ������µ�ClientRunner
		private void acceptConnect(SelectionKey key) throws IOException {
			System.out.println("accept connect !!!");
			ServerSocketChannel server_socket_channel = (ServerSocketChannel) key.channel();
			SocketChannel socket_channel = null;
			ClientRunner new_client = new ClientRunner();

			socket_channel = server_socket_channel.accept();
			socket_channel.configureBlocking(false);
			SelectionKey client_key = socket_channel.register(selector, SelectionKey.OP_READ);
			client_key.attach(new_client);	//SelectionKey���Ӷ���

			new_client.socket_channel = socket_channel;
			
			/***ClientRunner����implements Runnable***/
			//Thread new_client_thread = new Thread(new_client);
			//new_client.in_client_thread = new_client_thread;
			//new_client_thread.start();
			
			synchronized(all_client) {
				all_client.add(new_client);
			}
		}

		//������Ϣ�ֽ���������key���ӵ�ClientRunner������Ӧclient����Ϣ�ֽ�����
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
				
				//key��Ӧ��client�����˹ر���socket_channel
				if(size == -1) {
					//((ClientRunner)key.attachment()).terminate();
					//�׳��쳣�������ϲ㴦������terminate()��
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
					//����byte����
					for(byte b : bytes)
						client.message_byte_q.add(b);
					System.out.println("���" + bytes.length + "��byte������");
					System.out.println("Ŀǰbyte���г��ȣ� " + client.message_byte_q.size());
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
		//�������ݿ�
		connectDatabase();
		//����IO�߳�
		new Thread(new NIOReader()).start();
		//������Ϣ�����߳�
		new Thread(new MessageMonitor()).start();
		//������ʱ�����̣߳�
		timer = new Timer();
		timer_task = new MyTimerTask();
		timer.schedule(timer_task, 0, 1000);	//0���ÿ��1��ִ��һ��timer_task
	}

	//��ʼ��Ϸ
	public void launchGameWindow(Table game_table) {
		assert(game_table.isFull());
		String message = game_table.left_client.id + " " + game_table.right_client.id;
		Message game_begin_message = Message.newMessage(Message.MessageType.OPPONENT_SEATED, message);
		game_table.notifyAllTableMember(game_begin_message);
		//��ʼ��table��chess_board_status
		game_table.initializeChessBoardStatus();
	}

	public static void main(String[] args) {
		new GameServer().serverStart();
	}



	/*
	 * �ڲ��ࣺ��ʱ�����߳�
	 */

	class MyTimerTask extends java.util.TimerTask {
		//ά��һ�����ڶ�ս��Table�б�
		private ArrayList<Table> all_playing_table = new ArrayList<Table>();

		//�¼�һ�����ڶ�ս�Ķ�ս��
		public synchronized void addTable(Table table) {
			for(Table t : all_playing_table)
				assert(t != table);
			all_playing_table.add(table);
		}

		//�Ƴ�һ�����ڶ�ս�Ķ�ս��
		public synchronized void removeTable(Table table) {
			all_playing_table.remove(table);
		}
		
		public synchronized void run() {
			//���ڶ�ս��ÿ���̶߳�֪ͨһ��һ���ȥ
			//ע�����������ͬʱ���޸Ĳ����Ĵ���������ɾ����
			Iterator<Table> it = all_playing_table.iterator();
			while(it.hasNext()) {
				Table table = it.next();
				try {
					table.left_client.oneSecond();
					table.right_client.oneSecond();
					for(ClientRunner spectator : table.spectator_list)
						spectator.oneSecond();
				} catch (Exception e) {
					//�쳣��˵����սclient�˳��ˣ�����ö�ս��
					it.remove();
				}
			}
		}
	}


	/*
	 * �ڲ��ࣺTable
	 */
	class Table {
		//ʤ��ͳ��
		int left_win_count = 0, right_win_count = 0;
		int black_piece_count = 0, white_piece_count = 0;
		
		//���̾���
		int[][] chess_board_status = new int[8][8];
		
		//�˸�����
		ArrayList<Map.Entry<Integer, Integer>> all_direction = new ArrayList<Map.Entry<Integer, Integer>>();
		
		//��ս˫��
		private ClientRunner left_client = null, right_client = null;
		//�ο��б�
		private ArrayList<ClientRunner> spectator_list = new ArrayList<ClientRunner>();
		//���巽
		ClientRunner turn_client = null;
		
		//���캯������ʼ�����򣬵����ܳ�ʼ��chess_board_status
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
		
		//��ȡtable��ǰͳ����Ϣ��˫��ʱ�䣬ʤ��ͳ��
		
		
		//���ö�ս��������ϴζ�ս��Ϣ��ע�⣬�ǡ���һ�족��Ϣ��������һ����Ϣ��
		public void reset()	 {
			initializeChessBoardStatus();
			left_win_count = right_win_count = 0;
			left_client = right_client = null;
			turn_client = null;
		}
		
		
		//ˢ��������Ŀͳ��
		public void refreshPieceCount()	{
			black_piece_count = white_piece_count = 0;
			for(int i = 0; i < 64; i++) {
				if(chess_board_status[i/8][i%8] == BLACK_FLAG)
					black_piece_count++;
				if(chess_board_status[i/8][i%8] == WHITE_FLAG)
					white_piece_count++;
			}
		}
		
		//һ�ֿ�ʼ�ı�Ҫ����
		public void gameBegin() {
			//���³�ʼ������
			initializeChessBoardStatus();

			//��Ϸ��ʼ��left_client����ִ�ڣ�
			left_client.color = BLACK;
			right_client.color = WHITE;
			turn_client = left_client;
			
			//���̸����Ϣ
			Message chess_board_status_message = getChessBoardStatusMessage();
			notifyAllTableMember(chess_board_status_message);
			
			String message = left_client.id + " " + right_client.id;
			Message game_begin_message = Message.newMessage(Message.MessageType.GAME_BEGIN_TYPE, message);
			notifyAllTableMember(game_begin_message);
			
			timer_task.addTable(this);
		}
		
		
		//һ�ֽ����ı�Ҫ�����������ʧ��һ��
		public void gameOver(ClientRunner lose_client) {
			//���ٶԴ�����ʱ��
			timer_task.removeTable(this);
			//���������巽
			turn_client = null;
			left_client.ready = right_client.ready = false;
			//ˢ��ʤ��ͳ�ƣ�
			if(lose_client == left_client)
				right_win_count++;
			else left_win_count++;

		}
		
		//��ʼ��chess_board_status
		public void initializeChessBoardStatus() {
			//����
			for(int i = 0; i < 64; i++)
				chess_board_status[i/8][i%8] = NULL_FLAG;
			chess_board_status[3][3] = chess_board_status[4][4] = BLACK_FLAG;	//������
			chess_board_status[3][4] = chess_board_status[4][3] = WHITE_FLAG;	//������
			chess_board_status[2][4] = chess_board_status[3][5] = chess_board_status[4][2] = chess_board_status[5][3] = BLACK_AVAILABLE; //���ӿ���
			chess_board_status[2][3] = chess_board_status[4][5] = chess_board_status[5][4] = chess_board_status[3][2] = WHITE_AVAILABLE; //���ӿ���
		}
		
		//��������״̬��Ϣ
		public Message getChessBoardStatusMessage() {
			String message = "";
			for(int i = 0; i < 8; i++)
				for(int j = 0; j < 8; j++)
					message += Integer.toString(chess_board_status[i][j]) + " ";
			return Message.newMessage(Message.MessageType.CHESS_BOARD_STATUS_TYPE, message);			
		}
		
		//����chess_board_status
		public void adjustChessBoardStatus(int hit_index, String hit_client_color) {
			int i = hit_index / 8; int j = hit_index % 8;	//�������
			int hit_client_flag = hit_client_color.equals(BLACK) ? BLACK_FLAG : WHITE_FLAG;		//���巽
			int another_client_flag = hit_client_color.equals(BLACK) ? WHITE_FLAG : BLACK_FLAG;	//��һ��

			//ÿ�������ϵļ��
			for(Map.Entry<Integer, Integer> direction : all_direction) {
				int new_i = i;
				int new_j = j;
				while(true) {
					new_i += direction.getKey();
					new_j += direction.getValue();
					if(new_i > 7 || new_i < 0 || new_j >7 || new_j < 0)	//Խ����
						break;
					if(chess_board_status[new_i][new_j] == another_client_flag)
						continue;
					if(chess_board_status[new_i][new_j] == hit_client_flag) {		//�����֮����ӱ�ɫ
						System.out.println("direction: " + direction.getKey() + ", " + direction.getValue());
						while(new_i != i || new_j != j) {		//���һ��ѭ����ı�hit_index
							System.out.println("!!!");
							new_i -= direction.getKey();
							new_j -= direction.getValue();
							//assert(chess_board_status[new_i][new_j] == another_client_flag);
							System.out.println(new_i +","+new_j + " changed");
							chess_board_status[new_i][new_j] = hit_client_flag;
						}
					}
					break;		//�����ո����ɴ��˳�
				}
			}
			
			//��ÿ��λ�ü�鲢����available
			for(int index = 0; index < 64; index++)
				checkAndSetAvailable(index);
		}
		
		//��鲢����available��־
		public void checkAndSetAvailable(int index) {
			int i = index / 8; int j = index % 8;
			//��ǰλ�������ӣ��˳�
			if(chess_board_status[i][j] == BLACK_FLAG || chess_board_status[i][j] == WHITE_FLAG)
				return;
			chess_board_status[i][j] = NULL_FLAG;	//���ԭ����
			
			boolean black_available = false;
			boolean white_available = false;
			
			//ÿ�������ϵļ��
			for(Map.Entry<Integer, Integer> direction : all_direction) {
				int new_i = i + direction.getKey();
				int new_j = j + direction.getValue();
				if(new_i > 7 || new_i < 0 || new_j > 7 || new_j < 0)	//Խ����
					continue;
				//�ھ�û�����ӣ��˳�����ѭ��
				if(chess_board_status[new_i][new_j] != BLACK_FLAG && chess_board_status[new_i][new_j] != WHITE_FLAG)
					continue;
				int neighbor_color_flag = chess_board_status[new_i][new_j];	//�ھ���ɫ
				int another_color_flag = neighbor_color_flag == BLACK_FLAG ? WHITE_FLAG : BLACK_FLAG;
				
				while(true) {
					new_i += direction.getKey();
					new_j += direction.getValue();
					if(new_i > 7 || new_i < 0 || new_j > 7 || new_j < 0)	//Խ����
						break;
					if(chess_board_status[new_i][new_j] == neighbor_color_flag)
						continue;
					if(chess_board_status[new_i][new_j] == another_color_flag) {
						if(another_color_flag == BLACK_FLAG)
							black_available = true;
						else
							white_available = true;
					}
					break;		//�����ո����ɴ��˳�
				}
				if(black_available && white_available)	//both_available�ˣ�û��Ҫ�ټ��
					break;
			}
			chess_board_status[i][j] = black_available && white_available ? BOTH_AVAILABLE :
				black_available ? BLACK_AVAILABLE : white_available ? WHITE_AVAILABLE : NULL_FLAG;
		}
		
		//��ȡ����
		public ClientRunner getOpponentClient(ClientRunner c) {
			return left_client == c ? right_client : right_client == c ? left_client: null;	
		}
		
		//������client
		public void addClient(int seat_num, ClientRunner c) {
			assert(seat_num >= 0 && seat_num < table_sum * 2);
			if(seat_num % 2 == 0)
				left_client = c;
			else
				right_client = c;
		}
		
		//ɾ��client
		public void removeClient(ClientRunner c) {
			if(left_client == c)
				left_client = null;
			else if(right_client == c)
				right_client = null;
		}

		//�ж����Ƿ���������ս˫������������
		public boolean isFull() {
			return left_client != null && right_client != null;
		}
		
		//����Ϣ������table��Ա����ս˫�����οͣ�
		public void notifyAllTableMember(Message message)  {
			assert(isFull());
			left_client.NIOSendMessage(message);
			right_client.NIOSendMessage(message);
			for(ClientRunner spectator : spectator_list)
				spectator.NIOSendMessage(message);
		}
	}
	
	/*
	 * �ڲ��ࣺClient
	 */
	class ClientRunner {
		//��MessageMonitor�ַ���message��notify����֮��������
		public BlockingQueue<Message> message_to_handle = new LinkedBlockingQueue<Message>();

		//message�ֽڶ�������һ��message�ĳ��ȣ�����message֮����������Ϊ-1
		public AtomicInteger next_message_length;
		
		//����NIO��socket_channel
		public SocketChannel socket_channel = null;
		public BlockingQueue<Byte> message_byte_q = new LinkedBlockingQueue<Byte>();
		
		
		private String id, password;
		private int seat_num;		//��־Clientλ�õ�Ψһ����
		
		private boolean ready = false;	//�Ƿ�׼���ÿ�ʼ��Ϸ
		
		private String color;	//�ַ�����ɫ��
		
		
		public ClientRunner() {
			System.out.println("new ClientRunner !!!");
			initialize();
		}
		
		private void initialize() {
			next_message_length = new AtomicInteger(-1);
		}

		//�������ݶ���
		public void NIOSendMessage(Message message) {
			byte[] message_bytes = SerializableUtil.toBytes(message);

			byte[] length_bytes = BytesIntTransfer.Int2Bytes(message_bytes.length);
			ByteBuffer length_buffer = ByteBuffer.wrap(length_bytes);
			ByteBuffer message_buffer = ByteBuffer.wrap(message_bytes);

			try {
				//message��������
				while(length_buffer.hasRemaining()) {
					System.out.println("ID: " + id + " sending " + message);
					socket_channel.write(length_buffer);
				}
				//����message
				socket_channel.write(message_buffer);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		
		
		//����û��������Ƿ�Ϸ�
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
		
		//���id�Ƿ�δ��ע��
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
		
		//ע�����û�
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
		
		//����
		public void standUp() {
			if(seat_num < 0)
				return;
			Table in_table = getTableBySeatNum(seat_num);
			in_table.removeClient(this);
			//��ս�����飨��Ҫ��ʱ�Ķ�ս�����飩�������
			timer_task.removeTable(in_table);
			seat_num = -1;
		}
		
		//����
		public void sitDown(int num) {
			assert(num >= 0 && num < table_sum * 2);
			seat_num = num;
			getTableBySeatNum(num).addClient(num, this);
		}
		
		
		//��ȡһ������ȫ����λ��Ϣ��seat_status_type��Ϣ����
 		public Message getSeatStatusMessage() {
			String seat_status = "";
			for(Table table : all_table) {
				if(table.left_client != null)
					seat_status += table.left_client.id + " ";	//�ո��Ǽ����
				else
					seat_status += "#" + " ";
				
				if(table.right_client != null)
					seat_status += table.right_client.id + " ";	//�ո��Ǽ����
				else
					seat_status +=  "#" + " ";
			}
			
			return Message.newMessage(Message.MessageType.SEAT_STATUS_TYPE, seat_status);
		}
		
 		
 		//��ʱ����һ���ȥ��֪ͨ�Լ�����client
 		public void oneSecond() throws IOException {
 			//ע�⣺����throws������timer_task�����쳣��Ȼ��ɾ����Ӧtable
			NIOSendMessage(Message.newMessage(Message.MessageType.ONE_SECOND, ""));
 		}
 		
 		
		//������Ϣ���ʹ���ͬ��Ϣ
		public void handleMessage() {
			while(message_to_handle.size() > 0) {
				Message message = message_to_handle.poll();
				Message.MessageType message_type = message.getType();

				System.out.println("handling " + message + " by client " + id); 
				
				if(message_type == Message.MessageType.LOGIN_TYPE)				//��¼��Ϣ
					handleLoginMessage(message.getBuf());
				else if(message_type == Message.MessageType.REGISTER_TYPE)		//ע����Ϣ
					handleRegisterMessage(message.getBuf());
				else if(message_type == Message.MessageType.SEAT_TYPE)			//��λ��Ϣ
					handleSeatMessage(message.getBuf());
				else if(message_type == Message.MessageType.REVERSI_TYPE)		//������Ϣ
					handleReversiMessage(message.getBuf());
				else if(message_type == Message.MessageType.CHAT_MESSAGE_TYPE)	//������Ϣ
					handleChatMessage(message.getBuf());
				else if(message_type == Message.MessageType.CLIENT_QUIT)		//client�˳�
					handleClientQuit();
				else if(message_type == Message.MessageType.GAME_READY)			//׼������
					handleGameReady();
				else if(message_type == Message.MessageType.LOSE_GAME)			//��Ϸ��
					handleLoseGame();
				else if(message_type == Message.MessageType.SPECTATOR_TYPE)		//�����ս
					handleSpectator(message.getBuf());
			}

		}
		
		
		//�µ�spectator
		public void handleSpectator(String spectator_button_num) {
			int table_num = Integer.parseInt(spectator_button_num);
			Table table = all_table.get(table_num);
			//��ս˫��δ�������������ս
			if(!table.isFull())
				return;
			
		}
		
		//�����и�
		public void handleLoseGame() {
			Table game_table = getTableBySeatNum(seat_num);
			//һ�ֽ�����ˢ��ʤ��ͳ�ƣ��������ʧ�ܷ�
			game_table.gameOver(this);

			int black_win = game_table.left_client.color.equals(BLACK)?game_table.left_win_count:game_table.right_win_count;
			int white_win = game_table.left_client.color.equals(WHITE)?game_table.left_win_count:game_table.right_win_count;
			
			//ע�⣺һ�ֽ���ֻ����win_count������ͳ����Ϣ�¾ֿ�ʼʱ��ˢ��
			Message win_count_message = 
					Message.newMessage(Message.MessageType.WIN_COUNT_TYPE, black_win + " " + white_win);
			game_table.notifyAllTableMember(win_count_message);
			
			//buf��ʾʧ��һ����id
			Message game_over_message = 
					Message.newMessage(Message.MessageType.GAME_OVER_TYPE, this.id);
			game_table.notifyAllTableMember(game_over_message);
		}
		
		
		//������Ϸ��ʼ����
		public void handleGameReady() {
			synchronized(getTableBySeatNum(seat_num)) {
				this.ready = true;
				Table game_table = getTableBySeatNum(seat_num);
				ClientRunner opponent_client = game_table.getOpponentClient(this);
				if(opponent_client == null || opponent_client.ready == false)	//����δ������û׼����
					return;
				
				//��Ϸ��ʼ
				game_table.gameBegin();
			}
		}
		
		//����������Ϣ
		public void handleChatMessage(String buf) {
			String chat_content = id + ": " + buf;
			Message chat_message = Message.newMessage(Message.MessageType.CHAT_MESSAGE_TYPE, chat_content);
			getTableBySeatNum(seat_num).notifyAllTableMember(chat_message);
		}
		
		
		//����client�˳���Ϣ
		public void handleClientQuit() {
			synchronized(getTableBySeatNum(seat_num)) {
				//terminate(Thread.currentThread());
				terminate();
				//ע�⣬��client�˳�ʱ������seat_status_message��֪ͨ����client
				notifyAllClient(getSeatStatusMessage());
				//System.out.println("client " + id + " is going to notifyAllClient !!!");

			}
		}
		
		//�����¼��Ϣ
		public void handleLoginMessage(String buf) {
			if (checkIdPassword(buf).equals(true)){ // �Ϸ����û�������
				NIOSendMessage(Message.newMessage(Message.MessageType.LOGIN_SUCCESS, ""));
				NIOSendMessage(getSeatStatusMessage());
			}
			else
				NIOSendMessage(Message.newMessage(Message.MessageType.WRONG_ID_PASSWORD, ""));
		}
		
		//����ע����Ϣ
		public void handleRegisterMessage(String buf) {
			if (checkIdAvailable(buf).equals(true)) { // ���û�����δע��
				registerNewId();
				NIOSendMessage(Message.newMessage(Message.MessageType.REGISTER_SUCCESS, ""));
			} else
				NIOSendMessage(Message.newMessage(Message.MessageType.ID_ALREADY_EXIST, ""));
		}
		
		//������λ��Ϣ
		public void handleSeatMessage(String buf) {
			System.out.println("buf: " + buf);
			//��ȡ��λ��
			int num = Integer.parseInt(buf);
			//��ȡ��λ���ϵ�ǰclient
			ClientRunner client_in_the_seat = getClientBySeatNum(num);
			//��ȡ��λ�Ŷ�Ӧtable
			Table going_table = getTableBySeatNum(num);

			synchronized (going_table) {
				//�������Լ�������
				if(this.equals(client_in_the_seat)) {
					standUp();
					//�����ɹ���ִ
					NIOSendMessage(Message.newMessage(Message.MessageType.SEAT_OFF_SUCCESS, ""));
					//��������client����λ����
					notifyAllClient(getSeatStatusMessage());
				}
				//�����б��ˣ�����ʧ��
				else if (client_in_the_seat != null)
					NIOSendMessage(Message.newMessage(Message.MessageType.SEATED_FAIL, ""));
				//����û�ˣ���������
				else {
					standUp(); sitDown(num);	//����
					//֪ͨ�ͻ���������
					NIOSendMessage(Message.newMessage(Message.MessageType.SEATED_SUCCESS, ""));
					System.out.println("going to sent seated_success to client !!!");
					//��������client����λ����
					notifyAllClient(getSeatStatusMessage());
					//����֮�����Ƿ񿪾�
					if(getTableBySeatNum(seat_num).isFull())
						GameServer.this.launchGameWindow(getTableBySeatNum(seat_num));
				}
			}
		}
		
		//����������Ϣ
		public void handleReversiMessage(String buf) {
			System.out.println("id: " + id);
			
			Table game_table = getTableBySeatNum(seat_num);
			System.out.println("game_table.turn_client.id: " + game_table.turn_client.id);

			if(game_table.turn_client != this)
				return;	//�����巽�������Ч
			int index = Integer.parseInt(buf);	//���̵��λ��
			int index_status = game_table.chess_board_status[index/8][index%8] ;
			
			System.out.println("index_status: " + index_status);
			//��������λ�ã������Ч
			if(index_status != (color.equals(BLACK) ? BLACK_AVAILABLE : WHITE_AVAILABLE)
					&& index_status != BOTH_AVAILABLE)
				return;
			
			System.out.println("888");
			
			game_table.turn_client = game_table.getOpponentClient(game_table.turn_client);	//���巽ת��
			game_table.adjustChessBoardStatus(index, color);	//�������̸��
			
			game_table.refreshPieceCount();		//ˢ������ͳ��
			//ע�⣺client�˵�����ͳ�Ƶ�ˢ�������Լ����
			
			Message chess_board_status_message = game_table.getChessBoardStatusMessage();
			game_table.notifyAllTableMember(chess_board_status_message);	//���̸����Ϣ
			
			Message turn_message = Message.newMessage(Message.MessageType.TURN_TYPE, game_table.getOpponentClient(this).id);
			game_table.notifyAllTableMember(turn_message);	//���巽��Ϣ
			
			
		}

		public void terminate() {
			System.out.println("client " + id + "terminate !!!");
			standUp();
			//�ȹص��Լ���socket�������Լ���all_client���Ƴ����Ͳ������Լ�����
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
 * д��handleLoseGame()
 * 
 * 
 * 
 ********/
















