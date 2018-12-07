package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
  /**
   * Allocate a new user kernel.
   */
  public UserKernel() {
    super();
  }

  /**
   * Initialize this kernel. Creates a synchronized console and sets the
   * processor's exception handler.
   */
  public void initialize(String[] args) {
    super.initialize(args);

    console = new SynchConsole(Machine.console());

    Machine.processor().setExceptionHandler(new Runnable() {
      public void run() {
        exceptionHandler();
      }
    });

    int numberOfPages = Machine.processor().getNumPhysPages();
    for (int n = 0; n < numberOfPages; n++) {
      UserTable.add(n);
    }
  }

  /**
   * Test the console device.
   */
  public void selfTest() {
    super.selfTest();

    System.out.println("Testing the console device. Typed characters");
    System.out.println("will be echoed until q is typed.");

    char c;

    do {
      c = (char) console.readByte(true);
      console.writeByte(c);
    } while (c != 'q');

    System.out.println("");
  }

  /**
   * Returns the current process.
   *
   * @return the current process, or <tt>null</tt> if no process is current.
   */
  public static UserProcess currentProcess() {
    if (!(KThread.currentThread() instanceof UThread))
      return null;

    return ((UThread) KThread.currentThread()).process;
  }

  /**
   * The exception handler. This handler is called by the processor whenever a
   * user instruction causes a processor exception.
   *
   * <p>
   * When the exception handler is invoked, interrupts are enabled, and the
   * processor's cause register contains an integer identifying the cause of the
   * exception (see the <tt>exceptionZZZ</tt> constants in the <tt>Processor</tt>
   * class). If the exception involves a bad virtual address (e.g. page fault, TLB
   * miss, read-only, bus error, or address error), the processor's BadVAddr
   * register identifies the virtual address that caused the exception.
   */
  public void exceptionHandler() {
    Lib.assertTrue(KThread.currentThread() instanceof UThread);

    UserProcess process = ((UThread) KThread.currentThread()).process;
    int cause = Machine.processor().readRegister(Processor.regCause);
    process.handleException(cause);
  }

  /**
   * Start running user programs, by creating a process and running a shell
   * program in it. The name of the shell program it must run is returned by
   * <tt>Machine.getShellProgramName()</tt>.
   *
   * @see nachos.machine.Machine#getShellProgramName
   */
  public void run() {
    super.run();

    UserProcess process = UserProcess.newUserProcess();

    String shellProgram = Machine.getShellProgramName();
    Lib.assertTrue(process.execute(shellProgram, new String[] {}));

    KThread.currentThread().finish();
  }

  /**
   * Terminate this kernel. Never returns.
   */
  public void terminate() {
    super.terminate();
  }

  public static int getPage() {
    int page = -1;
    Machine.interrupt().disable();
    if (UserTable.isEmpty() == false) {
      page = UserTable.removeFirst();
    }
    Machine.interrupt().enable();
    return page;
  }

  public static void addPage(int page) {
    Lib.assertTrue(page >= 0 && page < Machine.processor().getNumPhysPages());
    Machine.interrupt().disable();
    UserTable.add(page);
    Machine.interrupt().enable();
  }

  public static int getPID() {
    int val;
    Machine.interrupt().disable();
    val = ++userPid;
    Machine.interrupt().enabled();
    return userPid;
  }

  public static UserProcess getProcess(int process) {
    return userProcMap.get(process);
  }

  public static UserProcess regProcess(int pid, UserProcess process) {
    UserProcess iProcess;
    Machine.interrupt().disable();
    iProcess = userProcMap.put(pid, process);
    Machine.interrupt().enabled();
    return iProcess;
  }

  public static UserProcess unregProcess(int process) {
    UserProcess dProcess;
    Machine.interrupt().disable();
    dProcess = userProcMap.remove(process);
    Machine.interrupt().enabled();
    return dProcess;
  }

  /** Globally accessible reference to the synchronized console. */
  public static SynchConsole console;

  // dummy variables to make javac smarter
  private static Coff dummy1 = null;
  private static LinkedList<Integer> UserTable = new LinkedList<Integer>();
  private static int userPid = 0;
  private static HashMap<Integer, UserProcess> userProcMap = new HashMap<Integer, UserProcess>();
}
