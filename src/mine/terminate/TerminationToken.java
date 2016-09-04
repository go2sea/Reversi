package mine.terminate;


import java.util.concurrent.atomic.AtomicInteger;

public class TerminationToken {
	//toShutdown标志
	protected volatile boolean toShutdown = false;
	 //reservations剩余任务数
	public final AtomicInteger reservations = new AtomicInteger(0);
	
	public boolean isToShutdown() {
		return toShutdown;
	}
	
	public void setToShutdown(boolean toShutdown) {
		//this.toShutdown = true;
		this.toShutdown = toShutdown;
	}
	
	//可否立即break
	public boolean breakableNow() {
		return isToShutdown() && reservations.get() <=0;
	}
	
}
