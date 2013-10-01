package at.cibiv.codoc.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import at.cibiv.ngs.tools.util.MathUtil;

/**
 * An input stream reading Golomb encoded numbers.
 * 
 * @author popitsch
 * 
 */
public class GolombInputStream<T> extends InputStream implements PopableStream<T> {

	final static int BUF_SIZE = 1024 * 1024;
	ByteBuffer buf;
	int M;
	int k;
	InputStream in;
	byte current = 0;
	int pos = 8;
	final static byte[] mask = new byte[] { (byte) 128, 64, 32, 16, 8, 4, 2, 1 };
	int available = 0;
	Class<T> datatype = null;

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
	public GolombInputStream(InputStream in, int k, Class<T> datatype) throws IOException {
		this.M = MathUtil.pow2(k);
		this.k = k;
		this.in = in;
		this.datatype = datatype;
		byte[] tmp = new byte[BUF_SIZE];
		if (in != null)
			this.available = in.read(tmp);
		if (this.available >= 0)
			this.buf = ByteBuffer.wrap(tmp, 0, this.available);
		this.pos = 8; // force byte reload
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
	public Class<T> getDatatype() {
		return this.datatype;
	}

	/**
	 * @return -1 if the stream is number of available golomb numbers.
	 */
	public int available() throws IOException {
		if (this.available < 0) {
			// System.out.println("available: " + available + " pos: " + pos +
			// " current: "
			// + Integer.toBinaryString(current));

			// special case. we are in the last byte that contains possibly
			// another code.
			// if all remaining bits are 0, this is not the case.
			if (pos < 7) {
				for (int i = pos; i < 8; i++) {
					if ((current & mask[i]) == 0) {
						// there is still a 0 (which is the separator between
						// the two golomb sections => there is still at least
						// one encoded
						// value...
						return 1;
					}
				}
			}
			return -1;
		}
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
	public T pop() throws IOException {
		int val = read();
		if (datatype == Byte.class) {
			return datatype.cast((byte) val);
		} else if (datatype == Character.class) {
			return datatype.cast((char) val);
		} else
			return datatype.cast(val);
	}

	@Override
	public int read() throws IOException {
		entityCounter++;
		int q = 0;
		int nr = 0;
		while (readBit())
			q++; // count leading 1s

		for (int a = 0; a < k; a++) {
			if (readBit()) {
				nr += (1 << a);
			} else
				;
		}
		nr += q * M;
		return nr;
	}

	/**
	 * Close this stream
	 */
	public void close() throws IOException {
		in.close();
	}

	@Override
	public String toString() {
		return "[GolombInputStream (k=" + k + ">]";
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		int x = 1000;
		File f = new File("c:/data/bio/sam-testsuite/golo.tmp");

		Random rand = new Random();
		int[] nums = new int[x];
		int k = 1;

		long t = System.currentTimeMillis();
		GolombOutputStream<Integer> o = new GolombOutputStream<Integer>(new FileOutputStream(f), k, Integer.class);
		for (int i = 0; i < x; i++) {
			int r = rand.nextInt(10);
			nums[i] = r;
			o.write(r);
		}
		o.close();
		System.out.println("Wrote " + x + " golomb numbers between 0 and 99 in " + (System.currentTimeMillis() - t)
				+ "ms.");
		System.out.println("Size: " + f.length() + ", uncompressed (int) : " + (x * 4) + ", uncompressed (long) : "
				+ (x * 8));
		t = System.currentTimeMillis();

		GolombInputStream<Integer> in = new GolombInputStream<Integer>(new FileInputStream(f), k, Integer.class);
		int i = 0;
		while (in.available() > 0) {
			System.out.println(i);
			int c = in.pop();
			int r = nums[i];
			if (r != c)
				System.out.println("Error: " + c);
			i++;
			if (i % 1000000 == 0)
				System.out.print(".");
			if (i % 10000000 == 0)
				System.out.println();
		}
		in.close();
		System.out.println("Read " + i + " golomb numbers in " + (System.currentTimeMillis() - t) + "ms.");

	}

}
