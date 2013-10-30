package at.cibiv.codoc.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import at.cibiv.codoc.utils.FileUtils;


/**
 * A compressed file.
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CompressedFile {

	private FileHeader header;

	public CompressedFile(FileHeader header) {
		this.header = header;
	}

	public FileHeader getHeader() {
		return header;
	}

	public void setHeader(FileHeader header) {
		this.header = header;
	}

	/**
	 * Store to file.
	 * 
	 * @param out
	 * @throws Throwable
	 */
	public void toFile(File fout) throws Throwable {
		List<File> files = new ArrayList<File>();
		files.add(header.getF1());
		for (FileDataOutputBlock<?> b : header.getBlocks()) {
			if (b instanceof TripleFileDataOutputBlock<?>) {
				files.add(b.getF1());
				files.add(((TripleFileDataOutputBlock<?>) b).getF2());
				files.add(((TripleFileDataOutputBlock<?>) b).getF3());
			} else if (b instanceof HeterogenousFileDataOutputBlock<?>) {
				files.add(b.getF1());
				files.add(((HeterogenousFileDataOutputBlock<?>) b).getF2());
			} else {
				files.add(b.getF1());
			}
		}
		// System.out.println("Concatenate: " +
		// Arrays.toString(files.toArray()));
		FileUtils.concatenateFile(fout, false, files);
	}

}
