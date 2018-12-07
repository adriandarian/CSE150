package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i, i, false, false, false, false);

        UserKernel.processIDMutex.P();
        this.pID = UserKernel.processID++;
        UserKernel.processIDMutex.V();

        // Init array of open files, max of 15
        fileDescriptor = new OpenFile[16];

        fileDescriptor[0] = UserKernel.console.openForReading();
        fileDescriptor[1] = UserKernel.console.openForWriting();

        statusLock = new Lock();
        joinCond = new Condition(statusLock);
        exitStatus = null;
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to load
     * the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch. Called by
     * <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        // Nothing to see here..
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for the
     * null terminator, and convert it to a <tt>java.lang.String</tt>, without
     * including the null terminator. If no null terminator is found, returns
     * <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated string.
     * @param maxLength the maximum number of characters in the string, not
     *                  including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to the
     *               array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        // Virtual
        int currVpn = vpn;
        int pageOffset = vpnOffset;
        // Physical
        int currPpn = entry.ppn;
        int currAddr = realAddr;

        int written = 0;
        int bufOffset = offset;
        int leftToWrite = length;

        if (data == null)
            return 0;

        if (!(offset >= 0 && length >= 0 && offset + length <= data.length))
            return 0;

        byte[] memory = Machine.processor().getMemory();

        if (vaddr < 0 || vaddr >= memory.length || !entry.valid) {
            entry.used = false;
            return 0;
        }

        while (written < length) {
            if (pageOffset + leftToWrite > pageSize) {
                int amountToWrite = pageSize - pageOffset;
                System.arraycopy(memory, currAddr, data, bufOffset, amountToWrite);
                written += amountToWrite;
                bufOffset += amountToWrite;
                leftToWrite = length - written;
                if (++currVpn >= pageTable.length)
                    break;
                else {
                    pageTable[currVpn - 1].used = false;
                    TranslationEntry currEntry = pageTable[currVpn];
                    if (!currEntry.valid)
                        break;
                    pageOffset = 0;
                    currEntry.used = true;
                    currAddr = currPpn * pageSize;
                    currPpn = currEntry.ppn;
                }
            } else {
                System.arraycopy(memory, currAddr, data, bufOffset, leftToWrite);
                written += leftToWrite;
                bufOffset += leftToWrite;
            }

        }
        pageTable[currVpn].used = false;
        return written;
    }

    /**
     * Transfer all data from the specified array to this process's virtual memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to virtual
     *               memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        // Similar to readVirtualMemory
        // Virtual
        int currVpn = vpn;
        int pageOffset = vpnOffset;
        // Physical
        int currPpn = entry.ppn;
        int currAddr = realAddr;

        int written = 0;
        int bufOffset = offset;
        int leftToWrite = length;

        if (data == null)
            return 0;

        if (!(offset >= 0 && length >= 0 && offset + length <= data.length))
            return 0;

        byte[] memory = Machine.processor().getMemory();

        int vpn = Processor.pageFromAddress(vaddr);
        int vpnOffset = Processor.offsetFromAddress(vaddr);
        TranslationEntry entry = pageTable[vpn];

        // Make sure the section is not read only
        if (entry.readOnly)
            return 0;

        entry.used = true;
        int realAddr = entry.ppn * pageSize + vpnOffset;

        // for now, just assume that virtual addresses equal physical addresses
        if (realAddr < 0 || realAddr >= memory.length || !entry.valid) {
            entry.used = false;
            return 0;
        }

        while (written < length) {
            if (pageOffset + leftToWrite > pageSize) {
                int amountToWrite = pageSize - pageOffset;
                System.arraycopy(data, bufOffset, memory, currAddr, amountToWrite);
                written += amountToWrite;
                bufOffset += amountToWrite;
                leftToWrite = length - written;
                if (++currVpn >= pageTable.length)
                    break;
                else {
                    pageTable[currVpn - 1].used = false;
                    TranslationEntry currEntry = pageTable[currVpn];

                    // Make sure the next page is also not read only
                    if (!currEntry.valid || currEntry.readOnly)
                        break;

                    currEntry.used = true;
                    pageOffset = 0;
                    currPpn = currEntry.ppn;
                    currAddr = currPpn * pageSize;
                }
            } else {
                System.arraycopy(data, bufOffset, memory, currAddr, leftToWrite);
                written += leftToWrite; // written should now equal length
                bufOffset += leftToWrite;
            }

        }
        pageTable[currVpn].used = false;
        return written;
    }

    /**
     * Load the executable with the specified name into this process, and prepare to
     * pass it the specified arguments. Opens the executable, reads its header
     * information, and copies sections and arguments into this process's virtual
     * memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
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

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
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
        }
        incProcessCount();

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into memory.
     * If this returns successfully, the process will definitely be run (this is the
     * last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess,
                    "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;

                TranslationEntry entry = pageTable[vpn];

                UserKernel.physPageMutex.P();
                Integer thePage = null;
                try {
                    thePage = UserKernel.physicalPages.removeFirst();

                } catch (NoSuchElementException e) {
                    // Not enough physical memory left, so give the pages back
                    // to the processor and return false
                    unloadSections();
                    return false;
                }
                UserKernel.physPageMutex.V();

                entry.ppn = thePage;
                entry.valid = true;
                entry.readOnly = section.isReadOnly();
                section.loadPage(i, entry.ppn);
            }
        }

        for (int i = numPages - 9; i < numPages; i++) {
            TranslationEntry entry = pageTable[i];
            UserKernel.physPageMutex.P();
            Integer pageNumber = null;

            try {
                pageNumber = UserKernel.physicalPages.removeFirst();
            }
            // Catch when not enough memory, so free it:
            catch (NoSuchElementException e) {
                unloadSections();
                return false;
            }

            UserKernel.physPageMutex.V();
            entry.ppn = pageNumber;
            entry.valid = true;
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < pageTable.length; i++) {
            TranslationEntry entry = pageTable[i];
            if (entry.valid) {
                UserKernel.physPageMutex.P();
                UserKernel.physicalPages.add(entry.ppn);
                UserKernel.physPageMutex.V();
            }
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the program
     * loaded into this process. Set the PC register to point at the start function,
     * set the stack pointer register to point at the top of the stack, set the A0
     * and A1 registers to argc and argv, respectively, and initialize all other
     * registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
            syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     * 
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
        case syscallHalt:
            return handleHalt();
        case syscallJoin:
            return handleJoin();
        case syscallCreate:
            return handleCreate();
        case syscallOpen:
            return handleOpen();
        case syscallRead:
            return handleRead();
        case syscallWrite:
            return handleWrite();
        case syscallClose:
            return handleClose();
        case syscallUnlink:
            return handleUnlink();

        default:
            Lib.debug(dbgProcess, "Unknown syscall " + syscall);
            Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
     * The <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
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
            Lib.assertNotReached("Unexpected exception");
        }
    }

    // Returns the index of the slot in fileDescritpors
    // where the file has been opened.
    // Returns -1 on failure for fault handling
    private int handleCreate(int fileInt) {
        String fName = readVirtualMemoryString(fileInt, 256);
        if (fName == null) {
            Lib.debug(dbgProcess, "\thandleCreate Failed: specified file does not exist in Virtual Memory");
            return -1;
        }

        OpenFile file = ThreadedKernel.fileSystem.open(fName, true);
        if (file == null) {
            Lib.debug(dbgProcess, "\thandleCreate Failed: file couldn't be opened from File System");
            return -1;
        }

        for (int i = 2; i < fileDescriptor.length; i++) {
            if (fileDescriptor[i] == null) {
                // We found an open slot
                fileDescriptor[i] = file;
                return i;
            }
        }
        Lib.debug(dbgProcess, "\thandleCreate Failed: file descriptors full!");
        return -1;
    }

    private int handleOpen(int fileInt) {
        String fName = readVirtualMemoryString(fileInt, 256);
        if (fName == null) {
            Lib.debug(dbgProcess, "\thandleOpen Failed: specified file does not exist in Virtual Memory");
            return -1;
        }
        // Difference here: set false so don't create a file if not found.
        OpenFile file = ThreadedKernel.fileSystem.open(fName, false);

        if (file == null) {
            Lib.debug(dbgProcess, "\thandleOpen Failed: Could not open file");
            return -1;
        }

        for (int i = 2; i < fileDescriptor.length; i++) {
            if (fileDescriptor[i] == null) {
                // We found an open slot
                fileDescriptor[i] = file;
                return i;
            }
        }
        Lib.debug(dbgProcess, "\thandleOpen Failed: file descriptors full!");
        return -1;
    }

    private int handleClose(int fileInt) {
        if (file < 0 || file > 15) {
            Lib.debug(dbgProcess, "\thandleClose Failed: invalid fileInt: " + fileInt);
            return -1;
        }

        OpenFile file = fileDescriptor[fileInt];

        if (file == null) {
            Lib.debug(dbgProcess, "\thandleClose Failed: file does not exist " + fileInt);
            return -1;
        }

        file.close();
        fileDescriptor[fileInt] = null;
        return 0;
    }

    private int handleRead(int fileInt, int buffer, int count) {
        if (fileInt < 2 || file > 15) {
            Lib.debug(dbgProcess, "\thandleRead Failed: invalid fileInt: " + fileInt);
            return -1;
        }

        OpenFile file = fileDescriptor[fileint];

        if (file == null) {
            Lib.debug(dbgProcess, "\thandleRead Failed: file does not exist " + fileInt);
            return -1;
        }

        byte[] buff = new byte[pageSize];
        int leftToRead = count;
        int totalRead = 0;
        int readByte = 0;

        while (leftToRead > pageSize) {
            readByte = theFile.read(buff, 0, pageSize);
            if (readByte == -1) {
                Lib.debug(dbgProcess, "\thandleRead Failed: Could not read file");
                return -1;
            } else if (readByte == 0) {
                return totalRead;
            }

            int byteRead = writeVirtualMemory(buffer, buff, 0, readByte);

            if (readByte != byteRead) {
                Lib.debug(dbgProcess, "\thandleRead Failed: incorrect match " + readByte + " =/= " + byteRead);
                return -1;
            }
            buffer += byteRead;
            totalRead += byteRead;
            leftToRead -= byteRead;
        }

        readByte = theFile.read(buff, 0, leftToRead);
        if (readByte == -1) {
            Lib.debug(dbgProcess, "\thandleRead: Failed to read file");
            return -1;
        }

        int byteRead = writeVirtualMemory(buffer, buff, 0, readByte);
        if (readByte != byteRead) {
            Lib.debug(dbgProcess, "\thandleRead: Read and write amounts did not match");
            return -1;
        }
        totalRead += byteRead;
        return totalRead;
    }

    private int handleWrite(int fileInt, int buffer, int count) {
        if (fileInt == 0) {
            Lib.debug(dbgProcess, "\thandleWrite Failed: stdin is at " + fileInt);
            return -1;
        }
        if (file < 1 || file > 15) {
            Lib.debug(dbgProcess, "\thandleWrite Failed: " + fileInt + " out of bounds");
            return -1;
        }

        OpenFile file = fileDescriptor[fileInt];

        if (file == null) {
            Lib.debug(dbgProcess, "\thandleWrite Failed: file does not exist");
            return -1;
        }

        byte[] buff = new byte[pageSize];
        int leftToWrite = count;
        int totalWrote = 0;
        int wroteByte = 0;
        while (leftToWrite > pageSize) {
            wroteByte = readVirtualMemory(buffer, buff);

            int byteWrote = theFile.write(buff, 0, wroteByte);

            if (wroteByte != byteWrote) {
                Lib.debug(dbgProcess, "\thandleWrite Failed: incorrect match " + wroteByte + " =/= " + byteWrote);
            }

            if (byteWrote == -1) {
                Lib.debug(dbgProcess, "\thandleWrite Failed: file not written");
                return -1;
            }

            if (byteWrote == 0) {
                return totalWrote;
            }

            buffer += byteWrote;
            totalWrote += byteWrote;
            leftToWrite -= byteWrote;
        }

        wroteByte = readVirtualMemory(buffer, buff, 0, leftToWrite);

        int byteWrote = theFile.write(buff, 0, wroteByte);

        if (wroteByte != byteWrote) {
            Lib.debug(dbgProcess, "\tIn handleWrite Failed: ");
        }

        if (byteWrote == -1) {
            Lib.debug(dbgProcess, "\thandleWrite Failed: incorrect match " + byteWrote);
            return -1;
        }

        totalWrote += byteWrote;

        return totalWrote;
    }

    private int handleUnlink(int fileInt) {
        String fName = readVirtualMemoryString(fileInt, 256);
        if (fName == null) {
            Lib.debug(dbgProcess, "\thandleUnlink: Could not read filename from Virtual Memory");
            return -1;
        }

        int indexInTable = isInFDTable(fName);
        if (indexInTable != -1) {
            handleClose(indexInTable);
        }

        if (ThreadedKernel.fileSystem.remove(fName))
            return 0;

        return -1;
    }

    private int isInFDTable(String filename) {
        for (int i = 0; i < fileDescriptor.length; i++) {
            OpenFile currFile = fileDescriptor[i];
            if (currFile != null && filename == currFile.getName())
                return i;
        }
        return -1;
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    protected int initialPC, initialSP;
    protected int argc, argv;

    protected static final int pageSize = Processor.pageSize;
    protected static final char dbgProcess = 'a';
    protected Lock statusLock;
    protected Condition joinCond;
    protected Semaphore parentMutex = new Semaphore(1);

    protected OpenFile[] fileDescriptor;

    protected int pID;
    protected UserProcess parent;
    protected Integer exitStatus;
    protected Hashtable<Integer, UserProcess> children = new Hashtable<Integer, UserProcess>();

}