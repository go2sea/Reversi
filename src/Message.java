
/*
 * socket传递的消息对象
 */

import java.io.Serializable;

public class Message implements Serializable{
	public static enum MessageType {
		/**** 内容消息类型（后面跟内容）****/
		
		//登录消息，注册消息用户名密码，聊天消息
		LOGIN_TYPE, REGISTER_TYPE, CHAT_MESSAGE_TYPE, 
		//入座位消息（客户端发），座位状态消息（服务器发）
		SEAT_TYPE, SEAT_STATUS_TYPE,
		//行棋消息
		REVERSI_TYPE,
		//游戏开始
		GAME_BEGIN_TYPE,
		//棋盘状态消息
		CHESS_BOARD_STATUS_TYPE,
		//行棋方消息
		TURN_TYPE,
		//胜负统计消息
		WIN_COUNT_TYPE,
		//一局结束消息，后跟失败方id
		GAME_OVER_TYPE,
		//观战消息
		SPECTATOR_TYPE,
		
		
		
		
		
		/**** 状态消息类型（后面没有内容）****/
		//用户名密码错误，ID已存在，登录验证成功，注册成功
		WRONG_ID_PASSWORD, ID_ALREADY_EXIST, LOGIN_SUCCESS, REGISTER_SUCCESS, 
		//入座成功，入座失败，离座成功
		SEATED_SUCCESS, SEATED_FAIL,SEAT_OFF_SUCCESS,
		//client退出消息
		CLIENT_QUIT,
		//游戏开始消息
		GAME_READY,
		//对手入座
		OPPONENT_SEATED,
		//一秒过去
		ONE_SECOND,
		//“我输了”
		LOSE_GAME,
		
		
	}
	
	public String type;
	public String buf;
	public String client_id;	//标志来自哪个Client
	
	private Message(String id, MessageType mt, String buf) {
		this.client_id = id;
		this.type = mt.name();
		this.buf = buf;
	}
	
	//获取一个 类型为mt，内容为buf的消息对象
	static Message newMessage(String id, MessageType mt, String buf) {
		System.out.println("in message. id: " + id);
		return new Message(id, mt, buf);
	}
	
	static Message newMessage(MessageType mt, String buf) {
		//-1代表从server到client的消息（或client发送的部分无需指定seat_num的消息，比如LOGIN_TYPE），无需指定是哪个seat_num
		return new Message(null, mt, buf);
	}
	
	//获取类型枚举对象
	public MessageType getType() {
		return MessageType.valueOf(type);
	}
	
	//获取消息内容
	public String getBuf() {
		return buf;
	}
	
	public String toString() {
		return "type: " + type + "\nbuf: " + buf;
	}
	
	
	
	
	
	
}
