/*
 * ������ڣ���¼����
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
	//һ��ʱ�޳�������λ���룩
	private static final int TIME_LIMIT = 5;
	
	//��ɫ����
	private static final String BLACK = "black";
	private static final String WHITE = "white";
	
	//��¼������ʧ�ܿ���Ϸ������
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
	
	
	/*** only for ��¼ע�����  ***/
	static JButton button_login = new JButton("��¼");
	static JButton button_register = new JButton("ע��");
	static JTextField textfield_id = new JTextField();			//id�����
	static JTextField textfield_password = new JTextField();	//pwssword�����
	static JButton button_connect_failure_confirm = new JButton("ȷ��");
	
	/*** only for ��Ϸ��������  ***/
	static final int table_sum = 4; //��λ��
	static ArrayList<Table> all_table = new ArrayList<Table>();	//���ж�ս��
	
	/*** only for ��Ϸ���ڽ���  ***/
	static final int BLACK_AVAILABLE = 1;
	static final int WHITE_AVAILABLE = 2;
	static final int BOTH_AVAILABLE = 3;
	static final int BLACK_FLAG = 4;
	static final int WHITE_FLAG = 5;
	static final int NULL_FLAG = 6;
	static int black_win_count = 0, white_win_count = 0;	//ʤ������
	static JPanel chess_board = new JPanel();	//����
	static int[][] chess_board_status = new int[8][8];		//���̾���
	static ArrayList<JButton> all_piece_button = new ArrayList<JButton>();	//���ť
	static JTextField chat_input_field = new JTextField();	//��������������
	static TextArea chat_display_area = new TextArea();	//����������ʾ��
	static JTextArea black_piece_count_area = new JTextArea(), white_piece_count_area = new JTextArea();		//���Ӽ���
	static JTextArea black_time_rest_area = new JTextArea(), white_time_rest_area = new JTextArea();	//ʣ��ʱ��
	static int black_time_rest;	//�ڷ�ʣ��ʱ�䣨�룩
	static int white_time_rest;	//�׷�ʣ��ʱ�䣨�룩
	static ImageIcon black_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static ImageIcon white_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static ImageIcon null_piece_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\����.jpg");
	static ImageIcon available_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\icon.jpg");
	static ImageIcon up_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static ImageIcon down_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static ImageIcon left_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static ImageIcon right_bound_icon = new ImageIcon("D:\\MyCode\\Java\\Reversi\\��.jpg");
	static JLabel black_player_id_label = new JLabel("player_id_1");	//���id
	static JLabel white_player_id_label = new JLabel("player_id_2");
	static JLabel black_win_count_label = new JLabel(Integer.toString(black_win_count));	//ʤ������
	static JLabel white_win_count_label = new JLabel(Integer.toString(white_win_count));
	static JLabel vs_label = new JLabel("��");	//���ȡ�
	static JLabel up_bound = new JLabel(up_bound_icon);	//��������ľ�ʱ߿�
	static JLabel down_bound = new JLabel(down_bound_icon);
	static JLabel left_bound = new JLabel(left_bound_icon);
	static JLabel right_bound = new JLabel(right_bound_icon);
	static JMenuBar menubar = new JMenuBar();	//�˵���
	static JMenu game_menu = new JMenu("��Ϸ");	//��Ϸ�˵�
	static JMenuItem new_game_item = new JMenuItem("�¾�");
	static JMenuItem quit_item = new JMenuItem("�˳�");
	static JMenu option_menu = new JMenu("ѡ��");	//ѡ��˵�
	static JMenuItem undo_item = new JMenuItem("����");
	static JMenuItem avalible_item = new JMenuItem("������λ��");
	static JMenu help_menu = new JMenu("����");	//�����˵�
	static JMenuItem help_item = new JMenuItem("�ڰ������");
	static 	JMenuItem about_item = new JMenuItem("���ںڰ���");
	static 	JLabel left_piece_icon = new JLabel(black_piece_icon);	//�Ʒְ�
	static JLabel right_piece_icon = new JLabel(white_piece_icon);
	
	
	//��ֱ���
	static String color;
	static boolean turn = false;
	
		
	//��MessageMonitor�ַ���message��notify����֮��������
	public BlockingQueue<Message> message_to_handle = new LinkedBlockingQueue<Message>();

	//message�ֽڶ�������һ��message�ĳ��ȣ�����message֮����������Ϊ-1
	public AtomicInteger next_message_length;
	
	//����NIO��socket_channel
	public SocketChannel socket_channel = null;
	public BlockingQueue<Byte> message_byte_q = new LinkedBlockingQueue<Byte>();
	
	
	//ʣ��ʱ��->��ʾ�ַ���
	public String time2String(int time_rest) {
		int minute, second;
		String minute_str, second_str;
		minute = time_rest / 60; second = time_rest % 60;
		minute_str = minute >= 10 ? Integer.toString(minute) : "0" + Integer.toString(minute);
		second_str = second >= 10 ? Integer.toString(second) : "0" + Integer.toString(second);
		return minute_str + ":" + second_str;
	}
	
	
	/*
	 * �ڲ��ࣺTable
	 */
	class Table {
		private JButton left_seat = new JButton();
		private JButton right_seat = new JButton();
		private JButton spectator = new JButton("��ս");
	}
	
	
	/*
	 * �ڲ��ࣺWindow
	 */
	class Window extends JFrame {
		
		public Window(Status status, String title) {
			//���ô���
			if(status == Status.LOGIN_REGISTER)
				frameConfigLoginRegister(title);
			else if(status == Status.CONNECT_FAILURE)
				frameConfigConnectFailure();
			else if(status == Status.GAME_LOBBY)
				frameConfigGameLobby();
			else if(status == Status.GAME_WINDOWS)
				frameConfigGameWindow("Reversi by go2sesa");
			
			
			//ͳһ���WindowListener
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					try {
						if(client_runner != null) {
							client_runner.terminate(client_thread);						
							client_thread.join();
						}
					} catch (InterruptedException e1) {	//InterruptedException����
					} catch (Exception e2) {
						e2.printStackTrace();
					}
					System.exit(0);
				}
			});
		}
		
		//������Ϸ���ڿ�
		public void frameConfigGameWindow(String title) {
			setTitle(title);
			setBounds(190, 90, 760, 490);
			setResizable(false);
			setLayout(null);
			setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			//���
			black_player_id_label.setBounds(490, 365, 80, 50);
			white_player_id_label.setBounds(635, 365, 80, 50);
			add(black_player_id_label); add(white_player_id_label);
			//ʤ������
			black_win_count_label.setBounds(505, 400, 60, 50);
			white_win_count_label.setBounds(655, 400, 60, 50);
			add(black_win_count_label); add(white_win_count_label);
			//���ȡ�
			vs_label.setBounds(586, 400, 50, 50); add(vs_label);
			//��������ľ�ʱ߿�
			up_bound.setBounds(0, 0, 440, 27);
			down_bound.setBounds(0, 409, 440, 35);
			left_bound.setBounds(0, 21, 27, 390);
			right_bound.setBounds(411, 27, 29, 390);
			add(left_bound); add(down_bound); add(up_bound); add(right_bound);
			/***�˵���***/
			//��Ϸ�˵�
			new_game_item.addActionListener(Login.this); game_menu.add(new_game_item);
			quit_item.addActionListener(Login.this); game_menu.add(quit_item);
			menubar.add(game_menu);
			//ѡ��˵�
			undo_item.addActionListener(Login.this); option_menu.add(undo_item);
			avalible_item.addActionListener(Login.this); option_menu.add(avalible_item);
			menubar.add(option_menu);
			//�����˵�
			help_item.addActionListener(Login.this); help_menu.add(help_item);
			about_item.addActionListener(Login.this); help_menu.add(about_item);
			menubar.add(help_menu);
			setJMenuBar(menubar);
			//��������������
			chat_display_area.setBounds(450, 00, 300, 200); add(chat_display_area);
			chat_input_field.setBounds(450, 205, 300, 50); add(chat_input_field);
			//������ť
			chess_board.setBounds(24, 24, 390, 390);
			chess_board.setLayout(new GridLayout(8, 8));
			for (int i = 0; i < 64; i++) {
				JButton button = new JButton(null_piece_icon);
				button.addActionListener(Login.this);
				all_piece_button.add(button);
				chess_board.add(button);
			}
			add(chess_board);
			(all_piece_button.get(27)).setIcon(black_piece_icon); (all_piece_button.get(36)).setIcon(black_piece_icon);	//������ͼ��
			(all_piece_button.get(28)).setIcon(white_piece_icon); (all_piece_button.get(35)).setIcon(white_piece_icon);	//������ͼ��
			chess_board_status[3][3] = chess_board_status[4][4] = BLACK_FLAG;	//������
			chess_board_status[3][4] = chess_board_status[4][3] = WHITE_FLAG;	//������
			chess_board_status[2][4] = chess_board_status[3][5] = chess_board_status[4][2] = chess_board_status[5][3] = BLACK_AVAILABLE; //���ӿ���
			chess_board_status[2][3] = chess_board_status[4][5] = chess_board_status[5][4] = chess_board_status[3][2] = WHITE_AVAILABLE; //���ӿ���
			//�Ʒְ�
			left_piece_icon.setBounds(450, 265, 50, 50); add(left_piece_icon);
			right_piece_icon.setBounds(595, 265, 50, 50); add(right_piece_icon);
			black_piece_count_area.setBounds(505, 265, 80, 50); black_piece_count_area.setFont(new Font("����", Font.BOLD, 45)); add(black_piece_count_area);
			white_piece_count_area.setBounds(650, 265, 80, 50);white_piece_count_area.setFont(new Font("����", Font.BOLD, 45)); add(white_piece_count_area);
			black_piece_count_area.setText("2");
			white_piece_count_area.setText("2");
			
			//��ʱ��
			black_time_rest_area.setFont(new Font("����", Font.BOLD, 35)); black_time_rest_area.setBounds(450, 325, 130, 45); add(black_time_rest_area);
			white_time_rest_area.setFont(new Font("����", Font.BOLD, 35)); white_time_rest_area.setBounds(595, 325, 130, 45); add(white_time_rest_area);
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));

			setVisible(true);
		}
		
		
		
		
		//������Ϸ������
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

			// ���
			for (Table table : all_table) {
				add(table.left_seat); add(table.right_seat); add(table.spectator);
			}
		}
		
		//���õ�¼-ע���
		public void frameConfigLoginRegister(String title) {
			setTitle(title);
			setLayout(null);
			setBounds(450, 200, 320, 200);
			setVisible(true);
			//�ǳ�label��textfield
			JLabel label_id = new JLabel("�ǳ�");
			label_id.setBounds(5, 5, 60, 30); add(label_id);
			textfield_id.setBounds(65, 5, 150, 30); add(textfield_id);
			//����label��textfield
			JLabel label_password = new JLabel("����");
			label_password.setBounds(5, 35, 60, 30); add(label_password);
			textfield_password.setBounds(65, 35, 150, 30); add(textfield_password);
			//��¼��ע�ᰴť
			button_login.setBounds(5, 100, 100, 50); add(button_login);
			button_register.setBounds(110, 100, 100, 50); add(button_register);
		}
		
		//��������ʧ�ܿ�
		public void frameConfigConnectFailure() {
			setBounds(600, 200, 200, 200);
			setVisible(true);
			
			JPanel panel_connect_failure = new JPanel();
			panel_connect_failure.setLayout(new GridLayout(2,1));
			JLabel label_connect_failure = new JLabel("��������ʧ�ܣ���������������");
			panel_connect_failure.add(label_connect_failure);
			panel_connect_failure.add(button_connect_failure_confirm);
			
			getContentPane().add(panel_connect_failure);
		}
		
	}
	
	
	public Login() {
		//��ʼ����λ
		for(int i = 0; i < table_sum; i++)
			all_table.add(new Table());
		
		//ע�⣺һ���������Ӷ������������˷���Login�Ĺ��췽��ͳһ��ӣ�������Window�Ĺ��췽������ֻ֤��һ��������
		chat_input_field.addActionListener(this);
		for (Table table : all_table) {
			table.left_seat.addActionListener(this);
			table.right_seat.addActionListener(this);
			table.spectator.addActionListener(this);
		}
		button_login.addActionListener(Login.this); 
		button_register.addActionListener(Login.this); 
		button_connect_failure_confirm.addActionListener(Login.this);
		
		//��ʼ״̬ʱ��top_frame�ǵ�¼��
		top_frame = new Window(Status.LOGIN_REGISTER, "Reversi by go2sea");
		
		//top_frame = new Window(Status.GAME_WINDOWS, "Game Window");
	}
	
	

	public boolean connectServer() {
		try {
			//NIOͨ��
			socket_channel = SocketChannel.open();
			socket_channel.configureBlocking(false);
			SocketAddress socketAddress = new InetSocketAddress("localhost", 5555);
			socket_channel.connect(socketAddress);
			if(socket_channel.isConnectionPending())
				socket_channel.finishConnect();

			System.out.println("connected !!!");
		
			//����IO�̣߳�ClientRunner��
			client_runner = new ClientRunner();
			client_thread = new Thread(client_runner);
			client_thread.start();
			//ע�⣺���߳��������У������������write
			//������Ϣ�����߳�
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
	
	
	//����seat��ȡ��ţ���source����seat������-1
	public int getSeatNum(Object source) {
		for(int i = 0; i < all_table.size(); i++)
			if(all_table.get(i).left_seat == source)
				return i * 2;
			else if(all_table.get(i).right_seat == source)
				return i * 2 + 1;
		return -1;
	}
	
	//���ݹ�ս��ť���ر�ţ���source���ǹ�ս������-1
	public int getSpectatorNum(Object source) {
		for(int i = 0; i < all_table.size(); i++)
			if(all_table.get(i).spectator == source)
				return i;
		return -1;
	}
	
	//���ݱ�ŷ���seat
	public JButton getSeatByNum(int seat_num) {
		assert(seat_num >= 0 && seat_num < table_sum * 2);
		
		int table_id = seat_num / 2;
		if(seat_num % 2 == 0)
			return all_table.get(table_id).left_seat;
		return all_table.get(table_id).right_seat;
	}

	
	//�������ݶ���
	public void NIOSendMessage(Message message) throws IOException {
		
		System.out.println("client " + id + " sending " + message);
		
		byte[] message_bytes = SerializableUtil.toBytes(message);

		byte[] length_bytes = BytesIntTransfer.Int2Bytes(message_bytes.length);
		ByteBuffer length_buffer = ByteBuffer.wrap(length_bytes);
		ByteBuffer message_buffer = ByteBuffer.wrap(message_bytes);

		//message��������
		while(length_buffer.hasRemaining())
			socket_channel.write(length_buffer);
		//����message
		socket_channel.write(message_buffer);

	}


	@Override //���Խӿڣ�ActionListener
	public void actionPerformed(ActionEvent e) {
		//����ʧ��ȷ�ϰ�ť
		if (e.getSource() == button_connect_failure_confirm)
			System.exit(0);

		try {
			// ��¼��ť
			if (e.getSource() == button_login) {
				id = textfield_id.getText();
				password = textfield_password.getText();
				if(password.equals("") || id.equals(""))
					return;
				if(s == null && !connectServer())
					return;
				
				NIOSendMessage(Message.newMessage(id, Message.MessageType.LOGIN_TYPE, id + " " + password));
			}
			// ע�ᰴť
			else if (e.getSource() == button_register) {
				String string_id = textfield_id.getText();
				String string_password = textfield_password.getText();
				if(string_password.equals("") || string_id.equals(""))
					return;
				if(s == null && !connectServer())
					return;
				NIOSendMessage(Message.newMessage(id, Message.MessageType.REGISTER_TYPE, string_id + " " + string_password));
			}
			// �˸���λ
			else if (getSeatNum(e.getSource()) >= 0) {
				String id_on_seat = ((JButton) e.getSource()).getText();				
				//�������λ�ѱ�����ռ�ã�ʲô������
				if(!id_on_seat.equals("") && !id_on_seat.equals(id))
					return;
				//��λ�գ�����
				if(id_on_seat.equals("")) {
					if(seat != null)	//��������ע�⣺��ʼʱgetText����null��
						seat.setText("");
					seat = (JButton) e.getSource();
					NIOSendMessage(Message.newMessage(id, Message.MessageType.SEAT_TYPE, Integer.toString(getSeatNum(seat))));
				}
				//��λ�����Լ����뿪��λ
				else {
					System.out.println("��λ�����Լ����뿪��λ");
					assert(id_on_seat.equals(id));
					NIOSendMessage(Message.newMessage(id, Message.MessageType.SEAT_TYPE, Integer.toString(getSeatNum(seat))));	//������ʱ������Ϣһ�£�������������
				}
			}
			//�ĸ���ս��ť
			else if(getSpectatorNum(e.getSource()) >= 0) {
				int spectator_num = getSpectatorNum(e.getSource());
				NIOSendMessage(Message.newMessage(Message.MessageType.SPECTATOR_TYPE, Integer.toString(spectator_num)));
			}
			//�ı������
			else if(e.getSource() == chat_input_field) {
				String chat_message = chat_input_field.getText().trim();
				chat_input_field.setText("");
				NIOSendMessage(Message.newMessage(id, Message.MessageType.CHAT_MESSAGE_TYPE, chat_message));
				//ע�⣬����Ҫ��������chat_display_area����ʾ���ȴ�������������Ϣ���ٸ���
			}
			//�¾ֲ˵���
			else if(e.getSource() == new_game_item) {
				NIOSendMessage(Message.newMessage(id, Message.MessageType.GAME_READY, ""));
			}
			//���ť
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
	
	
	
	
	//��Ϣ�����̣߳��ټ���&�������client��Ϣ�ֽڶ��� ��������Ϣ�������client����Ϣ���в�����
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
		
		//������һ�������յ�message�ĳ���
		public void tryTosetNextMessageLength() {
			//��һ�����õĻ�δ���� || �µ�4�ֽ�lengthδ���ֱ�ӷ���
			if(next_message_length.get() > 0 || message_byte_q.size() < 4)
				return;
			//ȡ���󳤶�
			byte[] length_bytes = new byte[4];
			for(int i = 0; i < 4; i++)
				length_bytes[i] = message_byte_q.poll();
			int length = BytesIntTransfer.Bytes2Int(length_bytes);
			System.out.println("�¸����󳤶ȣ� " + length);
			next_message_length.set(length);
		}
		
		//��Ϣ�ֽ����� ����>��Ϣ���󣬷���client����Ϣ����
		public void tryToReceiveNextMessage() {
			//4�ֽڳ��ȱ�־ �� ��Ϣ��δ���ֱ�ӷ���
			if(next_message_length.get() < 0 
					|| message_byte_q.size() < next_message_length.get())
				return;
			
			//ȡ�������ֽ���&�����л�&�ַ�
			byte[] obj_bytes = new byte[next_message_length.get()];
			for(int i = 0; i < next_message_length.get(); i++)
				obj_bytes[i] = message_byte_q.poll();
			Message new_message = (Message) SerializableUtil.toObject(obj_bytes);
			message_to_handle.add(new_message);	//�����������Ϣ����
			System.out.println("new_message.type: " + new_message.getType());
			//ע�⣺�ָ�-1׼���¸���Ϣ�Ľ���
			next_message_length.set(-1);
		}
		
		//������Ϣ
		public void tryToHandleNextMessage() {
			if(message_to_handle.size() <= 0)
				return;
			
			/***
			 * ע�⣺Server����ר�ŵ�IO��ȡ�߳�NIOServer��message��ClientRunner����
			 * Client��������ͬ��ClientRunner��������NIO���룬main�߳���������һ����NIO���
			 * message�Ĵ�����MessageMonitor��ɣ���ΪClient�˵���Ϣѹ��С�����Լ����߳̿���
			 * ��������message��������ɽ��յ��ӳ٣�
			 ****/
			//MessageMonitor�߳�����handleMessage
			client_runner.handleMessage();
			
		}
	}
	
	
	
	
	
	
	
	/*
	 * �ڲ��ࣺ�ͻ����߳�
	 */
	class ClientRunner implements Runnable, Terminable {
		//�������ŵؽ����߳�
		private TerminationToken termination_token = new TerminationToken();
		private Exception ex_for_cleanup = null;	//�����쳣����doCleanup
		
		public ClientRunner() {
			System.out.println("new ClientRunner !!!");
			next_message_length = new AtomicInteger(-1);
		}
		
		
		
		
		//��Ϣ�����ͳһ���
		public void handleMessage() {
			while(message_to_handle.size() > 0) {
				Message message = message_to_handle.poll();
				System.out.println(message);
				
				Message.MessageType message_type = message.getType();
				
				if(message_type != Message.MessageType.ONE_SECOND)
					System.out.println("message.type: " + message.getType() + "\nmessage.buf: "+ message.getBuf());

				
				if(message_type == Message.MessageType.LOGIN_SUCCESS) {			//��¼�ɹ�
					top_frame.dispose(); // ��ǰ���ڹر�
					top_frame = new Window(Status.GAME_LOBBY, null);
				}
				else if(message_type == Message.MessageType.WRONG_ID_PASSWORD) {//�û����������
					top_frame.dispose(); // ��ǰ���ڹر�
					top_frame = new Window(Status.LOGIN_REGISTER, "�û������������");
				}
				else if(message_type == Message.MessageType.REGISTER_SUCCESS) {	//ע��ɹ�
					top_frame.dispose(); // ��ǰ���ڹر�
					top_frame = new Window(Status.LOGIN_REGISTER, "ע��ɹ������¼");
				}
				else if(message_type == Message.MessageType.ID_ALREADY_EXIST) {	//�û����ѱ�ע��
					top_frame.dispose(); // ��ǰ���ڹر�
					top_frame = new Window(Status.LOGIN_REGISTER, "���û����ѱ�ע��");
				}
				else if(message_type == Message.MessageType.SEATED_SUCCESS)		//�����ɹ�
					seat.setText(id);
				else if(message_type == Message.MessageType.SEAT_OFF_SUCCESS) {	//�����ɹ�
					seat.setText(""); seat = null;
				}
				else if(message_type == Message.MessageType.SEATED_FAIL)		//����ʧ��
					seat = null;
				else if(message_type == Message.MessageType.SEAT_STATUS_TYPE)	//��λ���
					handleSeatStatusMessage(message.getBuf());
				else if(message_type == Message.MessageType.CHAT_MESSAGE_TYPE)	//������Ϣ
					handleChatMessage(message.getBuf());
				else if(message_type == Message.MessageType.OPPONENT_SEATED)	//��������
					handleOpponentSeated(message.getBuf());
				else if(message_type == Message.MessageType.GAME_BEGIN_TYPE)	//��Ϸ��ʼ
					handleGameBeginType(message.getBuf());
				else if(message_type == Message.MessageType.ONE_SECOND)			//һ���ȥ
					handleOneSecond(message.getBuf());
				else if(message_type == Message.MessageType.TURN_TYPE)			//���巽��Ϣ
					handleTurnType(message.getBuf());
				else if(message_type == Message.MessageType.CHESS_BOARD_STATUS_TYPE)	//���̸����Ϣ
					handleChessBoardStatusType(message.getBuf());
				else if(message_type == Message.MessageType.WIN_COUNT_TYPE)		//ʤ��ͳ����Ϣ
					handleWinCountType(message.getBuf());
				else if(message_type == Message.MessageType.GAME_OVER_TYPE)		//һ�ֽ�����Ϣ
					handleGameOverType(message.getBuf());
			}

		}
		
		//����һ�ֽ�����Ϣ
		public void handleGameOverType(String lose_client_id) {
			turn = false;
		}
				
		//����ʤ��ͳ����Ϣ
		private void handleWinCountType(String buf) {
			String[] win_count_status = buf.split(" ");
			String black_win = win_count_status[0];
			String white_win = win_count_status[1];
			black_win_count_label.setText(black_win); black_win_count_label.repaint();
			white_win_count_label.setText(white_win); white_win_count_label.repaint();
		}
		
		//�������̸����Ϣ
		private void handleChessBoardStatusType(String buf) {
			String[] status = buf.split(" ");
			assert(status.length == 64);
			for(int index = 0; index < 64; index++)
				chess_board_status[index/8][index%8] = Integer.parseInt(status[index]);
			
			//����chess_board_status����ͼ��
			for(int index = 0; index < 64; index++) {
				if(chess_board_status[index/8][index%8] == BLACK_FLAG)
					all_piece_button.get(index).setIcon(black_piece_icon);
				else if(chess_board_status[index/8][index%8] == WHITE_FLAG)
					all_piece_button.get(index).setIcon(white_piece_icon);
				else
					all_piece_button.get(index).setIcon(null_piece_icon);
				all_piece_button.get(index).repaint();
			}
			
			//ˢ������ͳ��
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
		
		//�������巽��Ϣ
		private void handleTurnType(String turn_client_id) {
			if(turn_client_id.equals(id))
				turn = true;
			else
				turn = false;
		}
		
		
		//�����ʱ��Ϣ��һ���ȥ
		private void handleOneSecond(String buf) {
			if((turn && color.equals(BLACK)) || (!turn && color.equals(WHITE))) 
				black_time_rest--;
			else
				white_time_rest--;
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));
			black_time_rest_area.repaint();
			white_time_rest_area.repaint();
			
			//��ʱ�����и�
			if(turn && black_time_rest * white_time_rest == 0)
				try {
					NIOSendMessage(Message.newMessage(id, Message.MessageType.LOSE_GAME, ""));
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		
		
		
		//������Ϸ��ʼ��Ϣ
		private void handleGameBeginType(String buf) {
			//ע�⣺������Ϣ��ˢ����Server�˷�����chess_board_status_message�������������账��

			//ʣ��ʱ��ˢ��
			black_time_rest = white_time_rest = TIME_LIMIT;
			black_time_rest_area.setText(time2String(black_time_rest));
			white_time_rest_area.setText(time2String(white_time_rest));
			black_time_rest_area.repaint();
			white_time_rest_area.repaint();
			//������Ŀͳ��ˢ��
			black_piece_count_area.setText("2");
			white_piece_count_area.setText("2");
			black_piece_count_area.repaint();
			white_piece_count_area.repaint();
			
			String[] buf_list = buf.split(" ");
			if(id.equals(buf_list[0])) {	//����ִ��
				color = BLACK;
				turn = true;
			}
			else {							//����ִ��
				assert(id.equals(buf_list[1]));
				color = WHITE;
				turn = false;
			}
		}
		
		//����chat_message��Ϣ
		private void handleChatMessage(String buf) {
			chat_display_area.setText(chat_display_area.getText() + buf + '\n');
		}
		
		//�������������Ϣ
		private void handleOpponentSeated(String buf) {
			top_frame.dispose();
			top_frame = new Window(Status.GAME_WINDOWS, "Game Window");
		}

		//����seat_status��Ϣ
		private void handleSeatStatusMessage(String buf) {		
			String[] client_id_list = buf.split(" ");
			assert(client_id_list.length == table_sum * 2);
			
			for(int i = 0; i < client_id_list.length; i++) {
				if(client_id_list[i].equals("#")) {
					//�����˳����Լ�ֱ���˳�
					if(!getSeatByNum(i).getText().equals("") && getOpponentSeatNum(seat) == i) {
						client_runner.terminate(client_thread);
						System.out.println("�����˳��ˣ�����");
					}
					getSeatByNum(i).setText("");
				}
				else
					getSeatByNum(i).setText(client_id_list[i]);
			}
		}

		
		//��ȡ������λ�ı�ţ�������λ�ţ�
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
						
						//����byte����
						for(byte b : bytes)
							message_byte_q.add(b);
						System.out.println("���" + bytes.length + "��byte������");
						System.out.println("Ŀǰbyte���г��ȣ� " + message_byte_q.size());
						
						
						
						// ��¼��ע��ķ�����Ϣ�ļ��
						//Message message = (Message) ois.readObject();
						//��Ϣ����ͳһ���
						//handleMessage(message);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				//�ر�ois��������������read�׳�SocketException: Socket closed ������
				ex_for_cleanup = e;
			} finally {
				doCleanup(ex_for_cleanup);
			}
		}

		@Override	//���Խӿ�Terminable��client_therad�ǵ�ǰthis�������ڵ��̶߳���
		public void terminate(Thread client_thread) {			
			//����toShutdown��־
			termination_token.setToShutdown(true);	//����toShutdown
			doTerminate();
			if (termination_token.reservations.get() <= 0)
				if(!client_thread.isInterrupted())
					client_thread.interrupt();
		}

		@Override	//��ֹǰ׼������
		public void doTerminate() {
			try {
				/***
				 * ע�⣺NIO��SocketChannel��close�ᴥ��Զ�˵�һ��OP_READ�¼���read����ֵΪ-1��
				 * ��˲��÷��ͱ�־�˳�����Ϣ��ֻ��ر�SocketChannel����
				 ***/
				//֪ͨ������Ҫ�˳�
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
			//����client_threadҪ�رգ�������������ⲿ��Login�ĳ�Ա��������Щ���ֶ��ر�
			if(top_frame != null) {
				//ע�⣺����dispose���׳�InterruptedException ��֪Ϊ�Ρ�������
				//���߾ȹ����Ȳ��ɼ�������null����GC������
				top_frame.setVisible(false);
				top_frame = null;
			}
		}

	}
	
	/*********** end of ClientRunner ***********/


}
