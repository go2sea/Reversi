package mine.terminate;

/*
 * 优雅地结束线程
 */
public interface Terminable {
	public void terminate(Thread client_thread);
	public void doTerminate();		//线程停止时操作，如关闭socket并处理相关异常
	public void doCleanup(Exception cause);
}
