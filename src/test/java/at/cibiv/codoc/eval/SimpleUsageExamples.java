package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.CoverageHit;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * Simple usage examples demonstrating how to use the CODOC API.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class SimpleUsageExamples {

	public static void iterateDOC(File covFile, File covVcfFile)
			throws CodocException, IOException {

		CoverageDecompressor cov = CoverageDecompressor.loadFromFile(covFile,
				covVcfFile);
		CompressedCoverageIterator it = cov.getCoverageIterator();
		while (it.hasNext()) {
			Float coverage = it.next();
			GenomicPosition pos = it.getGenomicPosition();
			System.out.println(pos.toString1based() + "\t" + coverage);
		}
		cov.close();
	}

	public static void queryDOC(File covFile, File covVcfFile)
			throws CodocException, IOException {
		CoverageDecompressor cov = CoverageDecompressor.loadFromFile(covFile,
				covVcfFile);
		GenomicPosition qpos = new GenomicPosition("20", 60000,
				COORD_TYPE.ONEBASED);
		CoverageHit hit = cov.query(qpos);
		if (hit != null) {
			System.out.println(qpos.toString1based() + "\t"
					+ hit.getInterpolatedCoverage() + " ["
					+ hit.getLowerBoundary() + "/" + hit.getUpperBoundary()
					+ "]");
		}
	}

	
	// public static void calcDOCperBED(File covFile, File covVcfFile, File
	// bedFile ) {
	// CoverageTools.calculateCoveragePerBedFeature();
	// }
	
	/**
	 * @param args
	 * @throws IOException
	 * @throws CodocException
	 */
	public static void main(String[] args) throws CodocException, IOException {
		File covFile = new File(
				"src/test/resources/covcompress/small.compressed");
		File covVcfFile = new File("src/test/resources/covcompress/small.vcf");

		System.out
				.println("============================================================================");
		System.out
				.println("Iterate over all DOC values (use small CODOC files with this example code only!)");
		iterateDOC(covFile, covVcfFile);

		System.out
				.println("============================================================================");
		System.out.println("Query a DOC value");
		queryDOC(covFile, covVcfFile);

	}

}
