package at.cibiv.codoc.eval;

import java.io.File;

import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageDecompressor;

public class BlockCountTest {

	public static void contBlocks(File codocFile) throws Throwable {
		
		CoverageDecompressor decomp = CoverageDecompressor.decompress(codocFile, null);
		String blocks = decomp.getCompressedConfig().getProperty(CoverageCompressor.OPT_BLOCK_BORDERS);
		//System.out.println(blocks);
		
		String[] t = blocks.split(",");
		System.out.println("File:\t" + codocFile );
		System.out.println("Found blocks:\t" + (t.length - 1));
		
		//decomp.getBlockIndexTree().dump();

		Integer prejsni = null; String chr = null;
		double sum = 0d, count = 0d;
		for (String x : t) {
			String c = x.split(":")[0];
			Integer meja = Integer.parseInt(x.split(":")[1]);
			if (prejsni != null) {
				if ( chr == null)
					chr = c;
				if (!c.equals(chr))
					break;
				int posc = meja - prejsni;
				sum += posc;
				count++;
				//System.out.println(x + " -> " + posc);
			}
			prejsni = meja;
			chr = c;
		}
		System.out.println("Avg. pos/BIT:\t" + (int) Math.round(sum / count));
		System.out.println("Sum:\t" + (int) Math.round(sum));
		decomp.close();
	}

	/**
	 * @param args
	 * @throws Throwable 
	 */
	public static void main(String[] args) throws Throwable {
		
		//args = new String[]{"c:/data/codoc-eval/RESULTS-BZIP2/sakai_pgm_400bp/sakai_pgm_400bp.bam.g2.comp"};
		
		
		if ( args.length<1)
			throw new RuntimeException("usage: BlockCountTest <codoc-file>");
		long start = System.currentTimeMillis();
		contBlocks(new File(args[0]));
		System.out.println("query took " + (System.currentTimeMillis()-start) +  "ms.");
	}

}
