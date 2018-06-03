// Inode class

public class Inode {
	
	private final static int iNodeSize = 32;       // fix to 32 bytes
	private final static int directSize = 11;      // # direct pointers
	public final static int UNUSED = 0;
	public final static int USED = 1;
	public final static int READ = 2;
	public final static int WRITE = 3;
	public final static int DELETE = 4;

	public int length;                             // file size in bytes	
	public short count;                            // # file-table entries pointing to this
	public short flag;                             // 0 = unused, 1 = used, ...
	public short direct[] = new short[directSize]; // direct pointers
	public short indirect;                         // a indirect pointer

	Inode( ) {                                     // a default constructor
		length = 0;
		count = 0;
		flag = 1;
		for ( int i = 0; i < directSize; i++ )
			direct[i] = -1;
		indirect = -1;
	}

	Inode( short iNumber ) {                       // retrieving inode from disk
		int blockNum = (iNumber / 16) + 1;
		int offset = iNumber % 16 * iNodeSize;
		
		byte[] blockInfo = new byte[512];	//the block size
		SysLib.rawread(blockNum, blockInfo);
		
		length = SysLib.bytes2int(blockInfo, offset);
		offset += 4; 						//size of an int
		count = SysLib.bytes2short(blockInfo, offset);
		offset += 2;						// size of a short
		flag = SysLib.bytes2short(blockInfo, offset);
		offset += 2;
		for (int i = 0; i < direct.length; i++) {
			direct[i] = SysLib.bytes2short(blockInfo, offset);
			offset += 2;
		}
		indirect = SysLib.bytes2short(blockInfo, offset);
		
	}

	int toDisk( short iNumber ) {                  // save to disk as the i-th inode
		int blockNum = (iNumber / 16) + 1;
		int offset = iNumber % 16 * iNodeSize;
		byte[] iNodeData = new byte[512];
		SysLib.rawread(blockNum, iNodeData);
			
		SysLib.int2bytes(length, iNodeData, offset);
		offset += 4;						//size of an int
		SysLib.short2bytes(count, iNodeData, offset);
		offset += 2;						//size of a short
		SysLib.short2bytes(flag, iNodeData, offset);
		offset += 2;
		for (int i = 0; i < directSize; i++) {
			SysLib.short2bytes(direct[i], iNodeData, offset);
			offset += 2;
		}
		SysLib.short2bytes(indirect, iNodeData, offset);
		
		SysLib.rawwrite(blockNum, iNodeData);
	}
	
	public static int numDirectPointers() {
		return directSize;
	}
	
	
	//freeindirectblock
	//findtargetblock
	//getindexblocknumber
	//setnextblocknumber
	
	
	
	
	
	
}