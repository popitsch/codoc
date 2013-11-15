package at.cibiv.codoc.io;

/**
 * A block of data.
 * @author niko.popitsch@univie.ac.at
 *
 */
public abstract class AbstractDataBlock {

	/**
	 * ID of this block
	 */
	String id = null;
	
	/**
	 * Envelope compression method of this block.
	 */
	BLOCK_COMPRESSION_METHOD compressionMethod = DEFAULT_COMPRESSION_METHOD;

	/**
	 * List of supported compression types
	 */
	public static enum BLOCK_COMPRESSION_METHOD {
		NONE, GZIP, BZIP2
	}
	
	public static BLOCK_COMPRESSION_METHOD DEFAULT_COMPRESSION_METHOD = BLOCK_COMPRESSION_METHOD.GZIP;

	/**
	 * Convert a string to its corresponding compression method.
	 * Returns the default compression method for invalid/null strings.
	 * @param s
	 * @return
	 */
	public static BLOCK_COMPRESSION_METHOD compFromString(String s) {
		if (s == null)
			return DEFAULT_COMPRESSION_METHOD;
		for (BLOCK_COMPRESSION_METHOD t : BLOCK_COMPRESSION_METHOD.values()) {
			if (t.name().equalsIgnoreCase(s))
				return t;
		}
		return DEFAULT_COMPRESSION_METHOD;
	}
	


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}



	public BLOCK_COMPRESSION_METHOD getCompressionMethod() {
		return compressionMethod;
	}



	public void setCompressionMethod(BLOCK_COMPRESSION_METHOD compressionMethod) {
		this.compressionMethod = compressionMethod;
	}

}
