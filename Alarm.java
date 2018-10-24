package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
  /**
   * Allocate a new Alarm. Set the machine's timer interrupt handler to this
   * alarm's callback.
   *
   * <p>
   * <b>Note</b>: Nachos will not function correctly with more than one alarm.
   */
  public Alarm() {
    Machine.timer().setInterruptHandler(new Runnable() {
      public void run() {
        timerInterrupt();
      }
    });
  }

  /**
   * The timer interrupt handler. This is called by the machine's timer
   * periodically (approximately every 500 clock ticks). Causes the current thread
   * to yield, forcing a context switch if there is another thread that should be
   * run.
   */
  public void timerInterrupt() {
    //KThread.currentThread().yield();
   
    
    boolean inStatus = KThread.Interrupt().disable();
    
    KThread temp = new KThread();
    		
    while ()
    	
    	boolean intStatus = Machine.interrupt().disable();
    
    threadTime temp = sleepingThreads.peek();

    while(temp != null && temp.time < Machine.timer().getTime()) {
        System.out.println("waking up thread");
        temp.thread.ready();
        sleepingThreads.poll();
        temp = sleepingThreads.peek();
    }
    
    Machine.interrupt().restore(intStatus);

    KThread.currentThread().yield();
}
    	
  

  /**
   * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
   * the timer interrupt handler. The thread must be woken up (placed in the
   * scheduler ready set) during the first timer interrupt where
   *
   * <p>
   * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
   *
   * @param x the minimum number of clock ticks to wait.
   *
   * @see nachos.machine.Timer#getTime()
   */
  public void waitUntil(long x) {
    // for now, cheat just to get something working (busy waiting is bad)
    long wakeTime = Machine.timer().getTime() + x;
    while (wakeTime > Machine.timer().getTime())
      KThread.yield();
    
    
    
    boolean intStatus = Machine.interrupt().disable();
    
    System.out.println("Sleeping thread at : " + x);

    threadTime temp = new threadTime();
    temp.time = Machine.timer().getTime() + x;
    temp.thread = KThread.currentThread();

    sleepingThreads.add(temp);
    
    temp.thread.sleep();

    Machine.interrupt().restore(intStatus);
}



private class threadTime implements Comparable<threadTime> {
    KThread thread;
    long time;
    
    public sleepingThreads(KThread thread, long wakeTime) {
    	this.thread=thread;
    	this.time=time;
    }

    public int compareTo(threadTime) {
        if(this.time 
    }
}

private PriorityQueue<threadTime> sleepingThreads = new PriorityQueue<threadTime>();

}
  
