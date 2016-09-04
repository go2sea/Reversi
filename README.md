# Reversi

###这是一个Java实现的C/S结构的网络黑白棋

	关键词：多线程、NIO、Reactor模式
	
	####基本功能：
		1、游戏大厅，注册玩家可以在游戏大厅选择对手、进入对战室。
		2、对战双方的聊天功能。聊天对游客可见。
		3、游客观战。观战中，游客可以自由发言，并对所有人可见。


	####结构：
		采用Reactor模式，NIO+MessageMonitor处理大部分消息：
        Client端有三个线程：主线程&ClientRunner线程&MessageMonitor线程。主线程负责actionPerform，ClientRunner线程负责收消息字节流（注意：并不处理），MessageMonitor负责监视&拆包生成消息并处理。
		Server端有多个线程：NIOReader线程&MessageMonitor线程&Timer线程（注意：ClientRunner不再是一个线程）。NIOReader线程负责接收所有NIO字节流，包括Client端的连接和消息接收（只处理其中的Client连接&Client关闭触发的OP_READ），MessageMonitor线程负责监视&拆包&生成消息&处理消息。Timer线程负责计时等，ClientRunner作为Monitor处理消息的工具。
		
	####关于消息传递：
		消息统一封装为message对象，有三个域，type、buf、client_id。type是一个枚举类型，标志消息的类别，buf是消息内容，client_id是客户端编号。
		消息发送过程：对message对象序列化得到一个byte数组，获取数组长度len，将len序列化得到另一个byte数组，将这两个byte数组封装成ByteBuffer，先传len的buffer，再传message的buffer（解决粘包问题）。
		
	####关于结束线程：
		client_1的主线程调用ClientRunner的terminate（）：设置toShutDown标志，调用doTerminate（）关闭相应SocketChannel，从此与Server再无瓜葛（注意不发送client_quit）；调用interrupt（），由中断或toShutDown标志使ClientRunner终止。
        Server端处理client_1的SocketChannel的close（）触发的OP_READ事件（返回值为-1）：注意，不直接调用key的cancel（），而是调用响应ClientRunner的terminate（）（注意：直接调用。整个Server的工作中，只有ClientRunner的关闭是由NIOReader来执行的，其他时刻NIOReader只负责接收消息）：设置toShutDown标志，调用doTerminate（）关闭相应SocketChannel，并从all_client中移除client_1，用seatStatusMessage告知其他响应要关闭线程如client_2。
        client_2的ClientRunner线程收到seatStatusMessage，发现对手退出，自己直接调用terminate（），执行相同操作：设置toShutDown标志，调用doTerminate（）关闭相应SocketChannel，从此与Server再无瓜葛（注意不发送client_quit）；调用interrupt（），由中断或toShutDown标志使ClientRunner终止。
         Server端处理client_2的SocketChannel的close（）触发的OP_READ事件（返回值为-1），执行相同操作：不直接调用key的cancel（），而是调用响应ClientRunner的terminate（）（注意：直接调用。整个Server的工作中，只有ClientRunner的关闭是由NIOReader来执行的，其他时刻NIOReader只负责接收消息）：设置toShutDown标志，调用doTerminate（）关闭相应SocketChannel，并从all_client中移除client_2，用seatStatusMessage告知其他响应要关闭线程，注意，此时没有其他要关闭的线程，整个关闭过程结束。
	