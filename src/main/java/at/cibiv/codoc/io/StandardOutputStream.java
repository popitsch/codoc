package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * A standard output stream. Passing null as output stream leads to a > dev/null
 * behavior.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class StandardOutputStream<T> implements PushableStream<T> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;
	
	/**
	 * The wrapped output stream
	 */
	OutputStream out = null;
	
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
	 * @param out
	 * @param datatype
	 */
	public StandardOutputStream(OutputStream out, Class<T> datatype) {
		this( out, datatype, false );
	}
	
	/**
	 * Constructor.
	 * @param out
	 * @param datatype
	 */
	public StandardOutputStream(OutputStream out, Class<T> datatype, boolean debug) {
		this.out = out;
		this.datatype = datatype;
		this.debug = debug;
	}


	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	/**
	 * be careful with null values! They are supported only for strings.
	 */
	@Override
	public void push(T data) throws IOException {
		entityCounter++;
		
		if (debug)
			System.out.println("pushed " + data);

		if (out == null)
			return;

		if (datatype == Byte.class) {
			out.write((Byte) data);
		} else if (datatype == Character.class) {
			out.write(MathUtil.char2byte((Character) data));
		} else if (datatype == Integer.class) {
			out.write(MathUtil.int2byte((Integer) data));
		} else if (datatype == String.class) {
			if (data == null)
				out.write(MathUtil.int2byte(-1));
			else {
				String c = (String) data;
				byte[] b = c.getBytes("UTF-8");
				int l = b.length;
				out.write(MathUtil.int2byte(l));
				if (l > 0) {
					out.write(b);
				}
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
	public Class<T> getDatatype() {
		if (out == null)
			return null;
		return datatype;
	}

	@Override
	public Long getEncodedEntities() {
		return entityCounter;
	}
	
	@Override
	public String toString() {
		if (out == null)
			return "[NullOutputStream<" + datatype.getName() + ">]";
		else
			return "[StandardOutputStream<" + datatype.getName() + ">]";
	}
	
	@Override
	public void flush() throws IOException {
		out.flush();
	}
}
