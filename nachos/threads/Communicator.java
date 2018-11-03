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
  private boolean message_in_use = false;
  
  private int speaker = 0;
  private int wait_listeners = 0;

  private Lock lock;
  private Condition speakers_Condition, listeners_Condition;
  
  
  public Communicator() {
    lock = new Lock();
    listener = new Condition(lock);
    speaker = new Condition(lock);
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
    lock.acquire();
    speaker++;
    
    while(wait_listeners == 0){
      speakers_Condition.sleep();
    
    if (message_in_use){
      speakers_Condition.sleep();}
    }
    
 
    message_in_use = true;
    messages = word;
    
    speaker--;
    listeners_Condition.wake();
    
    lock.release();
   
  }
    
    
  }

  /**
   * Wait for a thread to speak through this communicator, and then return the
   * <i>word</i> that thread passed to <tt>speak()</tt>.
   *
   * @return the integer transferred.
   */
  public int listen() {
    int thee_word;
    lock.acquire();
    wait_listeners++;
    
    
    while((!message_in_use)||(wait_listeners > 0){
      if(speaker>0){
        speakers_Condition.wake();
      }
        listeners_Condition.sleep();
    }
    
    thee_word = messages;
    message_in_use = false;
    wait_listeners--;
   
    speakers_Condition.wake();
    lock.release();
    
          
    return thee_word;
  }
}
