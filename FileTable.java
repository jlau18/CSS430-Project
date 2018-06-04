import java.util.*;

public class FileTable {
    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory
    public static final int unused = 0, used = 1, read = 2, write = 3;


    public FileTable( Directory directory ) { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    // major public methods
    public synchronized FileTableEntry falloc( String filename, String mode ) {
        // allocate a new file (structure) table entry for this file name
        short iNumber = -1;
        Inode inode = null;

        while(true){
            // get inumber from inode
            if(filename.equals("/")){ iNumber = (short) 0; }
            else { iNumber = dir.namei(filename); }
            // if inode exist
            if(iNumber >= 0 && iNumber < dir.maxFiles()){
                inode = new Inode(iNumber);

                if(mode.equals("r")){ // read only
                    if(inode.flag == used || inode.flag == unused
                            || inode.flag == read){
                        inode.flag = read;
                        inode.seekPtr = 0;
                        break;
                    }
                    else if(inode.flag == write){
                        try{ wait(); }
                        catch(InterruptedException e){ }
                        break;
                    }
                }
                else{  // writing or writing/reading or append
                    if(inode.flag == used || inode.flag == unused){
                        inode.seekPtr = 0;
                        inode.flag = write;
                        break;
                    }
                    else{
                        try{ wait(); }
                        catch(InterruptedException e){ }
                        inode.seekPtr = 0;
                        inode.flag = used;
                        //break;
                    }
                }
            }
            else if(!mode.equals("r")){ // inode does not exist
                // create new inode and get inumber
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = write;
                inode.seekPtr = 0;
                break;
            }
            else return null;
        }

        // increment inode count for num users
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry entry = new FileTableEntry(inode,iNumber,mode);
        table.addElement(entry);
        return entry;
    }

    public synchronized boolean ffree( FileTableEntry e ) {
        if(table.remove(e)){ // removes if in table
            if(e.inode.flag == read){
                if(e.inode.count == 1){
                    e.inode.flag = used;
                    notify();
                }
            }
            else if(e.inode.flag == write){
                e.inode.flag = used;
                notifyAll();
            }

            e.inode.count -= 1; // decrease user count
            e.inode.toDisk(e.iNumber); // saves to disk
            return true;
        }
        else return false;
    }

    public synchronized boolean fempty( ) {
        return table.isEmpty( );  // return if table is empty
    }                            // should be called before starting a format
}