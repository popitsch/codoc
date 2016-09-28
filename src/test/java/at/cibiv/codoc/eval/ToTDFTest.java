package at.cibiv.codoc.eval;

import at.cibiv.codoc.Main;


public class ToTDFTest {

	public static void main(String[] args) throws Throwable {
		String bam = "src/test/resources/totdf/small.bam";
		String roi = "src/test/resources/totdf/small.roi2.bed";
		String codoc = "src/test/resources/totdf/small.codoc";
		String wig = "src/test/resources/totdf/small.wig";
		String tdf = "src/test/resources/totdf/small.tdf";
		String chrlen = "src/test/resources/totdf/hs37d5.fa.chrSizes.chrom.sizes";
		
		
	
		
		Main.main(new String[] {"compress", "-cov", bam, "-roi", roi, "-o", codoc });
//		Main.main(new String[] {"compress", "-cov", bam,  "-o", codoc });

		Main.main(new String[] {"decompress", "towig", "-cov", codoc, "-o", wig });
//
		Main.main(new String[] {"decompress", "totdf", "-cov", codoc, "-chrLen", chrlen, "-o", tdf });
		
		System.out.println("All done.");
	}

}
