package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * An abstract run-length input stream.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 * @param <T>
 */
public class RunLengthInputStream<T, S> implements PopableStream<T> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * Max number of buffered RLEs.
	 */
	final static int MAX_BUF = 1000000;

	/**
	 * Input buffer
	 */
	protected LinkedList<RunLength<T>> buffer = new LinkedList<RunLength<T>>();

	/**
	 * The input stream the data is read from by the RLE implementations
	 */
	protected InputStream inData;

	/**
	 * The input stream the lengths are read from by the RLE implementations
	 */
	protected InputStream inLen;

	/**
	 * The current run-length.
	 */
	protected RunLength<T> rle = null;

	/**
	 * The data type that is used for storing the rle length values.
	 */
	protected Class<S> lengthType = null;

	/**
	 * The datatype.
	 */
	protected Class<T> datatype = null;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * popable input stream
	 */
	private PopableStream<T> popableStream;

	/**
	 * Constructor.
	 * 
	 * @param in
	 */
	public RunLengthInputStream(PopableStream<T> inData, InputStream inLen, Class<T> datatype, Class<S> lengthType,
			boolean debug) {
		this(inData.getInputStream(), inLen, datatype, lengthType, debug);
		this.popableStream = inData;
	}

	/**
	 * Constructor.
	 * 
	 * @param in
	 */
	public RunLengthInputStream(InputStream inData, InputStream inLen, Class<T> datatype, Class<S> lengthType,
			boolean debug) {
		this.inData = inData;
		this.inLen = inLen;
		this.datatype = datatype;
		this.lengthType = lengthType;
		this.debug = debug;
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
	 * Fill the input buffer.
	 * 
	 * @return false if not possible.
	 */
	public boolean fillBuffer() throws IOException {
		// load the object
		if (popableStream != null) {
			if ( popableStream.available() <= 0)
				return false;
		} else {
		if (inData.available() <= 0)
			return false;
		}

		do {
			Object obj = null;
			if (popableStream != null) {
				obj = popableStream.pop();
			}
			else {
				if (datatype == Byte.class) {
					int tmp = inData.read();
					if (tmp < 0)
						return false;
					obj = (byte) tmp;
				} else if (datatype == Character.class) {
					byte[] tmp = new byte[2];
					if (inData.read(tmp) < 2)
						return false;
					obj = MathUtil.byte2char(tmp);
				} else if (datatype == Integer.class) {
					byte[] tmp = new byte[4];
					if (inData.read(tmp) < 4)
						return false;
					obj = MathUtil.byte2int(tmp);
				} else if (datatype == String.class) {
					byte[] tmp = new byte[4];
					if (inData.read(tmp) < 4)
						return false;
					int l = MathUtil.byte2int(tmp);
					if (l > 0) {
						tmp = new byte[l];
						if (inData.read(tmp) < l)
							return false;
						obj = new String(tmp, "UTF-8");
					} else if (l == 0)
						obj = "";
					else
						obj = null;
				} else
					throw new IOException("Unsupported data type " + datatype);
			}

			int len = 0;

			if (lengthType == Byte.class) {
				int tmp = inLen.read();
				if (tmp == -1)
					return false;
				len = MathUtil.byte2charBisection((byte) tmp);
			} else if (lengthType == Character.class) {
				byte[] tmp2 = new byte[2];
				if (inLen.read(tmp2) < 2)
					return false;
				len = MathUtil.byte2char(tmp2);
			} else if (lengthType == Integer.class) {
				byte[] tmp3 = new byte[4];
				if (inLen.read(tmp3) < 4)
					return false;
				len = MathUtil.byte2int(tmp3);
			} else
				throw new IOException("Unsupported length type " + lengthType);

			RunLength<T> rle = new RunLength<T>(datatype.cast(obj), len);
			if (debug)
				System.out.println("read " + rle);
			buffer.add(rle);

		} while ((buffer.size() < MAX_BUF) && (inData.available() > 0));
		return true;
	}

	/**
	 * @return the next run length or null if non available.
	 * @throws IOException
	 */
	private RunLength<T> readRLE() throws IOException {
		if (buffer.size() == 0)
			if (!fillBuffer())
				throw new IOException("Stream depleted!");
		return buffer.pop();
	}

	/**
	 * Get the next object from this RLE-encoded stream or null if the end of
	 * the stream was reached.
	 * 
	 * @return
	 * @throws IOException
	 */
	public T pop() throws IOException {
		entityCounter++;
		if (rle == null)
			rle = readRLE();
		if (rle == null) {
			return null;
		}
		while (!rle.dec()) {
			rle = readRLE();
			if (rle == null) {
				return null;
			}
		}
		T ret = rle.getObject();
		return ret;
	}

	/**
	 * @return the current RLE object.
	 */
	protected RunLength<T> getCurrentRLE() {
		return rle;
	}

	/**
	 * Close this stream.
	 * 
	 * @throws IOException
	 */
	@Override
	public void close() throws IOException {
		if (popableStream != null)
			popableStream.close();
		else
			inData.close();
		inLen.close();
	}

	/**
	 * @return -1 if the stream is depleted
	 */
	@Override
	public int available() throws IOException {
		if (buffer.size() == 0) {
			if (fillBuffer()) {
				return buffer.size();
			}
			if (rle == null)
				return -1;
			return rle.getLen();
		}
		return buffer.size();
	}

	public Class<S> getLengthType() {
		return lengthType;
	}

	@Override
	public Class<T> getDatatype() {
		return datatype;
	}

	@Override
	public String toString() {
		return "[RunLengthInputStream<" + datatype.getName() + "><" + lengthType.getName() + ">]";
	}
}
