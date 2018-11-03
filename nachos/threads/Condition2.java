package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
  /**
   * Allocate a new condition variable.
   *
   * @param conditionLock the lock associated with this condition variable. The
   *                      current thread must hold this lock whenever it uses
   *                      <tt>sleep()</tt>, <tt>wake()</tt>, or
   *                      <tt>wakeAll()</tt>.
   */
  public Condition2(Lock conditionLock) {
    this.conditionLock = conditionLock;
  }

  /**
   * Atomically release the associated lock and go to sleep on this condition
   * variable until another thread wakes it using <tt>wake()</tt>. The current
   * thread must hold the associated lock. The thread will automatically reacquire
   * the lock before <tt>sleep()</tt> returns.
   */
  public void sleep() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    
    conditionLock.release();
    boolean intstatus = Machine.interrupt().disable();
    KThread thread = KThread.currentThread(); 
    waitQueue.waitForAccess(thread); //put thread in waiting queue (in Lock.java)
    thread.sleep();
    Machine.interrupt().restore(intstatus);
    conditionLock.acquire();
  }

  /**
   * Wake up at most one thread sleeping on this condition variable. The current
   * thread must hold the associated lock.
   */
  public void wake() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    
    conditionLock.release();
    
    	if(!waitQueue.isEmpty()) {
    		boolean intstatus = Machine.interrupt().disable();
    		KThread thread = waitQueue.removeFirst();
   
    	if(thread !=null) {
    		thread.ready();
    	}
    	
    }
    //conditionLock.acquire();
    //condition.signal(); 
    Machine.interrupt().restore(intstatus);
  }

  /**
   * Wake up all threads sleeping on this condition variable. The current thread
   * must hold the associated lock.
   */
  public void wakeAll() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    
    //boolean intstatus = Machine.interrupt().disable();
    
    while(!waitQueue.isEmpty()) {
    	wake();
    }
    /*
     * conditionLock.acquire();
     * conditionLock.release();
     * condition.signalAll(); // notifies all 
     * Machine.interrupt().restore(intstatus);
     */
  }

  private Lock conditionLock;
}
