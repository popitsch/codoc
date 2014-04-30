package at.cibiv.codoc.eval;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.input.BoundedInputStream;

import at.cibiv.codoc.io.MixedInputStream;
import at.cibiv.codoc.io.PopableStream;
import at.cibiv.codoc.io.StreamFactory;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.util.MathUtil;

/**
 * Tests a memoryinput stream implementation 
 * @author niko.popitsch@univie.ac.at
 *
 */
@Deprecated
public class MemoryInputStream implements PopableStream<Integer> {

	File inFile = null;
	FileInputStream fin = null;
	InputStream in = null;
	BoundedInputStream bin = null;
	private long entityCounter = 0l;
	byte[] tmp = new byte[4];
	boolean available = false;

	public MemoryInputStream(File inFile, long start, long stop)
			throws FileNotFoundException, IOException {
		this.inFile = inFile;
		this.fin = new FileInputStream(inFile);
		this.fin.skip(start);
		this.bin = new BoundedInputStream(this.fin, stop - start);
		in = new GZIPInputStream(bin);
		available = in.read(tmp) >= 0;
	}

	@Override
	public Integer pop() throws IOException {
		entityCounter++;
		Integer ret = null;
		if (tmp == null)
			throw new IOException("Could not read int! (so far read "
					+ entityCounter + " entities) ");
		ret = Integer.class.cast(MathUtil.byte2int(tmp));

		if (in.read(tmp) < 0)
			available = false;

		return ret;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	@Override
	public int available() throws IOException {
		if (available)
			return 4;
		else
			return -1;
	}

	@Override
	public Class<Integer> getDatatype() {
		return Integer.class;
	}

	@Override
	public Long getDecodedEntities() {
		return entityCounter;
	}

	@Override
	public InputStream getInputStream() {
		return in;
	}

	/**
	 * @param args
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {
		// TODO Auto-generated method stub

		File in = new File("src/test/resources/covcompress/small.compressed");
		// File in = new File(
		// "c:/data/testbams/geuvadis_UCSC_Genome-sorted.bam.p005.CODOC");

		FileInputStream fin = new FileInputStream(in);
		PropertyConfiguration headerconf = PropertyConfiguration
				.fromString("header-type=mixed\nheader-debug=false");
		MixedInputStream sin = (MixedInputStream) StreamFactory
				.createInputStream(fin, headerconf, "header");

		long headerLen = sin.popLong();
		System.out.println("Header bytes: " + headerLen);
		PropertyConfiguration conf = PropertyConfiguration.fromString(sin
				.popString());
		System.out.println(conf);

		long byteOff = headerLen;
		int l = sin.popInteger();
		for (int i = 0; i < l; i++) {
			String id = sin.popString();
			String type = sin.popString();
			if (!type.equals("normal"))
				throw new IOException("Block type not supported: " + type);
			System.out.println(type);
			long bytelen = sin.popLong();
			System.out.println(id + " block: [" + byteOff + "-"
					+ (byteOff + bytelen) + "]");
			byteOff += bytelen;

			if (id.equals("COV1")) {
				long start = byteOff - bytelen;
				long stop = byteOff;
				System.out.println("open block: [" + start + "-" + stop + "]");

				PopableStream<Integer> s = new MemoryInputStream(in, start,
						stop);
				while (s.available() > 0)
					System.out.println(s.pop());
				System.out.println("read " + s.getDecodedEntities());
			}
		}

	}

}
