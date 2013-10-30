package at.cibiv.codoc.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.util.DebugUtil;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

public class RandomAccessTest {
	
	static Random rand = new Random();
	
	public static GenomicPosition getRandomPosition( CoverageDecompressor decomp ) {
		List<String> chrom = decomp.getChromosomes();
		int cid = rand.nextInt( chrom.size() );
		String chr = chrom.get(cid);
		Long cl = decomp.getChromosomeLength(chr);
		if ( cl == null )
			throw new RuntimeException("Could not estimate length of chr " + chr );
		int pos = rand.nextInt(cl.intValue());
		return new GenomicPosition(chr, pos, COORD_TYPE.ZEROBASED);
	}
	

	/**
	 * @param args
	 * @throws Throwable 
	 */
	public static void main(String[] args) throws Throwable {
		
		//args = new String[] { "src/test/resources/covcompress/small.compressed", "/project/ngs-work/meta/reference/genomes//hg19_human/hg19.fa.chrSizes" };
		
		if ( args.length < 1 )
			throw new RuntimeException("usage: RandomAccessTest <covFile> <chrlenFile> <n> [<vcfFile>]");
		File covFile = new File(args[0]);
		File chrlenFile = new File(args[1]);
		int n = Integer.parseInt(args[2]);
		File vcfFile = (args.length > 3)?new File(args[3]):null;
		
		DebugUtil.addPerformanceMarker("query", "start");
		PropertyConfiguration conf = CoverageDecompressor.getDefaultConfiguration();
		conf.setProperty(CoverageDecompressor.OPT_COV_FILE, covFile.getAbsolutePath());
		if (vcfFile != null)
			conf.setProperty(CoverageDecompressor.OPT_VCF_FILE, vcfFile.getAbsolutePath());
		conf.setProperty(CoverageDecompressor.OPT_CHR_LEN_FILE, chrlenFile.getAbsolutePath());
		conf.setProperty( CoverageDecompressor.OPT_NO_CACHING, "true" );
		CoverageDecompressor decomp = new CoverageDecompressor(conf);
		DebugUtil.addPerformanceMarker("query", "instantiate");
		
		// create n valid random positions
		List<GenomicPosition> pos = new ArrayList<GenomicPosition>();
		for ( int i = 0; i < n; i++ )	
			pos.add(getRandomPosition(decomp));
		
		// query and measure time
		DebugUtil.addPerformanceMarker("query", "createRandomPos");
		for ( int i = 0; i < n; i++ ) {
			//System.out.println(pos.get(i));
			decomp.query(pos.get(i));
		}
		DebugUtil.addPerformanceMarker("query", "query");
		
		// create n valid random positions from a random chrom
		pos = new ArrayList<GenomicPosition>();
		String chr = decomp.getChromosomes().get(0);
		for ( int i = 0; i < n; i++ )	
			pos.add(new GenomicPosition(chr, i));
		decomp.query(pos.get(n-1));
		
		DebugUtil.addPerformanceMarker("query", "createRandomPos2");
		for ( int i = 0; i < n; i++ ) {
			//System.out.println(pos.get(i));
			decomp.query(pos.get(i));
		}
		DebugUtil.addPerformanceMarker("query", "query2");
		DebugUtil.printPerformanceMarker("query");		
	}

}
