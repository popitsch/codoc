package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * A mixed output stream.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class MixedOutputStream implements PushableStream<Object> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * The wrapped output stream
	 */
	OutputStream out = null;
	
	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * Constructor
	 * 
	 * @param out
	 * @param debug
	 */
	public MixedOutputStream(OutputStream out, boolean debug) {
		this.out = out;
		this.debug = debug;
	}


	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	
	/**
	 * Push data to this stream.
	 * Note that null values are not allowed!
	 */
	public void push(Object data) throws IOException {
		if (debug)
			System.out.println("pushed " + data);

		if (out == null)
			return;
		
		entityCounter++;

		Class<?> datatype = data.getClass();

		if (datatype == Byte.class) {
			out.write((Byte) data);
		} else if (datatype == Character.class) {
			out.write(MathUtil.char2byte((Character) data));
		} else if (datatype == Integer.class) {
			out.write(MathUtil.int2byte((Integer) data));
		} else if (datatype == Long.class) {
			out.write(MathUtil.long2byte((Long) data));
		} else if (datatype == String.class) {
			String c = (String) data;
			byte[] b = c.getBytes("UTF-8");
			int l = b.length;
			out.write(MathUtil.int2byte(l));
			if (l > 0) {
				out.write(b);
			}
		} else
			throw new IOException("Unsupported stream data type for " + datatype + " stream: " + data.getClass());
	}

	@Override
	public void close() throws IOException {
		if (out != null)
			out.close();
	}

	@Override
	public Class<Object> getDatatype() {
		return Object.class;
	}

	@Override
	public Long getEncodedEntities() {
		return entityCounter;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}
}
