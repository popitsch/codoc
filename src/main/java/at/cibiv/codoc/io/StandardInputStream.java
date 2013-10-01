package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * A standard output stream. Passing null will lead to a stream that always
 * returns null.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class StandardInputStream<T> implements PopableStream<T> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * The wrapped input stream
	 */
	InputStream in = null;

	/**
	 * The stream data type
	 */
	Class<T> datatype = null;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * Constructor.
	 * 
	 * @param in
	 * @param datatype
	 * @param debug
	 */
	public StandardInputStream(InputStream in, Class<T> datatype) {
		this(in, datatype, false);
	}

	/**
	 * Constructor.
	 * 
	 * @param in
	 * @param datatype
	 * @param debug
	 */
	public StandardInputStream(InputStream in, Class<T> datatype, boolean debug) {
		this.in = in;
		this.datatype = datatype;
		this.debug = debug;
	}

	@Override
	public InputStream getInputStream() {
		return in;
	}

	/**
	 * @return the number of decoded entities if available
	 */
	public Long getDecodedEntities() {
		return entityCounter;
	}

	@Override
	public int available() throws IOException {
		if (in == null)
			return -1;
		return in.available();
	}

	/**
	 * be careful with null values! They are supported only for strings.
	 */
	@Override
	public T pop() throws IOException {
		entityCounter++;
		T ret = null;
		if (datatype == Byte.class) {
			int i = in.read();
			if (i < 0)
				throw new IOException("Could not read byte! (so far read " + entityCounter + " entities)");
			ret = datatype.cast((byte) i);
		} else if (datatype == Character.class) {
			byte tmp[] = new byte[2];
			if (in.read(tmp) < 2)
				throw new IOException("Could not read char! (so far read " + entityCounter + " entities)");
			ret = datatype.cast(MathUtil.byte2char(tmp));
		} else if (datatype == Integer.class) {
			byte tmp[] = new byte[4];
			if (in.read(tmp) < 4)
				throw new IOException("Could not read int! (so far read " + entityCounter + " entities)");
			ret = datatype.cast(MathUtil.byte2int(tmp));
		} else if (datatype == String.class) {
			String r = null;
			byte[] tmp = new byte[4];
			if (in.read(tmp) < tmp.length)
				throw new IOException("Could not read string! (so far read " + entityCounter + " entities)");
			int l = MathUtil.byte2int(tmp);
			if (l > 0) {
				byte[] b = new byte[l];
				in.read(b);
				r = new String(b, "UTF-8");
			} else if (l == 0)
				r = "";
			else
				r = null;
			ret = datatype.cast(r);
		} else
			throw new IOException("Unsupported stream data type for " + datatype + " stream");
		if (debug)
			System.out.println("poped " + ret);
		return ret;
	}

	@Override
	public void close() throws IOException {
		if (in != null)
			in.close();
	}

	@Override
	public Class<T> getDatatype() {
		if (in == null)
			return null;
		return datatype;
	}

	@Override
	public String toString() {
		if (in == null)
			return "[NullInputStream<" + datatype.getName() + ">]";
		else
			return "[StandardInputStream<" + datatype.getName() + ">]";
	}
}
