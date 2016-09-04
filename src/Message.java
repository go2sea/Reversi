
/*
 * socket���ݵ���Ϣ����
 */

import java.io.Serializable;

public class Message implements Serializable{
	public static enum MessageType {
		/**** ������Ϣ���ͣ���������ݣ�****/
		
		//��¼��Ϣ��ע����Ϣ�û������룬������Ϣ
		LOGIN_TYPE, REGISTER_TYPE, CHAT_MESSAGE_TYPE, 
		//����λ��Ϣ���ͻ��˷�������λ״̬��Ϣ������������
		SEAT_TYPE, SEAT_STATUS_TYPE,
		//������Ϣ
		REVERSI_TYPE,
		//��Ϸ��ʼ
		GAME_BEGIN_TYPE,
		//����״̬��Ϣ
		CHESS_BOARD_STATUS_TYPE,
		//���巽��Ϣ
		TURN_TYPE,
		//ʤ��ͳ����Ϣ
		WIN_COUNT_TYPE,
		//һ�ֽ�����Ϣ�����ʧ�ܷ�id
		GAME_OVER_TYPE,
		//��ս��Ϣ
		SPECTATOR_TYPE,
		
		
		
		
		
		/**** ״̬��Ϣ���ͣ�����û�����ݣ�****/
		//�û����������ID�Ѵ��ڣ���¼��֤�ɹ���ע��ɹ�
		WRONG_ID_PASSWORD, ID_ALREADY_EXIST, LOGIN_SUCCESS, REGISTER_SUCCESS, 
		//�����ɹ�������ʧ�ܣ������ɹ�
		SEATED_SUCCESS, SEATED_FAIL,SEAT_OFF_SUCCESS,
		//client�˳���Ϣ
		CLIENT_QUIT,
		//��Ϸ��ʼ��Ϣ
		GAME_READY,
		//��������
		OPPONENT_SEATED,
		//һ���ȥ
		ONE_SECOND,
		//�������ˡ�
		LOSE_GAME,
		
		
	}
	
	public String type;
	public String buf;
	public String client_id;	//��־�����ĸ�Client
	
	private Message(String id, MessageType mt, String buf) {
		this.client_id = id;
		this.type = mt.name();
		this.buf = buf;
	}
	
	//��ȡһ�� ����Ϊmt������Ϊbuf����Ϣ����
	static Message newMessage(String id, MessageType mt, String buf) {
		System.out.println("in message. id: " + id);
		return new Message(id, mt, buf);
	}
	
	static Message newMessage(MessageType mt, String buf) {
		//-1�����server��client����Ϣ����client���͵Ĳ�������ָ��seat_num����Ϣ������LOGIN_TYPE��������ָ�����ĸ�seat_num
		return new Message(null, mt, buf);
	}
	
	//��ȡ����ö�ٶ���
	public MessageType getType() {
		return MessageType.valueOf(type);
	}
	
	//��ȡ��Ϣ����
	public String getBuf() {
		return buf;
	}
	
	public String toString() {
		return "type: " + type + "\nbuf: " + buf;
	}
	
	
	
	
	
	
}
