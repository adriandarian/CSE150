package nachos.threads;

import nachos.machine.*;
import nachos.threads.*;
import java.util.*;

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


public class LotteryScheduler extends PriorityScheduler {
  /**
   * Allocate a new lottery scheduler.
   */
  public LotteryScheduler() {
  }

  /*Global bounds for priority*/
  public static final int priorityDefault = 1;
  public static final int priorityMinimum = 1;
  public static final int priorityMaximum= Integer.MAX_VALUE;

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

  public KThread nextThread(){
    boolean intStatus = Machine.interrupt().disabled();
      queueOwner = null;
    if(threads.isEmpty()){
      Machine.interrupt().restore(intStatus);
      return null;
    }
    
    queueOwner = pickNextThread().thread;

    if(queueOwner != null)
      acquire(queueOwner);

    Machine.interrupt().restore(intStatus);
    return queueOwner;
  }

  public ThreadState pickNextThread() {
    if(threads.isEmpty())
      return null;

    int roll, index;
    int transferAmt = 0;
    Random rand = new Random();
    roll = rand.nextInt(ticketSum() + 1);

    for(int i = priorityMinimum; i < threads.size(); i++){
      KThread thread = (KThread) threads.get(i);
      transferAmt += getThreadPriority(thread);
      if(transferAmt >= roll){
       ThreadState next = getThreadState((KThread) threads.get(i));
       break;
      }
    }
    return next;
}

  public int ticketSum(){
    /* amount of tickets to be transferred*/
    int transferAmt = 0;

    for(int i = 0; i < threads.size(); i++){
      KThread thread = (KThread) threads.get(i);
      transferAmt += getThreadPriority(thread);
      enforcePriorityBounds(transferAmt);
    }

    return transferAmt;
  }

  /*Ensures priority Min/Max aren't passed*/
  public void enforcePriorityBounds(int priority) {
    if(priority < priorityMinimum)
      priority = priorityMinimum;
    else if(priority > priorityMaximum)
      priority = priorityMaximum;
  }


  public int getThreadPriority(KThread thread) {
    return getThreadState(thread).getPriority();
  }

  public void acquire(KThread thread){
    threads.remove(thread);
    acqThreads.add(thread);
  }
  public boolean increasePriority() {
    boolean intStatus = Machine.interrupt().disabled();
    KThread thread = KThread.currentThread();
    int priority = getPriority(thread);
    if(priority == priorityMaximum){
      return false;
    }else{
      setPriority(thread, priority++);
    }
    Machine.interrupt().restore(intStatus);
    return true;
  }


  public boolean decreasePriority() {
    boolean intStatus = Machine.interrupt().disabled();
    KThread thread = KThread.currentThread();
    int priority = getPriority(thread);
    if(priority == priorityMinimum){
      return false;
    }else{
      setPriority(thread, priority--);
    }
    Machine.interrupt().restore(intStatus);
    return true;
  }

  public void setPriority(KThread thread, int priority){
    Lib.assertTrue(Machine.interrupt().disabled());
    if(decreasePriority() && increasePriority()){
      getThreadState(thread).setPriority(priority);
    }
  }
  protected KThread queueOwner;
  protected LinkedList<KThread> acqThreads = new LinkedList(); //"acquired" threads
  protected LinkedList<KThread> threads = new LinkedList();// all threads
}
