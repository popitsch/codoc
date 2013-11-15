package at.cibiv.codoc.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.MalformedInputException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/**
 * File utilities.
 * 
 * @author popitsch
 * 
 */
public class FileUtils {

	/**
	 * Max buffer size used by this class.
	 */
	final static int MAX_BUF_SIZE = 1024 * 1024 * 100; // buffer size

	/**
	 * Default GZIP buffer size used by this class.
	 */
	public final static int DEF_GZIP_BUF_SIZE = 1024 * 64; // buffer size

	/**
	 * Create a temp file with the passed prefix.
	 * 
	 * @param prefix
	 * @return
	 */
	public static File createTempFile(File workDir, String prefix) {
		String postfix = "";
		File f = null;
		int i = 0;
		do {
			f = new File(workDir, prefix + postfix + i + ".tmp");
			i++;
		} while (f.exists());
		return f;
	}


	/**
	 * Concatenate files.
	 * @param outFile
	 * @param deleteFiles
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public static long[] concatenateFile(File outFile, boolean deleteFiles, Collection<File> in) throws IOException {
		return concatenateFile(outFile, deleteFiles, in.toArray(new File[in.size()]));
	}

	/**
	 * Concatenates an arbitrary number of files into the passed output file
	 * (can also be used for copying though!). Example: took 2min to concat 3 x
	 * 1.5GB into a new file.
	 * 
	 * @param in
	 *            arbitrary list of input file, ordered by their position in the
	 *            resulting file.
	 * @param out
	 *            resulting file.
	 * @param deleteFiles
	 *            deletes the input files if set to true.
	 * @return an array containing the byte offsets of the concatenated data
	 *         blocks in the resulting file.
	 * @throws IOException
	 */
	public static long[] concatenateFile(File outFile, boolean deleteFiles, File... in) throws IOException {
		if (in.length == 0)
			return null;
		long expectedLen = 0L;
		long[] pos = new long[in.length];
		FileInputStream[] instream = new FileInputStream[in.length];
		FileChannel[] inchan = new FileChannel[in.length];
		FileChannel destc = null;
		try {
			destc = new FileOutputStream(outFile).getChannel();

			long offset = 0;
			for (int i = 0; i < in.length; i++) {
				pos[i] = offset;
				instream[i] = new FileInputStream(in[i]);
				inchan[i] = instream[i].getChannel();
				long size = inchan[i].size();

				for (long x = 0; x < size;) {
					x += inchan[i].transferTo(x, size - x, destc);
				}

				offset += size;
				expectedLen += size;
				inchan[i].close();

				// System.out.println("Merged " + in[i] + " with " +
				// in[i].length() + ", pos " + pos[i]);
				//
				//
				if (deleteFiles)
					if (!in[i].delete())
						throw new IOException("Could not remove file " + in[i]);
			}

		} finally {
			for (FileInputStream s : instream)
				if (s != null)
					s.close();
			if (destc != null)
				destc.close();
		}
		if (expectedLen != outFile.length())
			throw new IOException("Expected file length " + expectedLen + " but found " + outFile.length());
		return pos;
	}

	public static List<File> splitFile(File toSplit, File dir, boolean deleteOriginal, List<Long> pos) throws IOException {
		return splitFile(toSplit, dir, deleteOriginal, pos.toArray(new Long[pos.size()]));
	}

	/**
	 * Splits the passed input stream at the passed positions into n randomly
	 * named files.
	 * 
	 * @param out
	 * @param deleteOriginal
	 * @param pos
	 * @return
	 * @throws IOException
	 */
	public static List<File> splitFile(File toSplit, File dir, boolean deleteOriginal, Long... pos) throws IOException {
		FileInputStream fin = null;
		FileOutputStream fout = null;
		ArrayList<File> ret = new ArrayList<File>();
		try {

			fin = new FileInputStream(toSplit);
			FileChannel chan = fin.getChannel();
			long oldpos = 0L;
			long expectedSize = 0L;
			for (long p : pos) {
				expectedSize = p - oldpos;
				File f = new File(dir, Math.random() + ".tmp");
				fout = new FileOutputStream(f);
				FileChannel out = fout.getChannel();
				for (long x = oldpos; x < p;) {
					x += chan.transferTo(x, p - x, out);
				}

				out.close();
				ret.add(f);
				if (f.length() != expectedSize)
					throw new IOException("Error when splitting file! expected size: " + expectedSize + " but was " + f.length());
				oldpos = p;
			}
			;

			// transfer last bit also
			File f = new File(dir, Math.random() + ".tmp");
			fout = new FileOutputStream(f);
			FileChannel out = fout.getChannel();
			expectedSize = toSplit.length() - oldpos;

			for (long x = oldpos; x < toSplit.length();) {
				x += chan.transferTo(x, toSplit.length() - x, out);
			}
			out.close();
			ret.add(f);

			// delete original ?
			if (deleteOriginal)
				toSplit.delete();

			return ret;
		} finally {
			if (fin != null)
				fin.close();
			if (fout != null)
				fout.close();
		}

	}

	/**
	 * gzip a file. Automatic garbage collection each ~500 calls.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public static void gzipFile(File in, File out, int BUF_SIZE) throws IOException {
		FileOutputStream fout = null;
		FileInputStream fin = null;
		try {
			fout = new FileOutputStream(out);
			fin = new FileInputStream(in);
			FileChannel cin = fin.getChannel();

			// System.out.println("GZIP " + in.length() + " buf " + BUF_SIZE);

			/**
			 * Random garbage collect after about 500 stream uses, see
			 * http://bugs.sun.com/view_bug.do?bug_id=5072161
			 */
			if (Math.random() < 0.05) {
				System.gc();
				System.runFinalization();
			}
			
			GZIPOutputStream gout = new GZIPOutputStream(fout, BUF_SIZE, true);
			WritableByteChannel cout = Channels.newChannel(gout);

			for (long x = 0; x < in.length();) {
				x += cin.transferTo(x, MAX_BUF_SIZE, cout);
			}

			cin.close();
			cout.close();
			gout.finish();
			gout.close();
		} finally {
			if (fin != null)
				fin.close();
			if (fout != null)
				fout.close();
		}
	}

	/**
	 * gunzip a file.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public static void gunzipFile(File in, File out, long BUF_SIZE) throws IOException {
		FileOutputStream fout = null;
		FileInputStream fin = null;
		try {
			fout = new FileOutputStream(out);
			fin = new FileInputStream(in);

			FileChannel cout = fout.getChannel();

			GZIPInputStream gin = new GZIPInputStream(fin);
			ReadableByteChannel cin = Channels.newChannel(gin);

			long pos = 0L;
			long transfered = 0L;
			do {
				transfered = cout.transferFrom(cin, pos, BUF_SIZE);
				pos += BUF_SIZE;
			} while (transfered > 0);
		} finally {
			if (fin != null)
				fin.close();
			if (fout != null)
				fout.close();
		}
	}

	/**
	 * gzip a file.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public static void bzip2File(File in, File out, int BUF_SIZE) throws IOException {
		FileOutputStream fout = null;
		FileInputStream fin = null;
		try {
			fout = new FileOutputStream(out);
			fin = new FileInputStream(in);
			FileChannel cin = fin.getChannel();

			/**
			 * Random garbage collect after about 500 stream uses, see
			 * http://bugs.sun.com/view_bug.do?bug_id=5072161
			 */
			if (Math.random() < 0.05) {
				System.gc();
				System.runFinalization();
			}
			
			BZip2CompressorOutputStream gout = new BZip2CompressorOutputStream(fout, 9);
			WritableByteChannel cout = Channels.newChannel(gout);

			for (long x = 0; x < in.length();) {
				x += cin.transferTo(x, MAX_BUF_SIZE, cout);
			}
			cin.close();
			cout.close();
		} finally {
			if (fin != null)
				fin.close();
			if (fout != null)
				fout.close();
		}
	}

	/**
	 * bunzip a file.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public static void bunzip2File(File in, File out, long BUF_SIZE) throws IOException {
		FileOutputStream fout = null;
		FileInputStream fin = null;
		try {
			fout = new FileOutputStream(out);
			fin = new FileInputStream(in);

			FileChannel cout = fout.getChannel();

			BZip2CompressorInputStream gin = new BZip2CompressorInputStream(fin);
			ReadableByteChannel cin = Channels.newChannel(gin);

			long pos = 0L;
			long transfered = 0L;
			do {
				transfered = cout.transferFrom(cin, pos, BUF_SIZE);
				pos += BUF_SIZE;
			} while (transfered > 0);
		} finally {
			if (fin != null)
				fin.close();
			if (fout != null)
				fout.close();
		}
	}

	/**
	 * Automatically detects the number of the used NL bytes (1 or 2).
	 * 
	 * @param f
	 * @return the number of bytes used for representing newlines in the passed
	 *         file.
	 * @throws IOException
	 */
	public static int detectNLBytes(File f) throws IOException {
		if (!f.exists())
			throw new FileNotFoundException("file " + f + " not found!");
		FileInputStream fin = null;
		try {
			fin = new FileInputStream(f);
			FileChannel fc = fin.getChannel();
			MappedByteBuffer buf = null;

			final long MAX_BUF_SIZE = 53687091; // 0.05 GB buffer size =
			// 53687091
			buf = fc.map(FileChannel.MapMode.READ_ONLY, 0L, Math.min(MAX_BUF_SIZE, fc.size()));
			// convert to chars
			Charset charset = Charset.forName("UTF-8");
			CharsetDecoder decoder = charset.newDecoder();
			CharBuffer charBuffer;
			try {
				charBuffer = decoder.decode(buf);
			} catch (MalformedInputException e0) {
				System.err.println("Not UTF-8 encoded?");
				throw e0;
			}
			char[] chars = charBuffer.array();
			for (int i = 0; i < chars.length; i++) {
				char c = chars[i];
				char c2 = ' ';
				if (i + 1 < chars.length)
					c2 = chars[i + 1];
				if (c == '\r') {
					if (c2 == '\n') {
						// System.out.println("\\r\\n");
						return 2;
					}
					// System.out.println("\\r");
					return 1;
				}
				if (c == '\n') {
					if (c2 == '\r') {
						// System.out.println("\\n\\r");
						return 2;
					}
					// System.out.println("\\n");
					return 1;
				}
			}
			System.err.println("Could not determine NL_BYTES of file " + f + " -- no newline found? -- use platform default");
			return System.getProperty("line.separator").length();
		} finally {
			if (fin != null)
				fin.close();
		}
	}

	/**
	 * Deletes a (temporary) file and reports an error to stderr if
	 * unsuccessful.
	 * 
	 * @param files
	 */
	public static void safeDeleteFiles(File... files) {
		for (File f : files)
			if (f != null)
				if (!f.delete()) {
					System.err.println("Possible privacy leak: could probably not delete file " + f + "! delete manually?!");
					f.deleteOnExit();
				}
	}

	/**
	 * Create a temp file with the passed prefix.
	 * 
	 * @param prefix
	 * @return
	 */
	public static File createRandomTempFile(File workDir, String prefix) {
		String postfix = "" + Math.random();
		File f = null;
		int i = 0;
		do {
			f = new File(workDir, prefix + postfix + i + ".tmp");
			i++;
		} while (f.exists());
		return f;
	}

	/**
	 * Debugging.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		File test = new File("null");
		File test2 = new File("null2");
		long start = System.currentTimeMillis();
		for (int i = 0; i < 1000 * 1000; i++) {
			gzipFile(test, test2, 1000);
			if (i % 1000 == 0) {
				Long now = System.currentTimeMillis();
				System.out.println("it " + i + "(" + (double) i/ (double) (now-start) + ")");
			}
		}
		System.out.println("Finished.");
	}

}
