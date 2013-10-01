package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;

import at.cibiv.ngs.tools.util.MathUtil;

public class MixedInputStream implements PopableStream<Object> {

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
	Class<?> datatype = Byte.class;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;
	
	/**
	 * Constructor.
	 * 
	 * @param in
	 * @param debug
	 */
	public MixedInputStream(InputStream in, boolean debug) {
		this.in = in;
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
	
	/**
	 * Pop a byte from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Byte popByte() throws IOException {
		setExpectedDatatype(Byte.class);
		return (Byte) pop();
	}

	/**
	 * Pop a character from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Character popCharacter() throws IOException {
		setExpectedDatatype(Character.class);
		return (Character) pop();
	}

	/**
	 * Pop an integer from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Integer popInteger() throws IOException {
		setExpectedDatatype(Integer.class);
		return (Integer) pop();
	}

	/**
	 * Pop a long from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Long popLong() throws IOException {
		setExpectedDatatype(Long.class);
		return (Long) pop();
	}

	/**
	 * Pop a string from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public String popString() throws IOException {
		setExpectedDatatype(String.class);
		return (String) pop();
	}

	@Override
	public Object pop() throws IOException {
		entityCounter++;
		Object ret = null;
		if (datatype == Byte.class) {
			int i = in.read();
			if (i < 0)
				throw new IOException("Could not read byte!");
			ret = datatype.cast((byte) i);
		} else if (datatype == Character.class) {
			byte tmp[] = new byte[2];
			if (in.read(tmp) < 2)
				throw new IOException("Could not read char!");
			ret = datatype.cast(MathUtil.byte2char(tmp));
		} else if (datatype == Integer.class) {
			byte tmp[] = new byte[4];
			if (in.read(tmp) < 4)
				throw new IOException("Could not read int! ");
			ret = datatype.cast(MathUtil.byte2int(tmp));
		} else if (datatype == Long.class) {
			byte tmp[] = new byte[8];
			if (in.read(tmp) < 8)
				throw new IOException("Could not read long! ");
			ret = datatype.cast(MathUtil.byte2long(tmp));
		} else if (datatype == String.class) {
			String r = null;
			byte[] tmp = new byte[4];
			if (in.read(tmp) < tmp.length)
				throw new IOException("Could not read string!");
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
		in.close();
	}

	@Override
	public int available() throws IOException {
		if (in == null)
			return -1;
		return in.available();
	}

	@Override
	public Class<Object> getDatatype() {
		return Object.class;
	}

	/**
	 * Sets the expected data type for the next pop() call.
	 * 
	 * @param datatype
	 */
	public void setExpectedDatatype(Class<?> datatype) {
		this.datatype = datatype;
	}

}
