package at.cibiv.codoc.eval;

import org.junit.Test;

import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageTools;

public class TestCalculateCoverageperBedFeature {

    @Test
    public void test() throws Throwable {
//	String[] args = new String[] { "compress", "-cov", "src/test/resources/covcompress/small.bam", "-qparam",
//		"0", "-o", "src/test/resources/testCalculateCoveragePerBedFeature/small.bam.p0.codoc" };
//	CoverageCompressor.main(args);
	
	String[] args = new String[] { "calculateCoveragePerBedFeature", "-cov", "src/test/resources/testCalculateCoveragePerBedFeature/small.bam.p0.codoc", "-bed",
		"src/test/resources/testCalculateCoveragePerBedFeature/testregions.bed", "-o", "src/test/resources/testCalculateCoveragePerBedFeature/testregions.bed.out" };
	CoverageTools.main(args);
	
    }

}
