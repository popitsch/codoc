package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageCompressor.QUANT_METHOD;
import at.cibiv.codoc.CoverageCompressor.STANDARD_STREAM;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.util.DebugUtil;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

public class PerformanceDecompressorTest {

	String bam = "c:/data/testbams/bigbam.bam";

	// String bam = "c:/data/testbams/small.bam";

	/**
	 * Create a config string for some standard stream.
	 * 
	 * @param s
	 * @param namevaluepairs
	 * @return
	 */
	protected static String createConfigString(STANDARD_STREAM id, String... namevaluepairs) {
		return createConfigString(id.name(), namevaluepairs);
	}

	/**
	 * Create a config string for some standard stream.
	 * 
	 * @param s
	 * @param namevaluepairs
	 * @return
	 */
	protected static String createConfigString(String id, String... namevaluepairs) {
		StringBuilder sb = new StringBuilder();
		for (String nv : namevaluepairs)
			sb.append(id + "-" + nv + "\n");
		return sb.toString();
	}

	/**
	 * Default configuration
	 * 
	 * @return
	 * @throws IOException
	 */
	public static PropertyConfiguration getAlternativeConfiguration() {
		StringBuffer sb = new StringBuffer();

		// chr
		sb.append(createConfigString(STANDARD_STREAM.CHR,
				"type=rle", "datatype=string", "lengthtype=integer",
				"debug=false", "compression=" + BLOCK_COMPRESSION_METHOD.GZIP));

		// pos
		sb.append(createConfigString(STANDARD_STREAM.POS,
				"type=rle", "datatype=integer", "lengthtype=byte",
				"debug=false", "compression=" + BLOCK_COMPRESSION_METHOD.GZIP));

		// cov1
		sb.append(createConfigString(STANDARD_STREAM.COV1,
				"type=standard", "datatype=integer", "lengthtype=byte",
				"debug=false", "compression=" + BLOCK_COMPRESSION_METHOD.GZIP));

		// cov2
		sb.append(createConfigString(STANDARD_STREAM.COV2,
				"type=standard", "datatype=integer", "lengthtype=byte",
				"debug=false", "compression=" + BLOCK_COMPRESSION_METHOD.GZIP));

		return PropertyConfiguration.fromString(sb.toString());
	}

	@Test
	public void compareConfigurations() throws Throwable {

		float[] params = new float[] { 0f, 0.05f, 0.1f, 0.2f };
		//float[] params = new float[] { 0.1f };
		
		for (float p : params) {

			System.out
					.println("-----------------------------------------------------------");
			System.out.println("param: " + p);

			DebugUtil.addPerformanceMarker("test", "start");

			PropertyConfiguration config2 = getAlternativeConfiguration();
			config2.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, bam);
			config2.setProperty(CoverageCompressor.OPT_OUT_FILE, bam
					+ ".codoc2");
			config2.setProperty(CoverageCompressor.OPT_BAM_FILTER,
					"FLAGS^^1024;FLAGS^^512");
			config2.setProperty(CoverageCompressor.OPT_QUANT_METHOD,
					QUANT_METHOD.PERC.name());
			config2.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "" + p);
			config2.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config2.setProperty(CoverageCompressor.OPT_VERBOSE, "false");

			CoverageCompressor comp2 = new CoverageCompressor(config2);

			DebugUtil.addPerformanceMarker("test", "compress alternative");

			CoverageDecompressor decomp2 = CoverageDecompressor.decompress(new File(bam + ".codoc2"), null);
			decomp2.query(new GenomicPosition("chr10", 200000, COORD_TYPE.ONEBASED ));
			decomp2.close();

			DebugUtil.addPerformanceMarker("test", "decompress alternative");

			PropertyConfiguration config = CoverageCompressor
					.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, bam);
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bam + ".codoc");
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER,
					"FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD,
					QUANT_METHOD.PERC.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "" + p);
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "false");

			CoverageCompressor comp = new CoverageCompressor(config);

			DebugUtil.addPerformanceMarker("test", "compress standard");

			CoverageDecompressor decomp = CoverageDecompressor.decompress(new File(bam + ".codoc"), null);
			decomp.query(new GenomicPosition("chr10", 200000, COORD_TYPE.ONEBASED ));
			decomp.close();
			
			DebugUtil.addPerformanceMarker("test", "decompress standard");

			DebugUtil.printPerformanceMarker("test");
			System.out.println("Size of " + bam + ".codoc2" + ": "
					+ (new File(bam + ".codoc2")).length());
			System.out.println("Size of " + bam + ".codoc" + ": "
					+ (new File(bam + ".codoc")).length());
		}

	}

}
