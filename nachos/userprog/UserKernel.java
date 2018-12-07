package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;

public class UserKernel extends ThreadedKernel {
  public UserKernel() {
    super();
  }

  public void initialize(String[] args) {
    super.initialize(args);
    console = new SynchConsole(Machine.console());
    Machine.processor().setExceptionHandler(new Runnable() {
      public void run() {
        exceptionHandler();
      }
    });
    int numPhysPages = Machine.processor().getNumPhysPages();
    for (int i = 0; i < numPhysPages; i++) {
      pageTable.add(i);
    }
  }

  public void selfTest() {
    super.selfTest();
  }

  public static UserProcess currentProcess() {
    if (!(KThread.currentThread() instanceof UThread)) {
      return null;
    }
    return ((UThread) KThread.currentThread()).process;
  }

  public void exceptionHandler() {
    Lib.assertTrue(KThread.currentThread() instanceof UThread);
    UserProcess process = ((UThread) KThread.currentThread()).process;
    int cause = Machine.processor().readRegister(Processor.regCause);
    process.handleException(cause);
  }

  public void run() {
    super.run();
    UserProcess process = UserProcess.newUserProcess();
    String shellProgram = Machine.getShellProgramName();
    Lib.debug('a', "Shell program: " + shellProgram);
    Lib.assertTrue(process.execute(shellProgram, new String[] { shellProgram, "0" }));
    KThread.currentThread().finish();
  }

  public void terminate() {
    super.terminate();
  }

  public static int getFreePage() {
    int pageNumber = -1;
    Machine.interrupt().disable();
    if (pageTable.isEmpty() == false) {
      pageNumber = pageTable.removeFirst();
    }
    Machine.interrupt().enable();
    return pageNumber;
  }

  public static void addFreePage(int pageNumber) {
    Lib.assertTrue(pageNumber >= 0 && pageNumber < Machine.processor().getNumPhysPages());
    Machine.interrupt().disable();
    pageTable.add(pageNumber);
    Machine.interrupt().enable();
  }

  public static int getNextPid() {
    int retval;
    Machine.interrupt().disable();
    retval = ++nextPid;
    Machine.interrupt().enabled();
    return nextPid;
  }

  public static UserProcess getProcessByID(int pid) {
    return processMap.get(pid);
  }

  public static UserProcess registerProcess(int pid, UserProcess process) {
    UserProcess insertedProcess;
    Machine.interrupt().disable();
    insertedProcess = processMap.put(pid, process);
    Machine.interrupt().enabled();
    return insertedProcess;
  }

  public static UserProcess unregisterProcess(int pid) {
    UserProcess deletedProcess;
    Machine.interrupt().disable();
    deletedProcess = processMap.remove(pid);
    Machine.interrupt().enabled();
    return deletedProcess;
  }

  public static SynchConsole console;
  private static Coff dummy1 = null;
  private static LinkedList<Integer> pageTable = new LinkedList<Integer>();
  private static int nextPid = 0;
  private static HashMap<Integer, UserProcess> processMap = new HashMap<Integer, UserProcess>();

}
