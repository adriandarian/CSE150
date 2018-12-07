package nachos.threads;

import nachos.machine.*;

public class ThreadedKernel extends Kernel {

  public ThreadedKernel() {
    super();
  }

  public void initialize(String[] args) {
    String schedulerName = Config.getString("ThreadedKernel.scheduler");
    scheduler = (Scheduler) Lib.constructObject(schedulerName);
    String fileSystemName = Config.getString("ThreadedKernel.fileSystem");
    if (fileSystemName != null)
      fileSystem = (FileSystem) Lib.constructObject(fileSystemName);
    else if (Machine.stubFileSystem() != null)
      fileSystem = Machine.stubFileSystem();
    else
      fileSystem = null;
    new KThread(null);
    alarm = new Alarm();
  }

  /**
   * Test this kernel. Test the <tt>KThread</tt>, <tt>Semaphore</tt>,
   * <tt>SynchList</tt>, and <tt>ElevatorBank</tt> classes. Note that the
   * autograder never calls this method, so it is safe to put additional tests
   * here.
   */
  public void selfTest() {
  }

  public void run() {
  }

  public void terminate() {
    Machine.halt();
  }

  public static Scheduler scheduler = null;
  public static Alarm alarm = null;
  public static FileSystem fileSystem = null;
  private static RoundRobinScheduler dummy1 = null;
  private static PriorityScheduler dummy2 = null;
  private static LotteryScheduler dummy3 = null;
  private static Condition2 dummy4 = null;
  private static Communicator dummy5 = null;
  private static Rider dummy6 = null;
  private static ElevatorController dummy7 = null;
}