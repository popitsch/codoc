package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;

import org.broad.igv.variant.vcf.VCFVariant;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.CoverageHit;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.ngs.tools.sam.iterator.ParseException;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;

/**
 * Simple usage examples demonstrating how to use the CODOC API.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class SimpleUsageExamples {

    public static void iterateDOC(File covFile, File covVcfFile) throws CodocException, IOException {

	CoverageDecompressor cov = CoverageDecompressor.loadFromFile(covFile, covVcfFile);
	try {
	    CompressedCoverageIterator it = cov.getCoverageIterator();
	    while (it.hasNext()) {
		Float coverage = it.next();
		GenomicPosition pos = it.getGenomicPosition();
		System.out.println(pos.toString1based() + "\t" + coverage);
	    }
	} finally {
	    cov.close();
	}
    }

    public static void queryDOC(File covFile, File covVcfFile) throws CodocException, IOException {
	CoverageDecompressor cov = CoverageDecompressor.loadFromFile(covFile, covVcfFile);
	try {
	    GenomicPosition qpos = new GenomicPosition("20", 60070, COORD_TYPE.ONEBASED);
	    CoverageHit hit = cov.query(qpos);
	    if (hit != null) {
		System.out.println(qpos.toString1based() + "\t" + hit.getInterpolatedCoverage() + " [" + hit.getLowerBoundary() + "/" + hit.getUpperBoundary()
			+ "]");
	    } else {
		System.out.println("NO RESULT");
	    }
	} finally {
	    cov.close();
	}
    }

    private static void querySNPs(File covFile, File covFile2, File covVcfFile) throws IOException, ParseException, CodocException {
	SimpleVCFFile vcf = new SimpleVCFFile(covVcfFile);
	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(covFile, covVcfFile);
	CoverageDecompressor cov2 = CoverageDecompressor.loadFromFile(covFile2, covVcfFile);
	try {
	    for (SimpleVCFVariant v : vcf.getVariants()) {

		CoverageHit h1 = cov1.query(v.getGenomicPosition());
		int c1 = (h1 != null) ? h1.getRoundedCoverage() : 0;
		CoverageHit h2 = cov1.query(v.getGenomicPosition());
		int c2 = (h2 != null) ? h1.getRoundedCoverage() : 0;
		System.out.println(v.getGenomicPosition().toString1based() + "\t" + v.getRefString() + "/" + v.getAltString() + "\t" + c1 + "\t" + c2);
	    }
	} finally {
	    cov1.close();
	    cov2.close();
	}
    }

    /**
     * @param args
     * @throws IOException
     * @throws CodocException
     * @throws ParseException
     */
    public static void main(String[] args) throws CodocException, IOException, ParseException {
	File covFile = new File("src/test/resources/covcompress/small.compressed");
	File covFile2 = new File("src/test/resources/covcompress/small.compressed");
	File covVcfFile = new File("src/test/resources/covcompress/small.vcf");

	 System.out
	 .println("============================================================================");
	 System.out
	 .println("Iterate over all DOC values (use small CODOC files with this example code only!)");
	 iterateDOC(covFile, covVcfFile);

	System.out.println("============================================================================");
	System.out.println("Query a DOC value");
	queryDOC(covFile, covVcfFile);

	System.out.println("============================================================================");
	System.out.println("Calculate the coverage of a set of SNPs for a couple of CODOC files");
	querySNPs(covFile, covFile2, covVcfFile);

    }

}
