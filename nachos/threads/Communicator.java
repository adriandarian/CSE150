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
  public Communicator() {
    Trans = false;
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
    communicator.acquire();

    while(Trans || subscriber.getThreadCount() == 0) {
      publisher.sleep();
    }

    Trans = true;
    message = word;
    subscriber.wake();

    communicator.release();
  }

  /**
   * Wait for a thread to speak through this communicator, and then return the
   * <i>word</i> that thread passed to <tt>speak()</tt>.
   *
   * @return the integer transferred.
   */
  public int listen() {
    int result;
    communicator.acquire();

    while (!inTransaction) {
      if (speakerQueue.getThreadCount() > 0)
        speakerQueue.wake();
      listenerQueue.sleep();
    }

    result = message;
    Trans = false;
    if (subscriber.getThreadCount() > 0 && publisher.getThreadCount() > 0)
      publisher.wake();
    
    communicator.release();
    return result;
  }

  private Lock communicator = new Lock();
  private Condition2 publisher = new Condition2(communicator);
  private COndition2 subscriber = new Condition2(communicator);
  private int message;
  private boolean Trans;
}
