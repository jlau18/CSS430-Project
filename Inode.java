// Inode class

public class Inode {
	
	private final static int iNodeSize = 32;       // fix to 32 bytes
	private final static int directSize = 11;      // # direct pointers
	private final static int blockSize = 512;		//size of a block
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
		
		byte[] blockInfo = new byte[blockSize];	//the block size
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
		byte[] iNodeData = new byte[blockSize];
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
	
	
	byte[] freeIndirectBlock() {
		if (indirect > 0) {				//check that indirect is valid
			byte[] emptyBlock = new byte[blockSize];
			SysLib.rawread(indirect, emptyBlock);	//read in the indirect block
			indirect = -1;					//set indirect to invalid
			return emptyBlock;
		}
		return null;
	}
	
	int findTargetBlock(int position) {
		int blockNum = position / blockSize;
		if (blockNum < directSize) {
			return direct[blockNum];
		} else {
			byte[] indirectPointers = new byte[blockSize];
			SysLib.rawread(indirect, indirectPointers);
			int slotInIndirect = (blockNum - directSize) * 2;
			return SysLib.bytes2int(indirectPointers, slotInIndirect);
		}
	}
	
	int getIndexBlockNumber() {
		return indirect;
	}
	
	boolean setNextBlocknumber(short blockNum) {
		for (int i = 0; i < directSize; i++) {
			if (direct[i] == -1) {
				direct[i] = blockNum;
				return true;
			}
		}
		if (indirect == -1) {
			return false;
		}
		byte[] indirectBlock = new byte[blockSize];
		SysLib.rawread(indirect, indirectBlock);
		short target = -1;
		for (int i = 0; i < blockSize / 2; i++) {
			short validCheck = SysLib.bytes2short(indirectBlock, 2 * i);
			if (validCheck == -1) {
				target = 2 * i;
				break;
			}
		}
		if (target != -1) {
			SysLib.short2bytes(blockNum, indirectBlock, target);
			SysLib.rawwrite(indirect, indirectBlock);
			return true;
		}
		return false;
	}
	
	void setIndexBlock(short blockNum) {
		byte[] indirectBlock = new byte[blockSize];
		for (int i = 0; i < blockSize / 2; i++) {
			SysLib.short2bytes((short) -1, indirectBlock, 2 * i);
		}
		indirect = blockNum;
		SysLib.rawwrite(indirect, indirectBlock);
	}
	
	
}