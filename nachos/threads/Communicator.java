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
  private int toSubscribe;
  private boolean toBeSubscribed;
  private Condition2 publisher;
  private Condition2 subscriber;
  private Condition2 subscriberShake;
  private Lock lock;
  /**
   * Allocate a new communicator.
   */
  public Communicator() {
    this.toBeSubscribed = false;
    this.lock = new Lock();
    this.publisher = new Condition2(lock);
    this.subscriber = new Condition2(lock);
    this.subscriberShake = new Condition2(lock);
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
    while (toBeSubscribed) {
      publisher.sleep();
    }

    this.toBeSubscribed = true;
    this.toSubscribe = word;
    publisher.wake();
    subscriberShake.sleep();

    lock.release();
  }

  /**
   * Wait for a thread to speak through this communicator, and then return the
   * <i>word</i> that thread passed to <tt>speak()</tt>.
   *
   * @return the integer transferred.
   */
  public int listen() {
    int transferred;
    lock.acquire();
    while (!toBeSubscribed) {
      subscriber.sleep();
    }

    transferred = this.toSubscribe;
    this.toBeSubscribed = false;

    publisher.wake();
    subscriberShake.wake();

    lock.release();
    return transferred;
  }
}
