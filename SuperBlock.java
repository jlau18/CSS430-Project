// Superblock class

public class SuperBlock{
	
	public int totalBlocks;
	public int totalInodes;
	public int freeList;
	public int lastFreeBlock;
	
	public SuperBlock(int totalBlocks) {
		
		byte[] curSuperBlock = new byte[512];
		SysLib.rawread(0, curSuperBlock);
		
		totalBlocks = SysLib.bytes2int(curSuperBlock, 0);
		totalInodes = SysLib.bytes2int(curSuperBlock, 4);
		freeList = SysLib.bytes2int(curSuperBlock, 8);
		lastFreeBlock = SysLib.bytes2int(curSuperBlock, 12);
		
		if (!(totalBlocks == totalBlocks && totalInodes > 0 && freeList > 1 && lastFreeBlock < totalBlocks)) {
			totalBlocks = totalBlocks;
			lastFreeBlock = totalBlocks - 1;
			reformat(64);
		}
		
	}
	
	public void reformat(int inodeCount) {
		totalInodes = inodeCount;
		
		int inodeBlockCount = inodeCount / 16;
		freeList = inodeBlockCount + 1;
		
		Inode blankInode = new Inode();
		blankInode.flag = 0;
		
		for (short i = 0; i < inodeCount; i++) {
			blankInode.toDisk(i);
		}
		
		byte[] blankBlock = new byte[512];
		for (int i = 0; i < 512; i++) {
			blankBlock[i] = 0;
		}
		
		for (int i = freeList; i < totalBlocks - 1; i++) {		
			int nextFree = i + 1;
			SysLib.int2bytes(nextFree, blankBlock, 0);
			SysLib.rawwrite(i, blankBlock);
		}
		
		SysLib.short2bytes((short)-1, blankBlock, 0);
		SysLib.rawwrite(totalBlocks - 1, blankBlock);
		
		byte[] newSuperBlock = new byte[512];
		int offset = 0;
		SysLib.int2bytes(totalBlocks, newSuperBlock, offset);
		offset += 4;
		SysLib.int2bytes(totalInodes, newSuperBlock, offset);
		offset += 4;
		SysLib.int2bytes(freeList, newSuperBlock, offset);
		offset += 4;
		SysLib.int2bytes(lastFreeBlock, newSuperBlock, offset);
		SysLib.rawwrite(0, newSuperBlock);
				
	}
	
	public int nextFreeBlock() {
		int retVal = freeList; 			//current val at freelist is next free block
		byte[] gettingNextFree = new byte[512];
		SysLib.rawread(freeList, gettingNextFree);
		freeList = SysLib.bytes2int(gettingNextFree, 0);
		return retVal;		
	}
	
	public boolean freeUpBlock(short blockNum) {
		if (blockNum > totalInodes && blockNum < totalBlocks) {
			byte[] updateOldLast = new byte[512];			//update old last free block values
			SysLib.rawread(lastFreeBlock, updateOldLast);
			SysLib.short2bytes(blockNum, updateOldLast, 0);
			SysLib.rawwrite(lastFreeBlock, updateOldLast);
			
			byte[] emptyBlock = new byte[512];				// clear out block for given blocknum
			SysLib.short2bytes((short) -1, emptyBlock, 0);
			SysLib.rawwrite(blockNum, emptyBlock);
			lastFreeBlock = blockNum;
			
			return true;
		}
		return false;
	}
	
	
}