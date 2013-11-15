package at.cibiv.codoc.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import at.cibiv.ngs.tools.sam.iterator.CharEncoder.SYMBOLS;
import at.cibiv.ngs.tools.util.MathUtil;

/**
 * An input stream reading Golomb encoded numbers.
 * 
 * @author popitsch
 * 
 */
public class SubByteInputStream implements PopableStream<Integer> {

	final static int BUF_SIZE = 1024 * 1024;
	ByteBuffer buf;
	InputStream in;
	byte current = 0;
	int pos = 8;
	final static byte[] mask = new byte[] { (byte) 128, 64, 32, 16, 8, 4, 2, 1 };
	int available = 0;
	Class<Integer> datatype = Integer.class;
	int usedbits = 0;
	Integer cached = null;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * Constructor.
	 * 
	 * @param in
	 * @param k
	 * @throws IOException
	 */
	public SubByteInputStream(InputStream in, int max) throws IOException {
		this.in = in;
		byte[] tmp = new byte[BUF_SIZE];
		if (in != null)
			this.available = in.read(tmp);

		if (this.available >= 0)
			this.buf = ByteBuffer.wrap(tmp, 0, this.available);
		this.pos = 8; // force byte reload
		usedbits = MathUtil.ld(max + 2); // 2 special
		// symbols! One
		// for EOF, one
		// for NULL
		// values,
		if (this.available >= 0)
			pop();
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
	public Class<Integer> getDatatype() {
		return this.datatype;
	}

	/**
	 * @return -1 if the stream is empty
	 */
	public int available() throws IOException {
		return this.available;
	}

	/**
	 * Read a bit from the stream.
	 * 
	 * @return
	 * @throws IOException
	 */
	public boolean readBit() throws IOException {
		if (pos > 7) {
			this.available--;
			current = buf.get();
			pos = 0;
			if (available == 0) {
				// load new block
				byte[] tmp = new byte[BUF_SIZE];
				this.available = in.read(tmp);
				if (this.available != -1)
					buf = ByteBuffer.wrap(tmp, 0, this.available);
			}
		}
		boolean ret = ((current & mask[pos]) == mask[pos]);
		pos++;
		return ret;
	}

	/**
	 * Get the next integer number
	 * 
	 * @return
	 * @throws IOException
	 */
	@Override
	public Integer pop() throws IOException {
		entityCounter++;
		Integer ret = cached;
		int val = 0;
		int mask = 1;
		for (int i = 0; i < usedbits; i++) {
			if (readBit())
				val = val | mask;
			mask <<= 1;
		}
		switch (val) {
		case 0:
			// EOF
			this.available = -1;
			this.cached = null;
			break;
		case 1:
			// null
			this.cached = null;
			break;
		default:
			this.cached = val - 2;
		}
		return ret;
	}

	/**
	 * Close this stream
	 */
	public void close() throws IOException {
		in.close();
	}

	@Override
	public String toString() {
		return "[DataSymbolInputStream]";
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		SubByteOutputStream dout = new SubByteOutputStream(bout, SYMBOLS.values().length);
		dout.push(SYMBOLS.A.ordinal());
		dout.push(SYMBOLS.A.ordinal());
		dout.push(SYMBOLS.A.ordinal());
		dout.push(SYMBOLS.A.ordinal());
		dout.push(SYMBOLS.A.ordinal());
		dout.push(SYMBOLS.A.ordinal());
		dout.push(null);
		dout.close();
		System.out.println("used " + bout.toByteArray().length + " bytes");

		ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
		SubByteInputStream din = new SubByteInputStream(bin, SYMBOLS.values().length);
		while (din.available() > 0) {
			System.out.println("R " + din.pop());
		}

	}

}
