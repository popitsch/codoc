package at.cibiv.codoc.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;

/**
 * A block of data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class FileDataInputBlock<T> extends AbstractDataBlock {

	/**
	 * List of supported attribute types
	 */
	public static enum SAMAttributeType {
		A, i, f, Z, H, B
	}

	File f1 = null;
	InputStream fin1 = null;
	PopableStream<T> stream = null;
	Long byteLengthF1 = null;
	SAMAttributeType attributeType = null;
	private boolean decompressed;

	/**
	 * Constructor.
	 * 
	 * @param f
	 * @param conf
	 * @param id
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	public FileDataInputBlock(String id, File f1) throws FileNotFoundException, Throwable {
		this.id = id;
		this.f1 = f1;
	}

	/**
	 * Configure this block.
	 * 
	 * @param conf
	 * @return
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	@SuppressWarnings("unchecked")
	public FileDataInputBlock<T> configure(PropertyConfiguration conf) throws FileNotFoundException, Throwable {
		this.fin1 = new BufferedInputStream(new FileInputStream(f1));
		this.stream = (PopableStream<T>) StreamFactory.createInputStream(fin1, conf, id);
		this.compressionMethod = compFromString(conf.getProperty(id + "-compression", (String) null));
		String at = conf.getProperty(id + "-attributetype", (String) null);
		if (at != null) {
			for (SAMAttributeType t : SAMAttributeType.values()) {
				if (t.name().equals(at))
					this.attributeType = t;
			}
		}
		return this;
	}

	/**
	 * Compresses the block data which will also close all open streams if any.
	 * 
	 * @throws IOException
	 */
	public boolean decompress() throws IOException {
		final int BUF_SIZE = 1024 * 1024 * 100;
		if ((f1 != null) && (f1.exists())) {
			File tmp = null;
			switch (compressionMethod) {
			case GZIP:
				tmp = FileUtils.createTempFile(f1.getParentFile(), f1.getName() + ".guz");
				FileUtils.gunzipFile(f1, tmp, BUF_SIZE);
				if (!f1.delete())
					return false;
				f1 = tmp;
				decompressed = true;
				break;
			case BZIP2:
				tmp = FileUtils.createTempFile(f1.getParentFile(), f1.getName() + ".buz");
				FileUtils.bunzip2File(f1, tmp, BUF_SIZE);
				if (!f1.delete())
					return false;
				f1 = tmp;
				decompressed = true;
				break;
			default:
			}
		}
		return true;
	}

	public File getF1() {
		return f1;
	}

	public void setF1(File f1) {
		this.f1 = f1;
	}

	public boolean isDecompressed() {
		return decompressed;
	}

	public void setDecompressed(boolean decompressed) {
		this.decompressed = decompressed;
	}

	public Long getByteLengthF1() {
		if ((byteLengthF1 == null) && (f1 != null))
			byteLengthF1 = f1.length();
		return byteLengthF1;
	}

	public void setByteLengthF1(Long byteLengthF1) {
		this.byteLengthF1 = byteLengthF1;
	}

	public PopableStream<T> getStream() {
		return stream;
	}

	public void setStream(PopableStream<T> stream) {
		this.stream = stream;
	}

	public SAMAttributeType getAttributeType() {
		return attributeType;
	}

	public void close() throws IOException {
		if (stream != null)
			stream.close();
		if (fin1 != null)
			fin1.close();
	}

	/**
	 * Delete the data files
	 * 
	 * @return true if the deletion was successful or false otherwise.
	 */
	public boolean deleteFiles() {
		boolean ret = true;
		if (f1 != null)
			if (f1.exists())
				ret &= f1.delete();
		return ret;
	}

	public String toString() {
		return "[block " + id + "," + getF1() + "," + getByteLengthF1() + ",compressed:" + getCompressionMethod() + "]";
	}

}
