/*
 * 程序入口，登录界面
 */

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import mine.terminate.Terminable;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import mine.terminate.*;


public class Login implements ActionListener {
	//一局时限常量（单位：秒）
	private static final int TIME_LIMIT = 5;
	
	//颜色常量
	private static final String BLACK = "black";
	private static final String WHITE = "white";
	
	//登录框、连接失败框、游戏大厅框
	public enum Status {
		LOGIN_REGISTER, CONNECT_FAILURE, GAME_LOBBY, GAME_WINDOWS
	}
	static Thread client_thread;
	static ClientRunner client_runner;
	static JFrame top_frame = null;
	static Socket s = null;
	//static ObjectInputStream ois;
	//static ObjectOutputStream oos;
	static String id = null, password = null;
	static JButton seat = null;
	
	
	/*** only for 登录注册界面  ***/
	static JButton button_login = new JButton("登录");
	static JButton button_register = new JButton("注册");
	static JTextField textfield_id = new JTextField();			//id输入框
	static JTextField textfield_password = new JTextField();	//pwssword输入框
	static JButton button_connect_failure_confirm = new JButton("确定");
	
	/*** only for 游戏大厅界面  ***/
	static final int table_sum = 4; //座位数
	static ArrayList<Table> all_table = new ArrayList<Table>();	//所有对战桌
	
	/*** only for 游戏窗口界面  ***/
	static final int BLACK_AVAILABLE = 1;
	static final int WHITE_AVAILABLE = 2;
	static final int BOTH_AVAILABLE = 3;
	static final int BLACK_FLAG = 4;
	static final int WHITE_FLAG = 5;
	static final int NULL_FLAG = 6;
	static int black_win_count = 0, white_win_count = 0;	//胜利局数
	static JPanel chess_board = new JPanel();	//棋盘
	static int[][] chess_board_status = new int[8][8];		//棋盘局面
	static ArrayList<JButton> all_piece_button = new ArrayList<JButton>();	//棋格按钮
	static JTextField chat_input_field = new JTextField();	//聊天内容输入区
	static TextArea chat_display_area = new TextArea();	//聊天内容显示区
	static JTextArea black_piece_count_area = new JTextArea(), white_piece_count_area = new JTextArea();		//棋子计数
	static JTextArea black_time_rest_area = new JTextArea(), white_time_rest_area = new JTextArea();	//剩余时间
	static int black_time_rest;	//黑方剩余时间（秒）
	static int white_time_rest;	//白方剩余时间（秒）
	static ImageIcon black_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\黑.jpg");
	static ImageIcon white_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\白.jpg");
	static ImageIcon null_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\背景.jpg");
	static ImageIcon available_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\icon.jpg");
	static ImageIcon up_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\上.jpg");
	static ImageIcon down_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\下.jpg");
	static ImageIcon left_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\左.jpg");
	static ImageIcon right_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\右.jpg");
	static JLabel black_player_id_label = new JLabel("player_id_1");	//玩家id
	static JLabel white_player_id_label = new JLabel("player_id_2");
	static JLabel black_win_count_label = new JLabel(Integer.toString(black_win_count));	//胜利局数
	static JLabel white_win_count_label = new JLabel(Integer.toString(white_win_count));
	static JLabel vs_label = new JLabel("比");	//“比”
	static JLabel up_bound = new JLabel(up_bound_icon);	//上下左右木质边框
	static JLabel down_bound = new JLabel(down_bound_icon);
	static JLabel left_bound = new JLabel(left_bound_icon);
	static JLabel right_bound = new JLabel(right_bound_icon);
	static JMenuBar menubar = new JMenuBar();	//菜单栏
	static JMenu game_menu = new JMenu("游戏");	//游戏菜单
	static JMenuItem new_game_item = new JMenuItem("新局");
	static JMenuItem quit_item = new JMenuItem("退出");
	static JMenu option_menu = new JMenu("选项");	//选项菜单
	static JMenuItem undo_item = new JMenuItem("悔棋");
	static JMenuItem avalible_item = new JMenuItem("可下子位置");
	static JMenu help_menu = new JMenu("帮助");	//帮助菜单
	static JMenuItem help_item = new JMenuItem("黑白棋帮助");
	static 	JMenuItem about_item = new JMenuItem("关于黑白棋");
	static 	JLabel left_piece_icon = new JLabel(black_piece_icon);	//计分板
	static JLabel right_piece_icon = new JLabel(white_piece_icon);
	
	
	//棋局变量
	static String color;
	static boolean turn = false;
	
		
	//由MessageMonitor分发的message，notify唤醒之后处理它们
	public BlockingQueue<Message> message_to_handle = new LinkedBlockingQueue<Message>();

	//message字节队列中下一个message的长度，处理message之后立即设置为-1
	public AtomicInteger next_message_length;
	
	//负责NIO的socket_channel
	public SocketChannel socket_channel = null;
	public BlockingQueue<Byte> message_byte_q = new LinkedBlockingQueue<Byte>();
	
	
	//剩余时间->显示字符串
	public String time2String(int time_rest) {
		int minute, second;
		String minute_str, second_str;
		minute = time_rest / 60; second = time_rest % 60;
		minute_str = minute >= 10 ? Integer.toString(minute) : "0" + Integer.toString(minute);
		second_str = second >= 10 ? Integer.toString(second) : "0" + Integer.toString(second);
		return minute_str + ":" + second_str;
	}
	
	
	/*
	 * 内部类：Table
	 */
	class Table {
		private JButton left_seat = new JButton();
		private JButton right_seat = new JButton();
		private JButton spectator = new JButton("观战");
	}
	
	
	/*
	 * 内部类：Window
	 */
	class Window extends JFrame {
		
		public Window(Status status, String title) {
			//配置窗口
			if(status == Status.LOGIN_REGISTER)
				frameConfigLoginRegister(title);
			else if(status == Status.CONNECT_FAILURE)
				frameConfigConnectFailure();
			else if(status == Status.GAME_LOBBY)
				frameConfigGameLobby();
			else if(status == Status.GAME_WINDOWS)
				frameConfigGameWindow("Reversi by go2sesa");
			
			
			//统一添加WindowListener
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					try {
						if(client_runner != null) {
							client_runner.terminate(client_thread);						
							client_thread.join();
						}
					} catch (InterruptedException e1) {	//InterruptedException不管
					} catch (Exception e2) {
						e2.printStackTrace();
					}
					System.exit(0);
				}
			});
		}
		
		//配置游戏窗口框
		public void frameConfigGameWindow(String title) {
			setTitle(title);
			setBounds(190, 90, 760, 490);
			setResizable(false);
			setLayout(null);
			setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			//玩家
			black_player_id_label.setBounds(490, 365, 80, 50);
			white_player_id_label.setBounds(635, 365, 80, 50);
			add(black_player_id_label); add(white_player_id_label);
			//胜利局数
			black_win_count_label.setBounds(505, 400, 60, 50);
			white_win_count_label.setBounds(655, 400, 60, 50);
			add(black_win_count_label); add(white_win_count_label);
			//“比”
			vs_label.setBounds(586, 400, 50, 50); add(vs_label);
			//上下左右木质边框
			up_bound.setBounds(0, 0, 440, 27);
			down_bound.setBounds(0, 409, 440, 35);
			left_bound.setBounds(0, 21, 27, 390);
			right_bound.setBounds(411, 27, 29, 390);
			add(left_bound); add(down_bound); add(up_bound); add(right_bound);
			/***菜单栏***/
			//游戏菜单
			new_game_item.addActionListener(Login.this); game_menu.add(new_game_item);
			quit_item.addActionListener(Login.this); game_menu.add(quit_item);
			menubar.add(game_menu);
			//选项菜单
			undo_item.addActionListener(Login.this); option_menu.add(undo_item);
			avalible_item.addActionListener(Login.this); option_menu.add(avalible_item);
			menubar.add(option_menu);
			//帮助菜单
			help_item.addActionListener(Login.this); help_menu.add(help_item);
			about_item.addActionListener(Login.this); help_menu.add(about_item);
			menubar.add(help_menu);
			setJMenuBar(menubar);
			//添加两个聊天组件
			chat_display_area.setBounds(450, 00, 300, 200); add(chat_display_area);
			chat_input_field.setBounds(450, 205, 300, 50); add(chat_input_field);
			//添加棋格按钮
			chess_board.setBounds(24, 24, 390, 390);
			chess_board.setLayout(new GridLayout(8, 8));
			for (int i = 0; i < 64; i++) {
				JButton button = new JButton(null_piece_icon);
				button.addActionListener(Login.this);
				all_piece_button.add(button);
				chess_board.add(button);
			}
			add(chess_board);
			(all_piece_button.get(27)).setIcon(black_piece_icon); (all_piece_button.get(36)).setIcon(black_piece_icon);	//俩黑子图标
			(all_piece_button.get(28)).setIcon(white_piece_icon); (all_piece_button.get(35)).setIcon(white_piece_icon);	//俩白子图标
			chess_board_status[3][3] = chess_board_status[4][4] = BLACK_FLAG;	//俩黑子
			chess_board_status[3][4] = chess_board_status[4][3] = WHITE_FLAG;	//俩白子
			chess_board_status[2][4] = chess_board_status[3][5] = chess_board_status[4][2] = chess_board_status[5][3] = BLACK_AVAILABLE; //黑子可下
			chess_board_status[2][3] = chess_board_status[4][5] = chess_board_status[5][4] = chess_board_status[3][2] = WHITE_AVAILABLE; //白子可下
			//计分板
			left_piece_icon.setBounds(450, 265, 50, 50); add(left_piece_icon);
			right_piece_icon.setBounds(595, 265, 50, 50); add(right_piece_icon);
			black_piece_count_area.setBounds(505, 265, 80, 50); black_piece_count_area.setFont(new Font("黑体", Font.BOLD, 45)); add(black_piece_count_area);
			white_piece_count_area.setBounds(650, 265, 80, 50);white_piece_count_area.setFont(new Font("黑体", Font.BOLD, 45)); add(white_piece_count_area);
			black_piece_count_area.setText("2");
			white_piece_count_area.setText("2");
			
			//计时器
			black_time_rest_area.setFont(new Font("黑体", Font.BOLD, 35)); black_time_rest_area.setBounds(450, 325, 130, 45); add(black_time_rest_area);
			white_time_rest_area.setFont(new Font("黑体", Font.BOLD, 35)); white_time_rest_area.setBounds(595, 325, 130, 45); add(white_time_rest_area);
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));

			setVisible(true);
		}
		
		
		
		
		//配置游戏大厅框
		public void frameConfigGameLobby() {
			setTitle("Game Lobby");
			setLayout(null);
			setBounds(200, 200, 550, 420);
			setVisible(true);

			all_table.get(0).left_seat.setBounds(30, 30, 110, 80);
			all_table.get(0).right_seat.setBounds(140, 30, 110, 80);
			all_table.get(1).left_seat.setBounds(280, 30, 110, 80);
			all_table.get(1).right_seat.setBounds(390, 30, 110, 80);
			all_table.get(2).left_seat.setBounds(30, 220, 110, 80);
			all_table.get(2).right_seat.setBounds(140, 220, 110, 80);
			all_table.get(3).left_seat.setBounds(280, 220, 110, 80);
			all_table.get(3).right_seat.setBounds(390, 220, 110, 80);
			
			all_table.get(0).spectator.setBounds(100, 120, 80, 50); 
			all_table.get(1).spectator.setBounds(350, 120, 80, 50);
			all_table.get(2).spectator.setBounds(100, 310, 80, 50);
			all_table.get(3).spectator.setBounds(350, 310, 80, 50);

			// 添加
			for (Table table : all_table) {
				add(table.left_seat); add(table.right_seat); add(table.spectator);
			}
		}
		
		//配置登录-注册框
		public void frameConfigLoginRegister(String title) {
			setTitle(title);
			setLayout(null);
			setBounds(450, 200, 320, 200);
			setVisible(true);
			//昵称label、textfield
			JLabel label_id = new JLabel("昵称");
			label_id.setBounds(5, 5, 60, 30); add(label_id);
			textfield_id.setBounds(65, 5, 150, 30); add(textfield_id);
			//密码label、textfield
			JLabel label_password = new JLabel("密码");
			label_password.setBounds(5, 35, 60, 30); add(label_password);
			textfield_password.setBounds(65, 35, 150, 30); add(textfield_password);
			//登录、注册按钮
			button_login.setBounds(5, 100, 100, 50); add(button_login);
			button_register.setBounds(110, 100, 100, 50); add(button_register);
		}
		
		//配置连接失败框
		public void frameConfigConnectFailure() {
			setBounds(600, 200, 200, 200);
			setVisible(true);
			
			JPanel panel_connect_failure = new JPanel();
			panel_connect_failure.setLayout(new GridLayout(2,1));
			JLabel label_connect_failure = new JLabel("连接网络失败，请重新启动程序");
			panel_connect_failure.add(label_connect_failure);
			panel_connect_failure.add(button_connect_failure_confirm);
			
			getContentPane().add(panel_connect_failure);
		}
		
	}
	
	
	public Login() {
		//初始化座位
		for(int i = 0; i < table_sum; i++)
			all_table.add(new Table());
		
		//注意：一个组件可添加多个监听器，因此放入Login的构造方法统一添加，而不是Window的构造方法，保证只有一个监听器
		chat_input_field.addActionListener(this);
		for (Table table : all_table) {
			table.left_seat.addActionListener(this);
			table.right_seat.addActionListener(this);
			table.spectator.addActionListener(this);
		}
		button_login.addActionListener(Login.this); 
		button_register.addActionListener(Login.this); 
		button_connect_failure_confirm.addActionListener(Login.this);
		
		//初始状态时的top_frame是登录框
		top_frame = new Window(Status.LOGIN_REGISTER, "Reversi by go2sea");
		
		//top_frame = new Window(Status.GAME_WINDOWS, "Game Window");
	}
	
	

	public boolean connectServer() {
		try {
			//NIO通道
			socket_channel = SocketChannel.open();
			socket_channel.configureBlocking(false);
			SocketAddress socketAddress = new InetSocketAddress("localhost", 5555);
			socket_channel.connect(socketAddress);
			if(socket_channel.isConnectionPending())
				socket_channel.finishConnect();

			System.out.println("connected !!!");
		
			//启动IO线程（ClientRunner）
			client_runner = new ClientRunner();
			client_thread = new Thread(client_runner);
			client_thread.start();
			//注意：主线程仍在运行，负责向服务器write
			//启动消息监视线程
			new Thread(new MessageMonitor()).start();
			
			return true;
			
		} catch (UnknownHostException e) {
			top_frame.dispose();
			top_frame = new Window(Status.CONNECT_FAILURE, null);
		} catch (IOException e) {
			top_frame.dispose();
			top_frame = new Window(Status.CONNECT_FAILURE, null);
		}
		return false;
	}
	
	
	//根据seat获取编号，若source不是seat，返回-1
	public int getSeatNum(Object source) {
		for(int i = 0; i < all_table.size(); i++)
			if(all_table.get(i).left_seat == source)
				return i * 2;
			else if(all_table.get(i).right_seat == source)
				return i * 2 + 1;
		return -1;
	}
	
	//根据观战按钮返回编号，若source不是观战，返回-1
	public int getSpectatorNum(Object source) {
		for(int i = 0; i < all_table.size(); i++)
			if(all_table.get(i).spectator == source)
				return i;
		return -1;
	}
	
	//根据编号返回seat
	public JButton getSeatByNum(int seat_num) {
		assert(seat_num >= 0 && seat_num < table_sum * 2);
		
		int table_id = seat_num / 2;
		if(seat_num % 2 == 0)
			return all_table.get(table_id).left_seat;
		return all_table.get(table_id).right_seat;
	}

	
	//发送数据对象
	public void NIOSendMessage(Message message) throws IOException {
		
		System.out.println("client " + id + " sending " + message);
		
		byte[] message_bytes = SerializableUtil.toBytes(message);

		byte[] length_bytes = BytesIntTransfer.Int2Bytes(message_bytes.length);
		ByteBuffer length_buffer = ByteBuffer.wrap(length_bytes);
		ByteBuffer message_buffer = ByteBuffer.wrap(message_bytes);

		//message长度先行
		while(length_buffer.hasRemaining())
			socket_channel.write(length_buffer);
		//真正message
		socket_channel.write(message_buffer);

	}


	@Override //来自接口：ActionListener
	public void actionPerformed(ActionEvent e) {
		//连接失败确认按钮
		if (e.getSource() == button_connect_failure_confirm)
			System.exit(0);

		try {
			// 登录按钮
			if (e.getSource() == button_login) {
				id = textfield_id.getText();
				password = textfield_password.getText();
				if(password.equals("") || id.equals(""))
					return;
				if(s == null && !connectServer())
					return;
				
				NIOSendMessage(Message.newMessage(id, Message.MessageType.LOGIN_TYPE, id + " " + password));
			}
			// 注册按钮
			else if (e.getSource() == button_register) {
				String string_id = textfield_id.getText();
				String string_password = textfield_password.getText();
				if(string_password.equals("") || string_id.equals(""))
					return;
				if(s == null && !connectServer())
					return;
				NIOSendMessage(Message.newMessage(id, Message.MessageType.REGISTER_TYPE, string_id + " " + string_password));
			}
			// 八个座位
			else if (getSeatNum(e.getSource()) >= 0) {
				String id_on_seat = ((JButton) e.getSource()).getText();				
				//如果该座位已被别人占用，什么都不做
				if(!id_on_seat.equals("") && !id_on_seat.equals(id))
					return;
				//座位空，入座
				if(id_on_seat.equals("")) {
					if(seat != null)	//先离座（注意：初始时getText不是null）
						seat.setText("");
					seat = (JButton) e.getSource();
					NIOSendMessage(Message.newMessage(id, Message.MessageType.SEAT_TYPE, Integer.toString(getSeatNum(seat))));
				}
				//座位上是自己，离开座位
				else {
					System.out.println("座位上是自己，离开座位");
					assert(id_on_seat.equals(id));
					NIOSendMessage(Message.newMessage(id, Message.MessageType.SEAT_TYPE, Integer.toString(getSeatNum(seat))));	//与入座时发送信息一致，服务器区别处理
				}
			}
			//四个观战按钮
			else if(getSpectatorNum(e.getSource()) >= 0) {
				int spectator_num = getSpectatorNum(e.getSource());
				NIOSendMessage(Message.newMessage(Message.MessageType.SPECTATOR_TYPE, Integer.toString(spectator_num)));
			}
			//文本输入框
			else if(e.getSource() == chat_input_field) {
				String chat_message = chat_input_field.getText().trim();
				chat_input_field.setText("");
				NIOSendMessage(Message.newMessage(id, Message.MessageType.CHAT_MESSAGE_TYPE, chat_message));
				//注意，不需要立即更改chat_display_area的显示，等待服务器返回消息后再更改
			}
			//新局菜单项
			else if(e.getSource() == new_game_item) {
				NIOSendMessage(Message.newMessage(id, Message.MessageType.GAME_READY, ""));
			}
			//棋格按钮
			else if(all_piece_button.contains((JButton) e.getSource())) {
				if(!turn)
					return;
				int index = all_piece_button.indexOf((JButton) e.getSource());
				NIOSendMessage(Message.newMessage(id, Message.MessageType.REVERSI_TYPE, Integer.toString(index)));
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}
	
	
	public static void main(String[] args) {
		new Login();
	}
	
	
	
	
	//消息监视线程：①监视&处理各个client消息字节队列 ②生成消息对象加入client的消息队列并处理
	class MessageMonitor implements Runnable {
		public boolean watching = true;

		public void run() {
			while(watching) {
				tryTosetNextMessageLength();
				tryToReceiveNextMessage();
				tryToHandleNextMessage();
				try {
					TimeUnit.MICROSECONDS.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		//设置下一条待接收的message的长度
		public void tryTosetNextMessageLength() {
			//上一条设置的还未处理 || 新的4字节length未到达，直接返回
			if(next_message_length.get() > 0 || message_byte_q.size() < 4)
				return;
			//取对象长度
			byte[] length_bytes = new byte[4];
			for(int i = 0; i < 4; i++)
				length_bytes[i] = message_byte_q.poll();
			int length = BytesIntTransfer.Bytes2Int(length_bytes);
			System.out.println("下个对象长度： " + length);
			next_message_length.set(length);
		}
		
		//消息字节数据 ——>消息对象，放入client的消息队列
		public void tryToReceiveNextMessage() {
			//4字节长度标志 或 消息体未到达，直接返回
			if(next_message_length.get() < 0 
					|| message_byte_q.size() < next_message_length.get())
				return;
			
			//取出对象字节流&反序列化&分发
			byte[] obj_bytes = new byte[next_message_length.get()];
			for(int i = 0; i < next_message_length.get(); i++)
				obj_bytes[i] = message_byte_q.poll();
			Message new_message = (Message) SerializableUtil.toObject(obj_bytes);
			message_to_handle.add(new_message);	//加入待处理消息队列
			System.out.println("new_message.type: " + new_message.getType());
			//注意：恢复-1准备下个消息的接收
			next_message_length.set(-1);
		}
		
		//处理消息
		public void tryToHandleNextMessage() {
			if(message_to_handle.size() <= 0)
				return;
			
			/***
			 * 注意：Server端有专门的IO读取线程NIOServer，message由ClientRunner处理
			 * Client端有所不同，ClientRunner用来接收NIO输入，main线程用来处理一部分NIO输出
			 * message的处理由MessageMonitor完成（因为Client端的消息压力小，所以监视线程可以
			 * 用来处理message而不会造成接收的延迟）
			 ****/
			//MessageMonitor线程亲自handleMessage
			client_runner.handleMessage();
			
		}
	}
	
	
	
	
	
	
	
	/*
	 * 内部类：客户端线程
	 */
	class ClientRunner implements Runnable, Terminable {
		//用于优雅地结束线程
		private TerminationToken termination_token = new TerminationToken();
		private Exception ex_for_cleanup = null;	//根据异常类型doCleanup
		
		public ClientRunner() {
			System.out.println("new ClientRunner !!!");
			next_message_length = new AtomicInteger(-1);
		}
		
		
		
		
		//消息处理的统一入口
		public void handleMessage() {
			while(message_to_handle.size() > 0) {
				Message message = message_to_handle.poll();
				System.out.println(message);
				
				Message.MessageType message_type = message.getType();
				
				if(message_type != Message.MessageType.ONE_SECOND)
					System.out.println("message.type: " + message.getType() + "\nmessage.buf: "+ message.getBuf());

				
				if(message_type == Message.MessageType.LOGIN_SUCCESS) {			//登录成功
					top_frame.dispose(); // 当前窗口关闭
					top_frame = new Window(Status.GAME_LOBBY, null);
				}
				else if(message_type == Message.MessageType.WRONG_ID_PASSWORD) {//用户名密码错误
					top_frame.dispose(); // 当前窗口关闭
					top_frame = new Window(Status.LOGIN_REGISTER, "用户名或密码错误");
				}
				else if(message_type == Message.MessageType.REGISTER_SUCCESS) {	//注册成功
					top_frame.dispose(); // 当前窗口关闭
					top_frame = new Window(Status.LOGIN_REGISTER, "注册成功，请登录");
				}
				else if(message_type == Message.MessageType.ID_ALREADY_EXIST) {	//用户名已被注册
					top_frame.dispose(); // 当前窗口关闭
					top_frame = new Window(Status.LOGIN_REGISTER, "该用户名已被注册");
				}
				else if(message_type == Message.MessageType.SEATED_SUCCESS)		//入座成功
					seat.setText(id);
				else if(message_type == Message.MessageType.SEAT_OFF_SUCCESS) {	//离座成功
					seat.setText(""); seat = null;
				}
				else if(message_type == Message.MessageType.SEATED_FAIL)		//入座失败
					seat = null;
				else if(message_type == Message.MessageType.SEAT_STATUS_TYPE)	//座位变更
					handleSeatStatusMessage(message.getBuf());
				else if(message_type == Message.MessageType.CHAT_MESSAGE_TYPE)	//聊天消息
					handleChatMessage(message.getBuf());
				else if(message_type == Message.MessageType.OPPONENT_SEATED)	//对手入座
					handleOpponentSeated(message.getBuf());
				else if(message_type == Message.MessageType.GAME_BEGIN_TYPE)	//游戏开始
					handleGameBeginType(message.getBuf());
				else if(message_type == Message.MessageType.ONE_SECOND)			//一秒过去
					handleOneSecond(message.getBuf());
				else if(message_type == Message.MessageType.TURN_TYPE)			//行棋方消息
					handleTurnType(message.getBuf());
				else if(message_type == Message.MessageType.CHESS_BOARD_STATUS_TYPE)	//棋盘格局消息
					handleChessBoardStatusType(message.getBuf());
				else if(message_type == Message.MessageType.WIN_COUNT_TYPE)		//胜负统计消息
					handleWinCountType(message.getBuf());
				else if(message_type == Message.MessageType.GAME_OVER_TYPE)		//一局结束消息
					handleGameOverType(message.getBuf());
			}

		}
		
		//处理一局结束消息
		public void handleGameOverType(String lose_client_id) {
			turn = false;
		}
				
		//处理胜负统计消息
		private void handleWinCountType(String buf) {
			String[] win_count_status = buf.split(" ");
			String black_win = win_count_status[0];
			String white_win = win_count_status[1];
			black_win_count_label.setText(black_win); black_win_count_label.repaint();
			white_win_count_label.setText(white_win); white_win_count_label.repaint();
		}
		
		//处理棋盘格局消息
		private void handleChessBoardStatusType(String buf) {
			String[] status = buf.split(" ");
			assert(status.length == 64);
			for(int index = 0; index < 64; index++)
				chess_board_status[index/8][index%8] = Integer.parseInt(status[index]);
			
			//根据chess_board_status设置图标
			for(int index = 0; index < 64; index++) {
				if(chess_board_status[index/8][index%8] == BLACK_FLAG)
					all_piece_button.get(index).setIcon(black_piece_icon);
				else if(chess_board_status[index/8][index%8] == WHITE_FLAG)
					all_piece_button.get(index).setIcon(white_piece_icon);
				else
					all_piece_button.get(index).setIcon(null_piece_icon);
				all_piece_button.get(index).repaint();
			}
			
			//刷新棋子统计
			int black_piece_count = 0, white_piece_count = 0;
			for(int i = 0; i < 64; i++) {
				if(chess_board_status[i/8][i%8] == BLACK_FLAG)
					black_piece_count++;
				if(chess_board_status[i/8][i%8] == WHITE_FLAG)
					white_piece_count++;
			}
			black_piece_count_area.setText(Integer.toString(black_piece_count)); black_piece_count_area.repaint();
			white_piece_count_area.setText(Integer.toString(white_piece_count)); white_piece_count_area.repaint();
		}
		
		//处理行棋方消息
		private void handleTurnType(String turn_client_id) {
			if(turn_client_id.equals(id))
				turn = true;
			else
				turn = false;
		}
		
		
		//处理计时消息：一秒过去
		private void handleOneSecond(String buf) {
			if((turn && color.equals(BLACK)) || (!turn && color.equals(WHITE))) 
				black_time_rest--;
			else
				white_time_rest--;
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));
			black_time_rest_area.repaint();
			white_time_rest_area.repaint();
			
			//超时处理：判负
			if(turn && black_time_rest * white_time_rest == 0)
				try {
					NIOSendMessage(Message.newMessage(id, Message.MessageType.LOSE_GAME, ""));
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		
		
		
		//处理游戏开始消息
		private void handleGameBeginType(String buf) {
			//注意：棋盘信息的刷新由Server端发来的chess_board_status_message触发，这里无需处理

			//剩余时间刷新
			black_time_rest = white_time_rest = TIME_LIMIT;
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));
			black_time_rest_area.repaint();
			white_time_rest_area.repaint();
			//棋子数目统计刷新
			black_piece_count_area.setText("2");
			white_piece_count_area.setText("2");
			black_piece_count_area.repaint();
			white_piece_count_area.repaint();
			
			String[] buf_list = buf.split(" ");
			if(id.equals(buf_list[0])) {	//先手执黑
				color = BLACK;
				turn = true;
			}
			else {							//后手执白
				assert(id.equals(buf_list[1]));
				color = WHITE;
				turn = false;
			}
		}
		
		//处理chat_message消息
		private void handleChatMessage(String buf) {
			chat_display_area.setText(chat_display_area.getText() + buf + '\n');
		}
		
		//处理对手入座消息
		private void handleOpponentSeated(String buf) {
			top_frame.dispose();
			top_frame = new Window(Status.GAME_WINDOWS, "Game Window");
		}

		//处理seat_status消息
		private void handleSeatStatusMessage(String buf) {		
			String[] client_id_list = buf.split(" ");
			assert(client_id_list.length == table_sum * 2);
			
			for(int i = 0; i < client_id_list.length; i++) {
				if(client_id_list[i].equals("#")) {
					//对手退出，自己直接退出
					if(!getSeatByNum(i).getText().equals("") && getOpponentSeatNum(seat) == i) {
						client_runner.terminate(client_thread);
						System.out.println("对手退出了！！！");
					}
					getSeatByNum(i).setText("");
				}
				else
					getSeatByNum(i).setText(client_id_list[i]);
			}
		}

		
		//获取对面座位的编号（对手座位号）
		private int getOpponentSeatNum(JButton seat) {
			int seat_num = getSeatNum(seat);
			if(seat_num % 2 == 0)
				return seat_num + 1;
			return seat_num - 1;
		}
		

		public void run() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ByteBuffer buffer = ByteBuffer.allocate(1024);

			try {
				while (!termination_token.breakableNow()) {
					byte[] bytes;
					int size = socket_channel.read(buffer);
					if(size == -1) {
						throw new IOException("Client closed !!!");
						//System.out.println()
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
						baos.reset();
						System.out.println("client receive " + bytes.length + " bytes !!!");
						
						//加入byte队列
						for(byte b : bytes)
							message_byte_q.add(b);
						System.out.println("添加" + bytes.length + "到byte队列中");
						System.out.println("目前byte队列长度： " + message_byte_q.size());
						
						
						
						// 登录或注册的返回信息的检查
						//Message message = (Message) ois.readObject();
						//消息处理统一入口
						//handleMessage(message);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				//关闭ois导致正在阻塞的read抛出SocketException: Socket closed ！！！
				ex_for_cleanup = e;
			} finally {
				doCleanup(ex_for_cleanup);
			}
		}

		@Override	//来自接口Terminable（client_therad是当前this对象所在的线程对象）
		public void terminate(Thread client_thread) {			
			//设置toShutdown标志
			termination_token.setToShutdown(true);	//设置toShutdown
			doTerminate();
			if (termination_token.reservations.get() <= 0)
				if(!client_thread.isInterrupted())
					client_thread.interrupt();
		}

		@Override	//终止前准备工作
		public void doTerminate() {
			try {
				/***
				 * 注意：NIO中SocketChannel的close会触发远端的一个OP_READ事件（read返回值为-1）
				 * 因此不用发送标志退出的消息，只需关闭SocketChannel即可
				 ***/
				//通知服务器要退出
				//NIOSendMessage(Message.newMessage(id, Message.MessageType.CLIENT_QUIT, ""));
				
				socket_channel.close();
				//ois.close(); oos.close(); s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void doCleanup(Exception cause) {
			System.out.println("doCleanup !!!");
			//尽管client_thread要关闭，但窗口组件是外部类Login的成员，所以这些得手动关闭
			if(top_frame != null) {
				//注意：调用dispose会抛出InterruptedException 不知为何。。。。
				//曲线救国，先不可见，设置null，让GC回收它
				top_frame.setVisible(false);
				top_frame = null;
			}
		}

	}
	
	/*********** end of ClientRunner ***********/


}
