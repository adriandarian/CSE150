package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */

 public static final int priorityMinimum = 1;
 public static final int priorityMaximum= Integer.MAX_VALUE;
public class LotteryScheduler extends PriorityScheduler {
  /**
   * Allocate a new lottery scheduler.
   */
  public LotteryScheduler() {
  }

  /**
   * Allocate a new lottery thread queue.
   *
   * @param transferPriority <tt>true</tt> if this queue should transfer tickets
   *                         from waiting threads to the owning thread.
   * @return a new lottery thread queue.
   */
  public ThreadQueue newThreadQueue(boolean transferPriority) {
      	return new PriorityQueue(transferPriority);
  }

  public boolean increasePriority(){
    boolean status = Machine.interrupt().disabled();
    KThread thread = KThread.currentThread();
    int priority = getPriority(thread);
    if(priority == priorityMaximum){
      return false;
    }else{
      setPriority(thread, priority++);
    }
    Machine.interrupt().restore(status);
    return true;
  }
  public boolean decreasePriority(){
    boolean status = Machine.interrupt().disabled();
    KThread thread = KThread.currentThread();
    int priority = getPriority(thread);
    if(priority == priorityMinimum){
      return false;
    }else{
      setPriority(thread, priority--);
    }
    Machine.interrupt().restore(status);
    return true;
  }

  public void setPriority(KThread thread, int priority){
    Lib.assertTrue(Machine.interrupt().disabled());
    if(decreasePriority() && increasePriority()){
      getThreadState(thread).setPriority(priority);
    }
  }
}
