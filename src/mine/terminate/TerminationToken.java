package mine.terminate;


import java.util.concurrent.atomic.AtomicInteger;

public class TerminationToken {
	//toShutdown��־
	protected volatile boolean toShutdown = false;
	 //reservationsʣ��������
	public final AtomicInteger reservations = new AtomicInteger(0);
	
	public boolean isToShutdown() {
		return toShutdown;
	}
	
	public void setToShutdown(boolean toShutdown) {
		//this.toShutdown = true;
		this.toShutdown = toShutdown;
	}
	
	//�ɷ�����break
	public boolean breakableNow() {
		return isToShutdown() && reservations.get() <=0;
	}
	
}
