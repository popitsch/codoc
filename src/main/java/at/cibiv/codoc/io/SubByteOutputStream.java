package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * Writes symbols to a wrapped output stream.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 * @param <SYMBOLS>
 */
public class SubByteOutputStream implements PushableStream<Integer> {

	/**
	 * Internal buffer size.
	 */
	final static int BUF_SIZE = 1024 * 1024;

	/**
	 * Internal buffer.
	 */
	ByteBuffer buf;
	int M;
	int k;
	OutputStream out;
	byte current = 0;
	int pos = 0;
	int max = 0;

	/**
	 * Number of bits written by this stream.
	 */
	private long writtenBits = 0L;

	/**
	 * The number of emitted code words (=run-lengths).
	 */
	protected long writtenCodewordCounter = 0;

	Class<Integer> datatype = Integer.class;

	private boolean closed = false;

	int usedbits = 0;

	/**
	 * Constructor. M=2^k
	 * 
	 * @param out
	 * @param k
	 */
	public SubByteOutputStream(OutputStream out, int max) {
		this.out = out;
		this.max = max;
		buf = ByteBuffer.allocate(BUF_SIZE);
		usedbits = MathUtil.ld(max + 2);
		// 2 special symbols! One for EOF, one for NULL values,
	}

	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	/**
	 * Write a bit to the stream
	 * 
	 * @param bit
	 * @throws IOException
	 */
	public void writeBit1() throws IOException {
		int mask = 0;
		switch (pos) {
		case 0:
			mask = 128;
			break;
		case 1:
			mask = 64;
			break;
		case 2:
			mask = 32;
			break;
		case 3:
			mask = 16;
			break;
		case 4:
			mask = 8;
			break;
		case 5:
			mask = 4;
			break;
		case 6:
			mask = 2;
			break;
		case 7:
			mask = 1;
			break;
		default:
			// emit current byte
			if (buf.position() >= BUF_SIZE) {
				out.write(buf.array());
				buf.clear();
			}
			buf.put(current);
			current = 0;
			pos = 0;
			mask = 128;
			break;
		}
		current |= mask;
		pos++;
		writtenBits++;
	}

	/**
	 * Write a bit to the stream
	 * 
	 * @param bit
	 * @throws IOException
	 */
	public void writeBit0() throws IOException {
		if (pos > 7) {
			// emit current byte
			if (buf.position() >= buf.capacity()) {
				out.write(buf.array());
				buf.clear();
			}
			buf.put(current);
			current = 0;
			pos = 0;
		}
		pos++;
		writtenBits++;
	}

	/**
	 * Write the passed number encoded to the underlying output stream.
	 * 
	 * @param num
	 * @throws IOException
	 */
	@Override
	public void push(Integer c) throws IOException {
		if (closed)
			throw new IOException("Stream already closed!");
		int code = 0;
		if (c == null) {
			code = 1;
		} else {
			if (c > max)
				throw new IOException("Symbol out of bounds!");
			code = c + 2;
		}
		for (int i = 0; i < usedbits; i++) {
			if ((code & 1) == 1)
				writeBit1();
			else
				writeBit0();
			code >>= 1;
		}
	}

	/**
	 * Close the stream. Flushes unfinished bytes to the output stream.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		if (closed)
			return;
		try {
			// write EOF symbol
			for (int i = 0; i < usedbits; i++) {
				writeBit0();
			}
			buf.put(current);
			if (buf.position() >= buf.capacity()) {
				out.write(buf.array());
				buf.clear();
			}
			buf.put(current);

			out.write(buf.array(), 0, buf.position());
		} finally {
			out.close();
			this.closed = true;
		}
	}

	public long getWrittenBits() {
		return writtenBits;
	}

	@Override
	public Long getEncodedEntities() {
		return writtenCodewordCounter;
	}

	@Override
	public String toString() {
		return "[DataSymbolOutputStream]";
	}

	@Override
	public Class<Integer> getDatatype() {
		return datatype;
	}

	public int getUsedbits() {
		return usedbits;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}
}
