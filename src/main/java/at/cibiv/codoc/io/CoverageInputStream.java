package at.cibiv.codoc.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.utils.CodocException;

/**
 * InputStream Wrapper for a compressed coverage iterator.
 * 
 * @author niko
 *
 */
public class CoverageInputStream extends InputStream {

	private CompressedCoverageIterator it;

	public CoverageInputStream(CompressedCoverageIterator it) {
		this.it = it;
	}

	@Override
	public int read() throws IOException {
		if (!it.hasNext())
			return -1;
		return it.nextCoverage();
	}

	public static void main(String[] args) throws CodocException, IOException {
		CoverageDecompressor dc = CoverageDecompressor.decompress(new File("src/test/resources/covcompress/small.compressed"), null);
		CompressedCoverageIterator cci = dc.getCoverageIterator();
		while (cci.hasNext()) {
			System.out.print(cci.nextCoverage());
		}
		System.out.println("Finished.");

		cci = dc.getCoverageIterator();
		CoverageInputStream ci = new CoverageInputStream(cci);
		int i;
		while ((i = ci.read()) != -1)
			System.out.print(i);

		System.out.println("Finished.");
	}
}
