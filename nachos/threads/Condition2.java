package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

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

    Machine.interrupt().disable();
    num++;
    Queue.waitForAccess(KThread.currentThread());
    KThread.sleep();

    conditionLock.acquire();
  }

  /**
   * Wake up at most one thread sleeping on this condition variable. The current
   * thread must hold the associated lock.
   */
  public void wake() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean Status = Machine.interrupt().disable();
    KThread thread = Queue.nextThread();

    if(thread != null) {
        num--;
        thread.ready();
    }

    Machine.interrupt().restore(Status);
  }

  /**
   * Wake up all threads sleeping on this condition variable. The current thread
   * must hold the associated lock.
   */
  public void wakeAll() {
    Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean Status = Machine.interrupt().disable();
    KThread thread;

    while((thread = Queue.nextThread()) != null) {
        thread.ready();
    }

    num = 0;
    Machine.interrupt().restore(Status);
  }

  public void acquireCondition2(KThread thread) {
    if(ThreadedKernel.scheduler instanceof PriorityScheduler)
        Queue.acquire(thread);
  }

  public  int getNum() {
    return num;
  }

  private Lock conditionLock;
  private ThreadQueue Queue = ThreadedKernel.scheduler.newThreadQueue(true);
  private int num;
}
