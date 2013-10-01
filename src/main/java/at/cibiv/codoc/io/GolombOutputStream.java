package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;


import at.cibiv.ngs.tools.util.MathUtil;

/**
 * A Golomb-encoding output stream. This implementation supports only
 * Golomb-power-of-2 (GOP2) codes. Use this stream *only* for small, positive
 * int values!
 * 
 * M = 2^k
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class GolombOutputStream<T> extends OutputStream implements PushableStream<T> {

	/**
	 * golden ration. needed for parameter estimation.
	 */
	private static final float GOLDEN_RATIO = (float) (Math.sqrt(5) + 1f) / 2f;

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

	/**
	 * Number of bits written by this stream.
	 */
	private long writtenBits = 0L;

	/**
	 * The number of emitted code words (=run-lengths).
	 */
	protected long writtenCodewordCounter = 0;

	Class<T> datatype = null;

	private boolean closed = false;

	/**
	 * Constructor. M=2^k
	 * 
	 * @param out
	 * @param k
	 */
	public GolombOutputStream(OutputStream out, int k, Class<T> datatype) {
		this.M = MathUtil.pow2(k);
		this.k = k;
		this.out = out;
		this.datatype = datatype;
		buf = ByteBuffer.allocate(BUF_SIZE);
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
	 * Write the passed number Golomb-encoded to the underlying output stream.
	 * 
	 * @param num
	 * @throws IOException
	 */
	@Override
	public void push(T num) throws IOException {
		if (num == null)
			throw new IOException("Cannot write null symbol to Golomb output stream.");
		if (datatype == Byte.class)
			write(((Byte) num).byteValue());
		else if (datatype == Character.class)
			write(((Character) num).charValue());
		else
			write((Integer) num);
	}

	@Override
	public void write(int num) throws IOException {
		if (closed)
			throw new IOException("Stream already closed!");
		if (num < 0)
			throw new IOException("Encoding of negative golomb numbers not yet supported! " + num);
		writtenCodewordCounter++;
		int q = num / M;
		for (int i = 0; i < q; i++) {
			writeBit1();
		}
		writeBit0();
		int v = 1;
		for (int i = 0; i < k; i++) {
			if ((v & num) == v)
				writeBit1();
			else
				writeBit0();
			v = v << 1;
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
			if (pos != 0) {
				if (buf.position() >= buf.capacity()) {
					out.write(buf.array());
					buf.clear();
				}
				// terminate by filling the byte with 1s
				while (pos <= 7) {
					writeBit1();
				}
				buf.put(current);
			}
			out.write(buf.array(), 0, buf.position());
		} finally {
			out.close();
			this.closed = true;
			buf=null;
		}
	}

	/**
	 * Calculate a M=2^k with k derived from the mean by the approx. formula
	 * given in Kiely, A., Selecting the Golomb Parameter in Rice Coding, IPN
	 * Progress Report 42, 2004
	 * 
	 * @param mUcons
	 * @return
	 */
	public static int calcK(float mu) {
		int k = Math.max(0, 1 + MathUtil.ld((int) Math.floor(Math.log(GOLDEN_RATIO - 1f) / Math.log(mu / (mu + 1)))));
		return k;
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
		return "[GolombOutputStream (k=" + k + ">]";
	}

	@Override
	public Class<T> getDatatype() {
		return datatype;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		// System.out.println(GolombOutputStream.calcK(11.70f));

		// long t = System.currentTimeMillis();
		// GolombOutputStream o = new GolombOutputStream(new
		// FileOutputStream("c:/data/bio/sam-testsuite/golo.tmp"), 16);
		// FileOutputStream o2 = new
		// FileOutputStream("c:/data/bio/sam-testsuite/raw.tmp");
		// Random rand = new Random();
		// for (int i = 0; i < 1000000; i++) {
		// long x = rand.nextInt(100); // M = 4 : compression > 50%
		// o.write(x);
		// o2.write(MathUtil.long2byte(x));
		// if (i % 1000 == 0)
		// System.out.print(".");
		// }
		// o.close();
		// o2.close();
		// System.out.println("Finished in " + (System.currentTimeMillis() - t)
		// + "ms.");
		//
		// GolombOutputStream o = new GolombOutputStream(new
		// FileOutputStream("c:/data/bio/sam-testsuite/golo.tmp"), 4);
		// ByteRLE r = new ByteRLE((byte) CharEncoder.CHARACTERS.E.ordinal());
		// r.inc();
		// System.out.println(r);
		// r.write(o);
		// r = new ByteRLE((byte) CharEncoder.CHARACTERS.A.ordinal());
		// r.inc();
		// r.inc();
		// System.out.println(r);
		// r.write(o);
		// o.close();
		//
		// GolombInputStream in = new GolombInputStream(new
		// FileInputStream("c:/data/bio/sam-testsuite/golo.tmp"), 4);
		// ByteRLE t = new ByteRLE((byte) 0);
		// t.read(in);
		// System.err.println(t);
		// t.read(in);
		// System.err.println(t);
		for (int i = 0; i < 30; i++)
			System.out.println(i + " => " + GolombOutputStream.calcK(i));

	}

	@Override
	public void flush() throws IOException {
		super.flush();
	}
}
