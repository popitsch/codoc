package at.cibiv.codoc.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;

/**
 * A block of data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class HeterogenousFileDataInputBlock<T> extends FileDataInputBlock<T> {

	File f2 = null;
	Long byteLengthF2 = null;
	InputStream fin2 = null;

	/**
	 * Constructor.
	 * 
	 * @param f
	 * @param conf
	 * @param id
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	public HeterogenousFileDataInputBlock(String id, File f1, File f2) throws FileNotFoundException, Throwable {
		super(id, f1);
		this.f2 = f2;
	}

	/**
	 * Configure this block.
	 * 
	 * @param conf
	 * @return
	 * @throws FileNotFoundException
	 * @throws CodocException 
	 * @throws Throwable
	 */
	@Override
	@SuppressWarnings("unchecked")
	public HeterogenousFileDataInputBlock<T> configure(PropertyConfiguration conf) throws FileNotFoundException, CodocException {
		this.fin1 = new BufferedInputStream(new FileInputStream(f1));
		this.fin2 = new BufferedInputStream(new FileInputStream(f2));
		this.stream = (PopableStream<T>) StreamFactory.createHeterogenousInputStream(fin1, fin2, conf, id);
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

		if ((f2 != null) && (f2.exists())) {
			File tmp = null;
			switch (compressionMethod) {
			case GZIP:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".guz");
				FileUtils.gunzipFile(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
				break;
			case BZIP2:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".buz");
				FileUtils.bunzip2File(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
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

	public Long getByteLengthF2() {
		if ((byteLengthF2 == null) && (f2 != null))
			byteLengthF2 = f2.length();
		return byteLengthF2;
	}

	public void setByteLengthF2(Long byteLengthF2) {
		this.byteLengthF2 = byteLengthF2;
	}

	@Override
	public void close() throws IOException {
		super.close();
		if (fin2 != null)
			fin2.close();
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
		return ret;
	}

	public String toString() {
		return "[Hblock " + id + "," + getF1() + "," + getByteLengthF1() + "," + getF2() + "," + getByteLengthF2()
				+ "]";
	}

}
