package at.cibiv.codoc.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;

/**
 * A block of data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class TripleFileDataOutputBlock<T> extends FileDataOutputBlock<T> {

	File f2 = null;
	Long byteLengthF2 = null;
	OutputStream fout2 = null;

	File f3 = null;
	Long byteLengthF3 = null;
	OutputStream fout3 = null;

	/**
	 * Constructor.
	 * 
	 * @param f
	 * @param conf
	 * @param id
	 * @throws FileNotFoundException
	 * @throws Throwable
	 */
	public TripleFileDataOutputBlock(String id, File f1, File f2, File f3) {
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
	 * @throws CodocException 
	 * @throws Throwable
	 */
	@Override
	@SuppressWarnings("unchecked")
	public TripleFileDataOutputBlock<T> configure(PropertyConfiguration conf) throws FileNotFoundException, CodocException {
		this.fout1 = new BufferedOutputStream(new FileOutputStream(f1));
		this.fout2 = new BufferedOutputStream(new FileOutputStream(f2));
		this.fout3 = new BufferedOutputStream(new FileOutputStream(f3));
		this.stream = (PushableStream<T>) StreamFactory.createTripleOutputStream(fout1, fout2, fout3, conf, id);
		this.compressionMethod = FileDataOutputBlock.compFromString(conf
				.getProperty(id + "-compression", (String) null));
		return this;
	}

	/**
	 * Compresses the block data which will also close all open streams if any.
	 * 
	 * @throws IOException
	 */
	@Override
	public boolean compress() throws IOException {
		final int BUF_SIZE = 1024 * 1024 * 100;
		if (!super.compress())
			return false;

		if ((f2 != null) && (f2.exists()) && (f3 != null) && (f3.exists())) {
			File tmp = null;
			switch (compressionMethod) {
			case GZIP:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".gz");
				FileUtils.gzipFile(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
				tmp = FileUtils.createTempFile(f3.getParentFile(), f3.getName() + ".gz");
				FileUtils.gzipFile(f3, tmp, BUF_SIZE);
				if (!f3.delete())
					return false;
				f3 = tmp;
				break;
			case BZIP2:
				tmp = FileUtils.createTempFile(f2.getParentFile(), f2.getName() + ".bz");
				FileUtils.bzip2File(f2, tmp, BUF_SIZE);
				if (!f2.delete())
					return false;
				f2 = tmp;
				tmp = FileUtils.createTempFile(f3.getParentFile(), f3.getName() + ".bz");
				FileUtils.bzip2File(f3, tmp, BUF_SIZE);
				if (!f3.delete())
					return false;
				f3 = tmp;
				break;
			default:
			}

			byteLengthF2 = f2.length();
			byteLengthF3 = f3.length();
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
		if (fout2 != null)
			fout2.close();
		if (fout3 != null)
			fout3.close();
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
