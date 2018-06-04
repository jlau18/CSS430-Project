public class FileSystem {
    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;

    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;


    public FileSystem (int totalBlocks)
    {
        superblock = new SuperBlock(totalBlocks);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);

        // read the "/" file from disk
        FileTableEntry dirEntry = open("/", "r");
        int directorySize = fsize( dirEntry);
        if (directorySize > 0)
        {
            // There is already data in the directory
            // We must read, and copy it to fsDirectory
            byte[] directoryData = new byte[directorySize];
            read(dirEntry, directoryData);
            directory.bytes2directory(directoryData);
        }
        close(dirEntry);
    }


    public int format( int files){
        superblock.reformat(files);
        directory = new Directory(files);
        filetable = new FileTable(directory);
        return 0;
    }


    public FileTableEntry open(String fileName, String mode){
        FileTableEntry fileTableEntry = filetable.falloc(fileName, mode);
        if (mode.equals("w")) // writing mode
        {
            if ( deallocateBlocks( fileTableEntry ) == false)
                return null;
        }
        return fileTableEntry;
    }

	//our close right now is passing in an int, need to change here or in kernel
    public int close(FileTableEntry fd){
        if(fd == null) return -1;

        synchronized(fd) {
            if(fd.count > 0) fd.count--;

            if (fd.count == 0) {
                fd.inode.toDisk(fd.iNumber);
                if(filetable.ffree(fd)){
                    return 0;
                }
                else return -1;
            }
            return 0;
        }
    }


    public int read(FileTableEntry fd, byte[] buffer){
        int bytesRead= 0;
        int readLength = 0;
        while (true) {
            switch(fd.inode.flag) {
                case Inode.USED:
                    return -1;
                case Inode.WRITE:
                default:
                    fd.inode.flag = Inode.READ;
                    byte[] tempBlock = new byte[Disk.blockSize];
                    int buffersize = 0;
                    int tempBuffer = 0;

                    while (bytesRead < buffer.length) {
                        int blockNum = fd.inode.findTargetBlock(
                                fd.seekPtr/ Disk.blockSize);

                        if (blockNum == -1) {
                            return -1;
                        }
                        if (SysLib.rawread(blockNum, tempBlock) == -1) {
                            return -1;
                        }

                        boolean lastBlock = ((
                                buffer.length - tempBuffer) < Disk.blockSize);

                        if(lastBlock){
                            readLength = (buffer.length - tempBuffer);
                        }
                        else{
                            readLength = Disk.blockSize;
                        }

                        if (readLength < 512){
                            System.arraycopy(tempBlock, fd.seekPtr,
                                    buffer, tempBuffer,readLength);
                            bytesRead = buffer.length;
                        }
                        else{  // data in multiple blocks
                            System.arraycopy(tempBlock, fd.seekPtr,
                                    buffer, tempBuffer, readLength);
                            bytesRead += readLength;

                        }
                        buffersize = buffersize + readLength - 1;
                        tempBuffer += readLength;
                    }

                    if (fd.count > 0) {
                        fd.count--;
                    }

                    if (fd.count > 0) {
                        notify();
                    }
                    return bytesRead;
            }
        }
    }


    public int write(FileTableEntry fd, byte[] buffer){
        if (fd == null || fd.mode == "r")
        {
            return -1;
        }
        short blockNum = (short)fd.inode.findTargetBlock(fd.seekPtr/ Disk.blockSize);
        int bytesWritten = 0;
        int blockOffset = fd.seekPtr % Disk.blockSize;
        while (true) {
            switch(fd.inode.flag) {
                case Inode.WRITE:
                case Inode.READ:
                    if (fd.count > 1) {
                        try { wait(); }
                        catch (InterruptedException e){}
                    } else {
                        fd.inode.flag = Inode.UNUSED;
                    }
                    break;
                case Inode.USED:
                    return -1;
                default:
                    fd.inode.flag = Inode.WRITE;
                    byte[] tempBlock = new byte[Disk.blockSize];
                    short inodeOffset;
                    while (bytesWritten < buffer.length) {
                        inodeOffset = (short)(fd.seekPtr/ Disk.blockSize);
                        if (inodeOffset >= Inode.numDirectPointers() - 1 &&
                                fd.inode.getIndexBlockNumber() <= 0) {
                            short indexBlock = (short)superblock.nextFreeBlock();
                            if (indexBlock == -1) {
                                return -1;
                            }
                            // set indirect block and save to disk
                            fd.inode.setIndexBlock(indexBlock);
                            fd.inode.toDisk(fd.iNumber);
                        }
                        int bytesLeft = buffer.length - bytesWritten;

                        // block not available yet
                        if (blockNum == -1 || (bytesWritten % Disk.blockSize >
                                0 && bytesLeft > 0)) {

                            blockNum = (short)superblock.nextFreeBlock();

                            if (blockNum == -1) { // no space
                                return -1;
                            }
                            //sets new block so it knows where rest of file goes
                            if (!fd.inode.setNextBlockNumber(blockNum)){
                                return -1;
                            }

                            fd.inode.toDisk(fd.iNumber);
                        }

                        SysLib.rawread(blockNum, tempBlock);

                         int bytesToWrite;
                         if(bytesLeft < (Disk.blockSize - blockOffset)){
                            bytesToWrite = bytesLeft;
                         }
                         else{
                            bytesToWrite = Disk.blockSize - blockOffset;
                        }

                        System.arraycopy(buffer, bytesWritten, tempBlock,
                                blockOffset, bytesToWrite);
                        SysLib.rawwrite(blockNum, tempBlock);

                        blockNum++;
                        bytesWritten += bytesToWrite;
                        fd.seekPtr += bytesToWrite;
                        blockOffset = 0;
                    }
                    fd.count--;

                    if (fd.seekPtr >= fd.inode.length) { // file has grown
                        fd.inode.length += (fd.seekPtr - fd.inode.length);
                        fd.inode.toDisk(fd.iNumber);
                    }
                    if (fd.count > 0) {
                        notifyAll();
                    }
                    return bytesWritten;
            }
        }
    }

    public int seek(FileTableEntry fd, int offset, int whence){

        synchronized (fd)
        {
            switch(whence)
            {
                case SEEK_SET:
                    fd.seekPtr = offset;
                    break;
                case SEEK_CUR:
                    fd.seekPtr += offset;
                    break;
                case SEEK_END:
                    fd.seekPtr = fd.inode.length + offset;
                    break;
                default:
                    return -1;
            }

            if (fd.seekPtr < 0)
            {
                fd.seekPtr = 0;
            }

            if (fd.seekPtr > fd.inode.length)
            {
                fd.seekPtr = fd.inode.length;
            }

            return fd.seekPtr;
        }
    }

    private boolean deallocateBlocks(FileTableEntry fd){
        short invalid = -1;
        if (fd.inode.count != 1)
        {
            SysLib.cerr("Null Pointer");
            return false;
        }
        for (short blockId = 0;
             blockId < Inode.numDirectPointers(); blockId++)
        {
            if (fd.inode.direct[blockId] != invalid)
            {
                superblock.freeUpBlock(blockId);
                fd.inode.direct[blockId] = invalid;
            }
        }
        byte [] data = fd.inode.freeIndirectBlock();

        if (data != null)
        {
            short blockId;
            while((blockId = SysLib.bytes2short(data, 0)) != invalid)
            {
                superblock.freeUpBlock(blockId);
            }
        }
        fd.inode.toDisk(fd.iNumber);
        return true;
    }


    public int delete(String filename) {
        FileTableEntry tcb = open(filename, "w");
        if (directory.ifree(tcb.iNumber) && (close(tcb) == 0)) {
            return 0;
        } else {
            return -1;
        }
    }


    public synchronized int fsize(FileTableEntry entry){
        synchronized(entry)
        {
            Inode inode = entry.inode;
            return inode.length;
        }
    }

    public void sync()
    {
        byte[] tempData = directory.directory2bytes();
        FileTableEntry root = open("/", "w");
        write(root, directory.directory2bytes());
        close(root);
    }
}