package at.cibiv.codoc.eval;

import java.io.File;
import java.util.SortedSet;

import junit.framework.Assert;

import org.junit.Test;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageCompressor.QUANT_METHOD;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.CoverageHit;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;

public class DecompressorTest {

	String bam = "src/test/resources/covcompress/small.bam";
	String vcf = "src/test/resources/covcompress/small.vcf";
	String comp = "src/test/resources/covcompress/small.compressed";
	String lenFile = "src/test/resources/covcompress/hg19.fa.chrLen";
	String roiFile = "src/test/resources/covcompress/small.roi";

	public void prepare(boolean useRoi) throws Throwable {
		// compress file
		PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
		config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, bam);
		config.setProperty(CoverageCompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageCompressor.OPT_OUT_FILE, comp);
		config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
		config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
		config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.2");
		config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
		config.setProperty(CoverageCompressor.OPT_BLOCKSIZE, "10");
		if (useRoi)
			config.setProperty(CoverageCompressor.OPT_BED_FILE, roiFile);

		CoverageCompressor comp = new CoverageCompressor(config);
	}

	@Test
	public void queryTest() throws Throwable {

		// decompress w/o rois
		prepare(false);

		// query
		PropertyConfiguration config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);

		// check chrom ranges - outside null should be returned
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 0, COORD_TYPE.ONEBASED)));
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 63025521, COORD_TYPE.ONEBASED)));

		// check alignment borders
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 59990, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 59991, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 60093, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 60094, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("21", 60028, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60029, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		// check deletion!
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60015, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertEquals(128f, decompressor.query(new GenomicPosition("20", 60022, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		// check VCF knot points
		SimpleVCFFile vcfFile = new SimpleVCFFile(new File(vcf));
		for (SimpleVCFVariant v : vcfFile.getVariants()) {
			//System.out.println("CHECK " + v + "\n(coverage: " + v.estimateCoverage() + ")");
			if (v.estimateCoverage() != null)
				Assert.assertEquals((float) v.estimateCoverage(),
						decompressor.query(new GenomicPosition(v.getChromosomeOrig(), v.getPosition(), COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		}

		decompressor.close();

	}

	@Test
	public void queryTestRoi() throws Throwable {
		// decompress with roi
		prepare(true);

		// query
		PropertyConfiguration config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);

		// check chrom ranges - outside null should be returned
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 0, COORD_TYPE.ONEBASED)));
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 63025521, COORD_TYPE.ONEBASED)));

		
		CompressedCoverageIterator it = decompressor.getCoverageIterator();
		while ( it.hasNext() ) {
			int cov = it.nextCoverage();
			System.out.println(it.getGenomicPosition().toString1basedOrig() + "->" + cov);
		}
		System.out.println( decompressor.query(new GenomicPosition("21", 60081, COORD_TYPE.ONEBASED) )  );
		
		// check ROI borders BED (0-bases!)
		// chr20 60005 60080 reg1
		// chr21 60015 60017 reg2
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 60004, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(29f, decompressor.query(new GenomicPosition("20", 60005, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(6f, decompressor.query(new GenomicPosition("20", 60081, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 60082, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60014, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		// check deletion
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60015, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("21", 60018, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60019, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());


		decompressor.close();

	}

	@Test
	public void compareWithIteratorTest() throws Throwable {
		// decompress with roi
		prepare(false);

		// query
		PropertyConfiguration config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);

		CompressedCoverageIterator it = decompressor.getCoverageIterator();
		while (it.hasNext()) {
			CoverageHit hitIterator = it.next();
			GenomicPosition posIterator = it.getGenomicPosition();

			CoverageHit hitQuery = decompressor.query(posIterator);
			
			//System.out.println(posIterator.toString1basedOrig() + " ->  " + hitIterator.getInterpolatedCoverage() + "/" +  hitQuery.getInterpolatedCoverage());

			Assert.assertEquals(hitQuery.getInterpolatedCoverage(), hitIterator.getInterpolatedCoverage() );

		}
	}

	@Test
	public void toBedTest() throws Throwable {
		// decompress with roi
		prepare(false);
		// config
		PropertyConfiguration config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);
		GenomicITree tree = decompressor.toBED(null, 1, null, "test", "test", "test");
		//tree.dump();
		CompressedCoverageIterator it = decompressor.getCoverageIterator();
		while (it.hasNext()) {
			CoverageHit hitIterator = it.next();
			SortedSet<? extends GenomicInterval> res = tree.query(it.getGenomicPosition());
			//System.out.println(it.getGenomicPosition().toString1based() + "/" + hitIterator.getRoundedCoverage());
			Assert.assertNotNull(res);
			if (hitIterator.getRoundedCoverage() > 0)
				Assert.assertTrue(res.size() > 0);
		}

	}

	@Test
	public void toBedTest2() throws Throwable {
		// prepare data
		prepare(false);
		// config
		PropertyConfiguration config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);
		GenomicITree tree = decompressor.toBED(null, null, 0, "test", "test", "test");
//		tree.dump();
		CompressedCoverageIterator it = decompressor.getCoverageIterator();
		while (it.hasNext()) {
			CoverageHit hitIterator = it.next();
			SortedSet<? extends GenomicInterval> res = tree.query(it.getGenomicPosition());
//			System.out.println(it.getGenomicPosition() + "/" + res);
			if (hitIterator.getRoundedCoverage() > 0)
				Assert.assertTrue(res == null || res.size() == 0);
		}

	}
	
	@Test
	public void scalingTest() throws Throwable {
		// prepare data
		prepare(false);
		
		float scale = 2.1f;

		// query1
		PropertyConfiguration config1 = CoverageDecompressor.getDefaultConfiguration();
		config1.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config1.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config1.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor1 = new CoverageDecompressor(config1);

		// query1
		PropertyConfiguration config2 = CoverageDecompressor.getDefaultConfiguration();
		config2.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config2.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config2.setProperty(CoverageDecompressor.OPT_SCALE_FACTOR, scale+"");
		config2.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor2 = new CoverageDecompressor(config2);
		
		CompressedCoverageIterator it1 = decompressor1.getCoverageIterator();
		CompressedCoverageIterator it2 = decompressor2.getCoverageIterator();
		while (it1.hasNext()) {
			CoverageHit hit1 = it1.next();
			CoverageHit hit2 = it2.next();

			Assert.assertEquals(hit1.getInterpolatedCoverage() * scale, hit2.getInterpolatedCoverage());
			Assert.assertEquals(hit1.getInterpolatedCoverage(), hit2.getInterpolatedCoverageRaw());
			Assert.assertEquals(hit1.getLowerBoundary() * scale, hit2.getLowerBoundary());
			Assert.assertEquals(hit1.getUpperBoundary() * scale, hit2.getUpperBoundary());
			Assert.assertEquals(hit1.getLowerBoundary(), hit2.getLowerBoundaryRaw());
			Assert.assertEquals(hit1.getUpperBoundary(), hit2.getUpperBoundaryRaw());
		}
	}

	
	@Test
	public void queryTestStrandParam() throws Throwable {

		// compress special
		PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
		config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, bam);
		config.setProperty(CoverageCompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageCompressor.OPT_OUT_FILE, comp);
		config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512;STRAND=+");
		config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
		config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0");
		config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
		new CoverageCompressor(config);
		
		// query
		config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);

		// check chrom ranges - outside null should be returned
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 0, COORD_TYPE.ONEBASED)));
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 63025521, COORD_TYPE.ONEBASED)));
		
		// check alignment borders
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 59991, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 59999, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(27f, decompressor.query(new GenomicPosition("20", 60000, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(24f, decompressor.query(new GenomicPosition("20", 60027, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 60092, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("20", 60093, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("21", 60028, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60029, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		// check deletion!
		Assert.assertEquals(0f, decompressor.query(new GenomicPosition("21", 60015, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		// check VCF knot points
		SimpleVCFFile vcfFile = new SimpleVCFFile(new File(vcf));
		for (SimpleVCFVariant v : vcfFile.getVariants()) {
			//System.out.println("CHECK " + v + "\n(coverage: " + v.estimateCoverage() + ")");
			if (v.estimateCoverage() != null)
				Assert.assertEquals((float) v.estimateCoverage(),
						decompressor.query(new GenomicPosition(v.getChromosomeOrig(), v.getPosition(), COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		}

		decompressor.close();

	}

	@Test
	public void queryTestFOPStrandParam() throws Throwable {

		// compress special
		PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
		config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, bam);
		config.setProperty(CoverageCompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageCompressor.OPT_OUT_FILE, comp);
		config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512;FOPSTRAND=-");
		config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
		config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0");
		config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
		new CoverageCompressor(config);
		
		// query
		config = CoverageDecompressor.getDefaultConfiguration();
		config.setProperty(CoverageDecompressor.OPT_COV_FILE, comp);
		config.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcf);
		config.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, lenFile);
		CoverageDecompressor decompressor = new CoverageDecompressor(config);

		// check chrom ranges - outside null should be returned
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 0, COORD_TYPE.ONEBASED)));
		Assert.assertNull(decompressor.query(new GenomicPosition("20", 63025521, COORD_TYPE.ONEBASED)));
		
		// check alignment borders
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 59991, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(3f, decompressor.query(new GenomicPosition("20", 59999, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(15f, decompressor.query(new GenomicPosition("20", 60000, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(12f, decompressor.query(new GenomicPosition("20", 60027, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(2f, decompressor.query(new GenomicPosition("20", 60092, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		Assert.assertEquals(1f, decompressor.query(new GenomicPosition("20", 60093, COORD_TYPE.ONEBASED)).getInterpolatedCoverage());

		Assert.assertNotNull( decompressor.query(new GenomicPosition("21", 60028, COORD_TYPE.ONEBASED)));

		// check VCF knot points
		SimpleVCFFile vcfFile = new SimpleVCFFile(new File(vcf));
		for (SimpleVCFVariant v : vcfFile.getVariants()) {
			//System.out.println("CHECK " + v + "\n(coverage: " + v.estimateCoverage() + ")");
			if (v.estimateCoverage() != null)
				Assert.assertEquals((float) v.estimateCoverage(),
						decompressor.query(new GenomicPosition(v.getChromosomeOrig(), v.getPosition(), COORD_TYPE.ONEBASED)).getInterpolatedCoverage());
		}

		decompressor.close();

	}


}
