package mine.terminate;

/*
 * ���ŵؽ����߳�
 */
public interface Terminable {
	public void terminate(Thread client_thread);
	public void doTerminate();		//�߳�ֹͣʱ��������ر�socket����������쳣
	public void doCleanup(Exception cause);
}
