// directory.java

public class Directory {
	private static int maxChars = 30; // max characters of each file name

	// Directory entries
	private int fsize[];        // each element stores a different file size.
	private char fnames[][];    // each element stores a different file name.

	public Directory( int maxInumber ) { // directory constructor
		fsize = new int[maxInumber];     // maxInumber = max files
		for ( int i = 0; i < maxInumber; i++ ) 
			fsize[i] = 0;                 // all file size initialized to 0
		fnames = new char[maxInumber][maxChars];
		String root = "/";                // entry(inode) 0 is "/"
		fsize[0] = root.length( );        // fsize[0] is the size of "/".
		root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
	}

	
	// assumes data[] received directory information from disk
	// initializes the Directory instance with this data[]
	public void bytes2directory( byte data[] ) {
		int maxFileCount = fsize.length;
		
		for (int i = 0; i < maxFileCount; i++) {
			fsize[i] = SysLib.bytes2int(data, 4 * i);
		}
		
		int offset = maxFileCount * 4;
		for (int i = 0; i < maxFileCount; i++) {
			for (int j = 0; j < maxChars; j++) {					//move by size of char 
				fnames[i][j] = (char) SysLib.bytes2short(data, (2 * j) + offset);
			}
			offset += 60;							//increment by namespace
		}
		
	}
	
	// converts and return Directory information into a plain byte array
	// this byte array will be written back to disk
	// note: only meaningfull directory information should be converted
	// into bytes.
	public byte[] directory2bytes( ) {
		int maxFileCount = fsize.length;
		byte[] result = new byte[64 * maxFileCount];		// 4bytes for the int, 60 bytes for name
		
		for (int i = 0; i < maxFileCount; i++) {
			SysLib.int2bytes(fsize[i], result, 4 * i);		//increment by size of int
		} 
		
		int offset = maxFileCount * 4;
		for (int i = 0; i < maxFileCount; i++) {
			for (int j = 0; j < maxChars; j++) {				
				SysLib.short2bytes((short) fnames[i][j], result, (2 * j) + offset);
			}
			offset += 60;								// increment by namespace
		}
		
		return result;
	}

	// filename is the one of a file to be created.
	// allocates a new inode number for this filename
	public short ialloc( String filename ) {
		for (int i = 0; i < fnames.length; i++) {
			if (fsize[i] == 0) {
				char[] nameAsArr = filename.toCharArray();
				for (int j = 0; j < nameAsArr.length; j++) {
					fnames[i][j] = nameAsArr[j];
				}
				fsize[i] = filename.length();
				short test = (short) i;
				return (short) i;
			}
		}
		return -1;
	}

	// deallocates this inumber (inode number)
	// the corresponding file will be deleted.
	public boolean ifree( short iNumber ) {
		if (iNumber < fsize.length && fsize[iNumber] > 0) {
			for (int i = 0; i < maxChars; i++) {
				fnames[iNumber][i] = 0;
			}
			fsize[iNumber] = 0;
			return true;
		}
		return false;
	}
	
	// returns the inumber corresponding to this filename
	public short namei( String filename ) {
		for (int i = 0; i < fnames.length; i++) {
			String curName = new String(fnames[i]).trim();
			if (filename.equals(curName)) {
				return (short) i;
			}
		}
		return (short) -1;
		
	}
	
	public int maxFiles() {
		return fsize.length;
	}
}
