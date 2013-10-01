package at.cibiv.codoc.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import bgraph.util.FileUtils;
import bgraph.util.PropertyConfiguration;

/**
 * A block of data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class FileDataOutputBlock<T> extends AbstractDataBlock {

	File f1 = null;
	OutputStream fout1 = null;
	PushableStream<T> stream = null;
	Long byteLengthF1 = null;

	/**
	 * Constructor.
	 * 
	 * @param f
	 * @param conf
	 * @param id
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	public FileDataOutputBlock(String id, File f1) throws FileNotFoundException, Throwable {
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
	public FileDataOutputBlock<T> configure(PropertyConfiguration conf) throws FileNotFoundException, Throwable {
		this.fout1 = new BufferedOutputStream(new FileOutputStream(f1));
		this.stream = (PushableStream<T>) StreamFactory.createOutputStream(fout1, conf, id);
		this.compressionMethod = compFromString(conf.getProperty(id + "-compression", (String) null));
		return this;
	}

	/**
	 * Compresses the block data which will also close all open streams if any.
	 * 
	 * @throws IOException
	 */
	public boolean compress() throws IOException {
		final int BUF_SIZE = 1024 * 1024 * 100;
		close();
		if ((f1 != null) && (f1.exists())) {
			File tmp = null;
			switch (compressionMethod) {
			case GZIP:
				tmp = FileUtils.createTempFile(f1.getParentFile(), f1.getName() + ".gz");
				FileUtils.gzipFile(f1, tmp, BUF_SIZE);
				if (!f1.delete())
					return false;
				f1 = tmp;
				break;
			case BZIP2:
				tmp = FileUtils.createTempFile(f1.getParentFile(), f1.getName() + ".bz");
				FileUtils.bzip2File(f1, tmp, BUF_SIZE);
				if (!f1.delete())
					return false;
				f1 = tmp;
				break;
			default:
			}
			byteLengthF1 = f1.length();
		}
		return true;
	}

	public File getF1() {
		return f1;
	}

	public void setF1(File f1) {
		this.f1 = f1;
	}

	public Long getByteLengthF1() {
		if ((byteLengthF1 == null) && (f1 != null))
			byteLengthF1 = f1.length();
		return byteLengthF1;
	}

	public void setByteLengthF1(Long byteLengthF1) {
		this.byteLengthF1 = byteLengthF1;
	}

	public PushableStream<T> getStream() {
		return stream;
	}

	public void close() throws IOException {
		if (stream != null)
			stream.close();
		if (fout1 != null)
			fout1.close();
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
		return "[block " + id + "," + getF1() + ", len:" + getByteLengthF1() + ",compression:" + getCompressionMethod()
				+ "]";
	}

}
