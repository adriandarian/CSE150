package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
  /**
   * Allocate a new communicator.
   */
  
  private int messages;
  private boolean if_message_in_use;
  
  private int wait_listeners;

  private Lock the_lock;
  private Condition2 speakers_Condition, listeners_Condition;
  
  
  public Communicator() {
    messages =0;
    if_message_in_use = false;
    wait_listeners =0;
    the_lock = new Lock();
    listeners_Condition = new Condition2(the_lock);
    speakers_Condition = new Condition2(the_lock);
  }

  /**
   * Wait for a thread to listen through this communicator, and then transfer
   * <i>word</i> to the listener.
   *
   * <p>
   * Does not return until this thread is paired up with a listening thread.
   * Exactly one listener should receive <i>word</i>.
   *
   * @param word the integer to transfer.
   */
  public void speak(int word) {
    the_lock.acquire();
    speakers_Condition++;
    
    while (wait_listeners == 0){
      speakers_Condition.sleep();
    
    if (if_message_in_use){
      speakers_Condition.sleep();}
    }
    
 
    if_message_in_use = true;
    messages = word;
    
    speakers_Condition--;
    listeners_Condition.wake();
    
    the_lock.release();
   
  }
    
    
  
  /**
   * Wait for a thread to speak through this communicator, and then return the
   * <i>word</i> that thread passed to <tt>speak()</tt>.
   *
   * @return the integer transferred.
   */
  public int listen() {
    int thee_word;
    the_lock.acquire();
    wait_listeners++;
    
    
    while((!message_in_use)||(wait_listeners > 0)){
     
        speakers_Condition.wake();
   
        listeners_Condition.sleep();
    }
    
    thee_word = messages;
    if_message_in_use = false;
    wait_listeners--;
   
    speakers_Condition.wake();
    the_lock.release();
    
    return thee_word;
  }
}
