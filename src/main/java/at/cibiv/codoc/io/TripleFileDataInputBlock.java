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
public class TripleFileDataInputBlock<T> extends FileDataInputBlock<T> {

	File f2 = null;
	Long byteLengthF2 = null;
	InputStream fin2 = null;

	File f3 = null;
	Long byteLengthF3 = null;
	InputStream fin3 = null;

	/**
	 * Constructor.
	 * 
	 * @param f
	 * @param conf
	 * @param id
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	public TripleFileDataInputBlock(String id, File f1, File f2, File f3) throws FileNotFoundException, Throwable {
		super(id, f1);
		this.f2 = f2;
		this.f3 = f3;
	}

	/**
	 * Configure this block.
	 * 
	 * @param conf
	 * @return
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	@Override
	@SuppressWarnings("unchecked")
	public TripleFileDataInputBlock<T> configure(PropertyConfiguration conf) throws FileNotFoundException, Throwable {
		this.fin1 = new BufferedInputStream(new FileInputStream(f1));
		this.fin2 = new BufferedInputStream(new FileInputStream(f2));
		this.fin3 = new BufferedInputStream(new FileInputStream(f3));
		this.stream = (PopableStream<T>) StreamFactory.createTripleInputStream(fin1, fin2, fin3, conf, id);
		this.compressionMethod = FileDataOutputBlock.compFromString(conf
				.getProperty(id + "-compression", (String) null));
		return this;
	}

	/**
	 * Compresses the block data which will also close all open streams if any.
	 * 
	 * @throws IOException
	 */
	public boolean decompress() throws IOException {
		final int BUF_SIZE = 1024 * 1024 * 100;
		if (!super.decompress())
			return false;

		if ((f2 != null) && (f2.exists()) && (f3 != null) && (f3.exists())) {
			File tmp = null;
			switch (compressionMethod) {
			case GZIP:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".guz");
				FileUtils.gunzipFile(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
				tmp = FileUtils.createTempFile(f3.getParentFile(), f3.getName() + ".guz");
				FileUtils.gunzipFile(f3, tmp, BUF_SIZE);
				if (!f3.delete())
					return false;
				f3 = tmp;
				break;
			case BZIP2:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".buz");
				FileUtils.bunzip2File(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
				tmp = FileUtils.createTempFile(f3.getParentFile(), f3.getName() + ".buz");
				FileUtils.bunzip2File(f3, tmp, BUF_SIZE);
				if (!f3.delete())
					return false;
				f3 = tmp;
				break;
			default:
			}

		}
		return true;
	}

	public File getF2() {
		return f2;
	}

	public void setF2(File f2) {
		this.f2 = f2;
	}

	public File getF3() {
		return f3;
	}

	public void setF3(File f3) {
		this.f3 = f3;
	}

	public Long getByteLengthF2() {
		if ((byteLengthF2 == null) && (f2 != null))
			byteLengthF2 = f2.length();
		return byteLengthF2;
	}

	public void setByteLengthF2(Long byteLengthF2) {
		this.byteLengthF2 = byteLengthF2;
	}

	public Long getByteLengthF3() {
		if ((byteLengthF3 == null) && (f3 != null))
			byteLengthF3 = f3.length();
		return byteLengthF3;
	}

	public void setByteLengthF3(Long byteLengthF3) {
		this.byteLengthF3 = byteLengthF3;
	}

	@Override
	public void close() throws IOException {
		super.close();
		if (fin2 != null)
			fin2.close();
		if (fin3 != null)
			fin3.close();
	}

	/**
	 * Delete the data files
	 * 
	 * @return true if the deletion was successful or false otherwise.
	 */
	@Override
	public boolean deleteFiles() {
		boolean ret = super.deleteFiles();
		if (f2 != null)
			if (f2.exists())
				ret &= f2.delete();
		if (f3 != null)
			if (f3.exists())
				ret &= f3.delete();
		return ret;
	}

	public String toString() {
		return "[Hblock " + id + "," + getF1() + "," + getByteLengthF1() + "," + getF2() + "," + getByteLengthF2()
				+ "," + getF3() + "," + getByteLengthF3() + "]";
	}

}
