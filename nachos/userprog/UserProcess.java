package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;
import java.util.Iterator;
import java.io.EOFException;

public class UserProcess {
  public UserProcess() {
    for (int i = 0; i < MAXFD; i++) {
      fds[i] = new FileDescriptor(); 
    } 
    fds[STDIN].file = UserKernel.console.openForReading(); 
    fds[STDIN].position = 0;
    Lib.assertTrue(fds[STDIN] != null); 
    OpenFile retval = UserKernel.fileSystem.open("out", false); 
    int fileHandle = findEmptyFileDescriptor();
    fds[fileHandle].file = retval; 
    fds[fileHandle].position = 0; 
    pid = UserKernel.getNextPid(); 
    UserKernel.registerProcess(pid, this);
  } 

  public static UserProcess newUserProcess() {
    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
  }

  public boolean execute(String name, String[] args) {
    if (!load(name, args)) {
      return false;
    }
    thread = new UThread(this); 
    thread.setName(name).fork(); 
    return true;
  }

  public void saveState() {
  }

  public void restoreState() {
    Machine.processor().setPageTable(pageTable);
  }

  public String readVirtualMemoryString(int vaddr, int maxLength) {
    Lib.assertTrue(maxLength >= 0);
    byte[] bytes = new byte[maxLength + 1];
    int bytesRead = readVirtualMemory(vaddr, bytes);
    for (int length = 0; length < bytesRead; length++) {
      if (bytes[length] == 0) {
        return new String(bytes, 0, length);
      }
    }
    return null;
  }

  public int readVirtualMemory(int vaddr, byte[] data) {
    return readVirtualMemory(vaddr, data, 0, data.length);
  }

  public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
    Processor processor = Machine.processor(); 
    byte[] memory = processor.getMemory();
    int vpn = processor.pageFromAddress(vaddr);
    int addressOffset = processor.offsetFromAddress(vaddr); 
    TranslationEntry entry = null; 
    entry = pageTable[vpn]; 
    entry.used = true;
    int ppn = entry.ppn;
    int paddr = (ppn * pageSize) + addressOffset; 
    if (ppn < 0 || ppn >= processor.getNumPhysPages()) { 
      Lib.debug(dbgProcess, "\t\t UserProcess.readVirtualMemory(): bad ppn " + ppn);
      return 0; 
    } 
    int amount = Math.min(length, memory.length - paddr);
    System.arraycopy(memory, paddr, data, offset, amount);
    return amount;
  }

  public int writeVirtualMemory(int vaddr, byte[] data) {
    return writeVirtualMemory(vaddr, data, 0, data.length);
  }

  public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
    Processor processor = Machine.processor(); 
    byte[] memory = Machine.processor().getMemory();
    int vpn = processor.pageFromAddress(vaddr); 
    int addressOffset = processor.offsetFromAddress(vaddr);
    TranslationEntry entry = null;
    entry = pageTable[vpn];
    entry.used = true; 
    entry.dirty = true; 
    int ppn = entry.ppn; 
    int paddr = (ppn * pageSize) + addressOffset; 
    if (entry.readOnly) { 
      Lib.debug(dbgProcess, "\t\t [UserProcess.writeVirtualMemory]: write read-only page " + ppn); 
      return 0; 
    } 
    if (ppn < 0 || ppn >= processor.getNumPhysPages()) { 
      Lib.debug(dbgProcess, "\t\t [UserProcess.writeVirtualMemory]: bad ppn " + ppn); 
      return 0; 
    } 
    int amount = Math.min(length, memory.length - vaddr);
    Lib.debug(dbgProcess, "[UserProcess.writeVirtualMemory]: arrary copy amount: " + amount);
    System.arraycopy(data, offset, memory, vaddr, amount);
    return amount;
  }

  private boolean load(String name, String[] args) {
    Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
    OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
    if (executable == null) {
      Lib.debug(dbgProcess, "\t[UserProcess.load] failed to open " + name);
      return false;
    }
    try {
      coff = new Coff(executable);
    } catch (EOFException e) {
      executable.close();
      Lib.debug(dbgProcess, "\tcoff load failed");
      return false;
    }
    numPages = 0;
    for (int s = 0; s < coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);
      if (section.getFirstVPN() != numPages) {
        coff.close();
        Lib.debug(dbgProcess, "\tfragmented executable");
        return false;
      }
      numPages += section.getLength();
    }
    byte[][] argv = new byte[args.length][];
    int argsSize = 0;
    for (int i = 0; i < args.length; i++) {
      argv[i] = args[i].getBytes();
      argsSize += 4 + argv[i].length + 1;
    }
    if (argsSize > pageSize) {
      coff.close();
      Lib.debug(dbgProcess, "\targuments too long");
      return false;
    }
    initialPC = coff.getEntryPoint();
    numPages += stackPages;
    initialSP = numPages * pageSize;
    numPages++;
    pageTable = new TranslationEntry[numPages]; 
    for (int i = 0; i < numPages; i++) { 
      int ppn = UserKernel.getFreePage(); 
      pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false); 
    }
    if (!loadSections()) {
      return false;
    }
    int entryOffset = (numPages - 1) * pageSize;
    int stringOffset = entryOffset + args.length * 4;
    this.argc = args.length;
    this.argv = entryOffset;
    for (int i = 0; i < argv.length; i++) {
      byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
      Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
      entryOffset += 4;
      Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
      stringOffset += argv[i].length;
      Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
      stringOffset += 1;
      Lib.debug(dbgProcess, "[UserProcess.load] args[" + i + "]: " + args[i]); 
    }
    return true;
  }

  protected boolean loadSections() {
    if (numPages > Machine.processor().getNumPhysPages()) {
      coff.close();
      Lib.debug(dbgProcess, "\tinsufficient physical memory");
      return false;
    }
    for (int s = 0; s < coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);
      Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
      for (int i = 0; i < section.getLength(); i++) {
        int vpn = section.getFirstVPN() + i;
        TranslationEntry entry = pageTable[vpn]; 
        entry.readOnly = section.isReadOnly(); 
        int ppn = entry.ppn; 
        section.loadPage(i, ppn); 
      }
    }
    return true;
  }

  protected void unloadSections() { 
    for (int i = 0; i < numPages; i++) { 
      UserKernel.addFreePage(pageTable[i].ppn); 
      pageTable[i].valid = false; 
    } 
  } 

  public void initRegisters() {
    Processor processor = Machine.processor();
    for (int i = 0; i < processor.numUserRegisters; i++) {
      processor.writeRegister(i, 0);
    }
    processor.writeRegister(Processor.regPC, initialPC);
    processor.writeRegister(Processor.regSP, initialSP);
    processor.writeRegister(Processor.regA0, argc);
    processor.writeRegister(Processor.regA1, argv);
  }

  private int handleHalt() {
    Machine.halt();
    Lib.assertNotReached("Machine.halt() did not halt machine!");
    return 0;
  }

  private int handleCreate(int a0) {
    Lib.debug(dbgProcess, "handleCreate()"); 
    String filename = readVirtualMemoryString(a0, MAXSTRLEN);
    Lib.debug(dbgProcess, "filename: " + filename);
    OpenFile retval = UserKernel.fileSystem.open(filename, true); 
    if (retval == null) { 
      return -1; 
    }
    else {
      int fileHandle = findEmptyFileDescriptor();
      if (fileHandle < 0) 
        return -1; 
      else { 
        fds[fileHandle].filename = filename; 
        fds[fileHandle].file = retval; 
        fds[fileHandle].position = 0; 
        return fileHandle; 
      } 
    } 
  } 

  private int handleOpen(int a0) {
    Lib.debug(dbgProcess, "[UserProcess.handleOpen] Start"); 
    Lib.debug(dbgProcess, "[UserProcess.handleOpen] a0: " + a0 + "\n"); 
    String filename = readVirtualMemoryString(a0, MAXSTRLEN); 
    Lib.debug(dbgProcess, "filename: " + filename); 
    OpenFile retval = UserKernel.fileSystem.open(filename, false); 
    if (retval == null) { 
      return -1; 
    } 
    else { 
      int fileHandle = findEmptyFileDescriptor();
      if (fileHandle < 0) 
        return -1; 
      else { 
        fds[fileHandle].filename = filename; 
        fds[fileHandle].file = retval; 
        fds[fileHandle].position = 0; 
        return fileHandle; 
      }
    } 
  } 

  private int handleRead(int a0, int a1, int a2) {
    Lib.debug(dbgProcess, "handleRead()"); 
    int handle = a0; 
    int vaddr = a1; 
    int bufsize = a2; 
    Lib.debug(dbgProcess, "handle: " + handle); 
    Lib.debug(dbgProcess, "buf address: " + vaddr); 
    Lib.debug(dbgProcess, "buf size: " + bufsize); 
    if (handle < 0 || handle > MAXFD || fds[handle].file == null) 
      return -1; 
    FileDescriptor fd = fds[handle]; 
    byte[] buf = new byte[bufsize]; 
    int retval = fd.file.read(fd.position, buf, 0, bufsize); 
    if (retval < 0) { 
      return -1; 
    } 
    else { 
      int number = writeVirtualMemory(vaddr, buf); 
      fd.position = fd.position + number; 
      return retval; 
    }
  } 

  private int handleWrite(int a0, int a1, int a2) {
    Lib.debug(dbgProcess, "handleWrite()"); 
    int handle = a0;
    int vaddr = a1; 
    int bufsize = a2; 
    Lib.debug(dbgProcess, "handle: " + handle); 
    Lib.debug(dbgProcess, "buf address: " + vaddr); 
    Lib.debug(dbgProcess, "buf size: " + bufsize); 
    if (handle < 0 || handle > MAXFD || fds[handle].file == null) 
      return -1; 
    FileDescriptor fd = fds[handle]; 
    byte[] buf = new byte[bufsize]; 
    int bytesRead = readVirtualMemory(vaddr, buf); 
    int retval = fd.file.write(fd.position, buf, 0, bytesRead); 
    if (retval < 0) { 
      return -1; 
    } 
    else { 
      fd.position = fd.position + retval; 
      return retval; 
    } 
  }

  private int handleClose(int a0) {
    Lib.debug(dbgProcess, "handleClose()"); 
    int handle = a0; 
    if (a0 < 0 || a0 >= MAXFD) 
      return -1; 
    boolean retval = true;
    FileDescriptor fd = fds[handle]; 
    fd.position = 0;
    fd.file.close();
    if (fd.toRemove) { 
      retval = UserKernel.fileSystem.remove(fd.filename); 
      fd.toRemove = false; 
    } 
    fd.filename = ""; 
    return retval ? 0 : -1; 
  } 

  private int handleUnlink(int a0) {
    Lib.debug(dbgProcess, "handleUnlink()");
    boolean retval = true;
    String filename = readVirtualMemoryString(a0, MAXSTRLEN); 
    Lib.debug(dbgProcess, "filename: " + filename); 
    int fileHandle = findFileDescriptorByName(filename); 
    if (fileHandle < 0) { 
      retval = UserKernel.fileSystem.remove(fds[fileHandle].filename); 
    }
    else { 
      fds[fileHandle].toRemove = true; 
    } 
    return retval ? 0 : -1; 
  }

  private void handleExit(int exitStatus) { 
    Lib.debug(dbgProcess, "handleExit()");
    for (int i = 0; i < MAXFD; i++) {
      if (fds[i].file != null) 
        handleClose(i);
    } 
    while (children != null && !children.isEmpty()) { 
      int childPid = children.removeFirst(); 
      UserProcess childProcess = UserKernel.getProcessByID(childPid);
      childProcess.ppid = ROOT; 
    } 
    this.exitStatus = exitStatus; 
    Lib.debug(dbgProcess, "exitStatus: " + exitStatus); 
    this.unloadSections();
    if (this.pid == ROOT) {
      Lib.debug(dbgProcess, "I am the root process"); 
      Kernel.kernel.terminate(); 
    } 
    else { 
      Lib.assertTrue(KThread.currentThread() == this.thread); 
      KThread.currentThread().finish(); 
    } 
    Lib.assertNotReached(); 
  } 

  private int handleExec(int file, int argc, int argv) { 
    Lib.debug(dbgProcess, "handleExec()"); 
    if (argc < 1) { 
      Lib.debug(dbgProcess, "[UserProcess::handleExec] Error: argc < 1"); 
      return -1; 
    } 
    String filename = readVirtualMemoryString(file, MAXSTRLEN); 
    if (filename == null) { 
      Lib.debug(dbgProcess, "[UserProcess::handleExec] Error: filename == null"); 
      return -1; 
    } 
    String suffix = filename.substring(filename.length() - 4, filename.length()); 
    if (suffix.equals(".coff")) { 
      Lib.debug(dbgProcess, "handleExec(): filename doesn't have the " + coff + " extension");
      return -1; 
    } 
    String args[] = new String[argc]; 
    byte temp[] = new byte[4]; 
    for (int i = 0; i < argc; i++) { 
      int cntBytes = readVirtualMemory(argv + i * 4, temp); 
      if (cntBytes != 4) { 
        return -1; 
      } 
      int argAddress = Lib.bytesToInt(temp, 0); 
      args[i] = readVirtualMemoryString(argAddress, MAXSTRLEN); 
    } 
    UserProcess childProcess = UserProcess.newUserProcess(); 
    childProcess.ppid = this.pid; 
    this.children.add(childProcess.pid); 
    Lib.debug(dbgProcess, "[UserProcess.handleExec] process " + this.pid + " add a child with pid=" + childProcess.pid); 
    boolean retval = childProcess.execute(filename, args); 
    if (retval) { 
      return childProcess.pid; 
    } 
    else { 
      return -1; 
    } 
  } 

  private int handleJoin(int childpid, int adrStatus) {
    Lib.debug(dbgProcess, "handleJoin()"); 
    boolean childFlag = false; 
    int tmp = 0;
    Iterator<Integer> it = this.children.iterator(); 
    while (it.hasNext()) { 
      tmp = it.next(); 
      if (tmp == childpid) { 
        it.remove(); 
        childFlag = true; 
        break; 
      } 
    } 
    if (childFlag == false) { 
      Lib.debug(dbgProcess, 
          "[UserProcess.handleJoin] " 
              + "Error: process " + this.pid 
              + " doesn't have a child with pid=" + childpid); 
      return -1; 
    } 
    UserProcess childProcess = UserKernel.getProcessByID(childpid); 
    if (childProcess == null) {
      Lib.debug(dbgProcess,
          "[UserProcess.handleJoin] " 
              + "Error: the child " + childpid 
              + " has already joined by the time of the call"); 
      return -2; 
    } 
    childProcess.thread.join(); 
    UserKernel.unregisterProcess(childpid); 
    byte temp[] = new byte[4]; 
    temp = Lib.bytesFromInt(childProcess.exitStatus); 
    int cntBytes = writeVirtualMemory(adrStatus, temp);
    if (cntBytes != 4) 
      return 1; 
    else 
      return 0; 
  } 

  private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
      syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

  public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    switch (syscall) {
    case syscallHalt:
      return handleHalt();
    case syscallCreate:
      return handleCreate(a0);
    case syscallOpen:
      return handleOpen(a0);
    case syscallRead:
      return handleRead(a0, a1, a2);
    case syscallWrite:
      return handleWrite(a0, a1, a2);
    case syscallClose:
      return handleClose(a0);
    case syscallUnlink:
      return handleUnlink(a0); 
    case syscallExit: 
      handleExit(a0); 
      Lib.assertNotReached(); 
      return 0; 
    case syscallExec: 
      return handleExec(a0, a1, a2); 
    case syscallJoin:
      return handleJoin(a0, a1); 
    default:
      Lib.debug(dbgProcess, "Unknown syscall " + syscall);
      Lib.assertNotReached("Unknown system call!");
    }
    return 0;
  }

  public void handleException(int cause) {
    Processor processor = Machine.processor();
    switch (cause) {
    case Processor.exceptionSyscall:
      int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
          processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
          processor.readRegister(Processor.regA3));
      processor.writeRegister(Processor.regV0, result);
      processor.advancePC();
      break;
    default:
      Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
      handleExit(-1); 
      Lib.assertNotReached("Unexpected exception");
    }
  }

  private int findEmptyFileDescriptor() { 
    for (int i = 0; i < MAXFD; i++) { 
      if (fds[i].file == null) 
        return i; 
    } 
    return -1; 
  } 

  private int findFileDescriptorByName(String filename) { 
    for (int i = 0; i < MAXFD; i++) { 
      if (fds[i].filename == filename) 
        return i; 
    }
    return -1; 
  }

  protected Coff coff;
  protected TranslationEntry[] pageTable;
  protected int numPages;
  protected final int stackPages = 8;
  private int initialPC, initialSP;
  private int argc, argv;
  private static final int pageSize = Processor.pageSize;
  private static final char dbgProcess = 'a';

  public class FileDescriptor { 
    public FileDescriptor() {
    } 
    private String filename = ""; 
    private OpenFile file = null; 
    private int position = 0; 
    private boolean toRemove = false;
  } 

  public static final int MAXFD = 16; 
  public static final int STDIN = 0; 
  public static final int STDOUT = 1; 
  public static final int MAXSTRLEN = 256; 
  public static final int ROOT = 1;
  private FileDescriptor fds[] = new FileDescriptor[MAXFD];
  private int cntOpenedFiles = 0; 
  private int pid;
  private int ppid; 
  private LinkedList<Integer> children = new LinkedList<Integer>(); 
  private int exitStatus; 
  private UThread thread;
}
