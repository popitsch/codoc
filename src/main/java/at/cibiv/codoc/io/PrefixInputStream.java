package at.cibiv.codoc.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * A input stream corresponding to @see PrefixOutputStream.
 * 
 * 
 * @author niko.popitsch@univie.ac.at
 */
public class PrefixInputStream<S> implements PopableStream<String> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * Counts the delivered strings
	 */
	private long deliveredStrings = 0L;

	/**
	 * Counts the delivered characters
	 */
	private long readChars = 0L;

	/**
	 * Stores the current prefix.
	 */
	private String prefix = "";

	/**
	 * The input stream the data is read from by the RLE implementations
	 */
	protected InputStream inData;

	/**
	 * The input stream the lengths are read from by the RLE implementations
	 */
	protected InputStream inLen;

	/**
	 * The data type that is used for storing the rle length values.
	 */
	protected Class<S> lengthType = null;

	/**
	 * For storing length differences.
	 */
	protected int lastLen = 0;

	/**
	 * When true, the stream will store string=length differences instead of
	 * rela lengths.
	 */
	private boolean storeLengthDiff = true;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * A map for prefixes.
	 */
	protected Map<Integer, String> prefixMap = new HashMap<Integer, String>();

	/**
	 * Constructor.
	 * 
	 * @param in
	 * @throws IOException
	 */
	public PrefixInputStream(InputStream inData, InputStream inLen, InputStream inMap, Class<S> lengthType,
			boolean storeLengthDiff, boolean debug) throws IOException {
		this.inData = inData;
		this.inLen = inLen;
		this.lengthType = lengthType;
		this.storeLengthDiff = storeLengthDiff;
		this.debug = debug;

		StandardInputStream<String> im = new StandardInputStream<String>(inMap, String.class);
		// read prefix map
		int c = 0;
		while (im.available() > 0) {
			String prefix = im.pop();
			if (prefix == null)
				break;
			prefixMap.put(c, prefix);
			c++;
		}
	}

	@Override
	public InputStream getInputStream() {
		return inData;
	}

	/**
	 * @return the number of decoded entities if available
	 */
	public Long getDecodedEntities() {
		return entityCounter;
	}

	/**
	 * @return the next string or null if non available.
	 * @throws IOException
	 */
	public String next() throws IOException {
		entityCounter++;

		// load length
		int len = 0;

		if (storeLengthDiff) {
			byte[] tmp3 = new byte[4];
			if (inLen.read(tmp3) < 4)
				return null;
			len = MathUtil.byte2int(tmp3);
			len += lastLen;
			lastLen = len;
		} else {
			if (lengthType == Byte.class) {
				int tmp = inLen.read();
				if (tmp == -1)
					return null;
				len = MathUtil.byte2charBisection((byte) tmp);
			} else if (lengthType == Character.class) {
				byte[] tmp2 = new byte[2];
				if (inLen.read(tmp2) < 2)
					return null;
				len = MathUtil.byte2char(tmp2);
			} else if (lengthType == Integer.class) {
				byte[] tmp3 = new byte[4];
				if (inLen.read(tmp3) < 4)
					return null;
				len = MathUtil.byte2int(tmp3);
			} else
				throw new IOException("Unsupported length type " + lengthType);
		}

		String cand = "";
		if (len != 0) {
			byte[] b = new byte[len];
			if (inData.read(b) < len)
				return null;
			cand = new String(b, "UTF-8");
		}

		if (cand.startsWith("@")) {
			int pid = Integer.parseInt(cand.substring(1));
			this.prefix = prefixMap.get(pid);
			if (this.prefix == null)
				throw new IOException("Unknown prefix key " + cand);
			String ret = next();
			return ret;
		}
		// if (cand.startsWith("&")) {
		// this.prefix = this.prefix + cand.substring(1);
		// String ret = next();
		// return ret;
		// }
		String ret = prefix + cand;
		deliveredStrings++;
		readChars += ret.length();

		if (debug)
			System.out.println("Read [" + ret + "]");

		return ret;
	}

	@Override
	public String pop() throws IOException {
		return next();
	}

	@Override
	public int available() throws IOException {
		return inLen.available();
	}

	@Override
	public Class<String> getDatatype() {
		return String.class;
	}

	public Class<S> getLengthType() {
		return lengthType;
	}

	/**
	 * Close this stream.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		inLen.close();
		inData.close();
	}

	/**
	 * @return the number of delivered String objects.
	 */
	public long getDeliveredStrings() {
		return this.deliveredStrings;
	}

	public long getReadChars() {
		return readChars;
	}


}
