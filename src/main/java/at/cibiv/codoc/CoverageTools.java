package at.cibiv.codoc;

import java.io.File;
import java.io.PrintStream;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.SortedSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import at.cibiv.codoc.CoverageCompressor.QUANT_METHOD;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant.ZYGOSITY;

/**
 * Various DOC tools.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class CoverageTools {

	public static boolean debug = true;
	public static final String CMD = "tools";
	public static final String CMD_INFO = "Various DOC tools.";

	/**
	 * Dump the iterator to stdout
	 * 
	 * @param covTFile
	 * @throws Throwable
	 */
	public static void dumpIterator(File cov1File, File cov1VcfFile) throws Throwable {
		if (debug)
			System.out.println("Load " + cov1File);
		CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);
		CompressedCoverageIterator it1 = cov1.getCoverageIterator();
		while (it1.hasNext()) {
			CoverageHit hit1 = it1.next();
			GenomicPosition pos = it1.getGenomicPosition();
			System.out.println(pos.getChromosomeOriginal() + "\t" + pos.get1Position() + "\t" + hit1.getInterpolatedCoverage());
		}
		cov1.close();
	}

	/**
	 * Extract variants that are only in the tumor sample or that changed from
	 * HET-> HOM
	 * 
	 * @param covTFile
	 * @param vcfTFile
	 * @param covNFile
	 * @param vcfNFile
	 * @param vcfOutFile
	 * @param minCoverage
	 * @param normalAlsoFile
	 * @param lowCoverageFile
	 * @throws Throwable
	 */
	public static void cancerNormalFilter(File covTFile, File vcfTFile, File covNFile, File vcfNFile, File vcfOutFile, int minCoverage, File normalAlsoFile,
			File lowCoverageFile) throws Throwable {

		if (debug)
			System.out.println("Load " + covTFile);
		CoverageDecompressor covT = CoverageDecompressor.loadFromFile(covTFile, vcfTFile);
		if (covT.getVcfFile() == null)
			throw new CodocException("Tumor coverage file had not VCF file set.");

		if (debug)
			System.out.println("Load " + covNFile);
		CoverageDecompressor covN = CoverageDecompressor.loadFromFile(covNFile, vcfNFile);
		if (covN.getVcfFile() == null)
			throw new CodocException("Normal coverage file had not VCF file set.");

		if (debug)
			System.out.println("Prepare vcf files");
		SimpleVCFFile vcfT = new SimpleVCFFile(covT.getVcfFile(), true);
		SimpleVCFFile vcfN = new SimpleVCFFile(covN.getVcfFile());
		GenomicITree vcfNItree = vcfN.getITree();

		PrintStream out = new PrintStream(vcfOutFile);
		PrintStream normalAlso = (normalAlsoFile == null) ? null : new PrintStream(normalAlsoFile);
		PrintStream lowCoverage = (lowCoverageFile == null) ? null : new PrintStream(lowCoverageFile);

		out.print(vcfT.getHeader());
		if (normalAlso != null)
			normalAlso.print(vcfT.getHeader());
		if (lowCoverage != null)
			lowCoverage.print(vcfT.getHeader());

		if (debug)
			System.out.println("Start");
		int c = 0;
		for (SimpleVCFVariant v : vcfT.getVariants()) {
			SortedSet<? extends GenomicInterval> res = vcfNItree.query(v.getGenomicPosition());
			boolean accept = true;

			if ((res != null) && (res.size() > 0)) {
				accept = false;

				// variant was found in normal. check whether it was HET in
				// normal and hom in cancer and accept if yes
				if (v.estimateZygosity() == ZYGOSITY.HOM) {

					for (GenomicInterval gi : res) {
						SimpleVCFVariant nv = (SimpleVCFVariant) gi.getAnnotation(SimpleVCFFile.ANNO_VARIANT);

						if ((nv != null && (nv.estimateZygosity() == ZYGOSITY.HET)))
							accept = true;
					}
				}
				// write unaccepted to normalonly file.
				if (!accept)
					if (normalAlso != null)
						normalAlso.println(v);
			}

			if (accept) {
				// variant was accepted. check coverage
				int coverage = 0;
				CoverageHit hit = covN.query(v.getGenomicPosition());
				if (hit != null)
					coverage = Math.round(hit.getInterpolatedCoverage());
				if (coverage >= minCoverage)
					out.println(v);
				else {
					if (lowCoverage != null)
						lowCoverage.println(v);
				}
			}
			c++;
			if (c % 10000 == 0)
				System.out.print(".");
		}
		covT.close();
		covN.close();
		if (debug)
			System.out.println("Finished.");
	}

	public static enum OPERATOR {
		ADD, SUBTRACT, DIFF, MIN, MAX
	};

	/**
	 * Linear combination of coverage signals.
	 * @param cov1File
	 * @param cov1VcfFile
	 * @param cov2File
	 * @param cov2VcfFile
	 * @param op
	 * @param covOut
	 * @throws Throwable
	 */
	public static void combineCoverageFiles(File cov1File, File cov1VcfFile, File cov2File, File cov2VcfFile, OPERATOR op, File covOut) throws Throwable {
		CoverageDecompressor cov1 = null, cov2 = null;
		PrintStream out = null;
		File temp = null;

		try {
			if (debug)
				System.out.println("Load " + cov1File);
			cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);

			if (debug)
				System.out.println("Load " + cov2File);
			cov2 = CoverageDecompressor.loadFromFile(cov2File, cov2VcfFile);

			temp = FileUtils.createRandomTempFile(covOut.getParentFile(), "delme");
			out = new PrintStream(temp);

			SyncedCompressedCoverageIterator it = new SyncedCompressedCoverageIterator(cov1, cov2);
			int count = 0;
			while (it.hasNext()) {
				GenomicPosition pos = it.next();
				CoverageHit hit1 = it.getCoverage1();
				CoverageHit hit2 = it.getCoverage2();
				float c1 = hit1.getInterpolatedCoverage();
				float c2 = hit2.getInterpolatedCoverage();
				float co = 0;
				switch (op) {
				case ADD:
					co = c1 + c2;
					break;
				case SUBTRACT:
					co = c1 - c2;
					break;
				case DIFF:
					co = Math.abs(c1 - c2);
					break;
				case MIN:
					co = Math.min(c1, c2);
					break;
				case MAX:
					co = Math.max(c1, c2);
					break;
				default:
					throw new InvalidParameterException("Unknown operator " + op);
				}
				out.println(pos.getChromosomeOriginal() + "\t" + pos.get1Position() + "\t" + co);
				count++;
			}
			out.close();
			out = null;

			System.out.println("Wrote " + temp);

			// compress
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, temp.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, covOut.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "" + debug);
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0");
			CoverageCompressor compressor = new CoverageCompressor(config);
			compressor.dumpWarnings();

			if (debug)
				switch (op) {
				case ADD:
					System.out.println("Added " + count + " values");
					break;
				case SUBTRACT:
					System.out.println("Subtracted " + count + " values");
					break;
				case DIFF:
					System.out.println("Diffed " + count + " values");
					break;
				case MIN:
					System.out.println("Calculated minimum for " + count + " values");
					break;
				case MAX:
					System.out.println("Calculated maximum for " + count + " values");
					break;
				}

		} finally {
			if (cov1 != null)
				cov1.close();
			if (cov2 != null)
				cov2.close();

			if (out != null)
				out.close();
			if (temp != null)
				temp.delete();
		}
		if (debug)
			System.out.println("Finished.");
	}

	/**
	 * Cross-correlation of coverage signals.
	 * @param cov1File
	 * @param cov1VcfFile
	 * @param cov2File
	 * @param cov2VcfFile
	 * @param corrOut
	 * @throws Throwable
	 */
	public static void correlateCoverageFiles(File cov1File, File cov1VcfFile, File cov2File, File cov2VcfFile, PrintStream corrOut) throws Throwable {
		CoverageDecompressor cov1 = null, cov2 = null;

		try {
			if (debug)
				System.out.println("Load " + cov1File);
			cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);

			if (debug)
				System.out.println("Load " + cov2File);
			cov2 = CoverageDecompressor.loadFromFile(cov2File, cov2VcfFile);

			SyncedCompressedCoverageIterator it = new SyncedCompressedCoverageIterator(cov1, cov2);
			double sumX = 0d, sumY = 0d, sumXY = 0d, N = 0d;
			while (it.hasNext()) {
				it.next();
				CoverageHit hit1 = it.getCoverage1();
				CoverageHit hit2 = it.getCoverage2();
				float c1 = hit1.getInterpolatedCoverage();
				float c2 = hit2.getInterpolatedCoverage();
				sumX += c1;
				sumY += c2;
				sumXY += c1 * c2;
				N++;
			}
			double meanX = sumX / N;
			double meanY = sumY / N;
			cov1.close();
			cov2.close();

			it = new SyncedCompressedCoverageIterator(cov1, cov2);

			double stddevX = 0d, stddevY = 0d;
			while (it.hasNext()) {
				it.next();
				CoverageHit hit1 = it.getCoverage1();
				CoverageHit hit2 = it.getCoverage2();
				float c1 = hit1.getInterpolatedCoverage();
				float c2 = hit2.getInterpolatedCoverage();
				stddevX += (c1 - meanX) * (c1 - meanX);
				stddevY += (c2 - meanY) * (c2 - meanY);
			}
			stddevX = Math.sqrt(stddevX / (N - 1));
			stddevY = Math.sqrt(stddevY / (N - 1));

			double r = (1d / (N - 1d)) * (sumXY - N * meanX * meanY) / (stddevX * stddevY);

			corrOut.println("N\t" + N);
			corrOut.println("sumX\t" + sumX);
			corrOut.println("sumY\t" + sumY);
			corrOut.println("sumXY\t" + sumXY);
			corrOut.println("meanX\t" + meanX);
			corrOut.println("meanY\t" + meanY);
			corrOut.println("stddevX\t" + stddevX);
			corrOut.println("stddevY\t" + stddevY);
			corrOut.println("corr r\t" + r);

		} finally {
			if (cov1 != null)
				cov1.close();
			if (cov2 != null)
				cov2.close();
		}
		if (debug)
			System.out.println("Finished.");

	}

	/**
	 * Print usage information.
	 * 
	 * @param options
	 */
	private static void usage(Options options, String subcommand, String e) {

		if (subcommand == null) {
			System.out.println("Usage:\t\tjava -jar x.jar " + CoverageTools.class + " <command> [options]:\t");
			System.out.println();
			System.out.println("Command:\tdumpCoverage\tDump a coverage iterator to stdout.");
			System.out
					.println("Command:\tcancerNormalFilter\tExtract all variants that occur exclusively in the tumor sample (with sufficient coverage in the normal) and the ones that changed their genotype from HET to HOM.");
			System.out.println("Command:\tcombineCoverageFiles\tCombine the coverage values from two coverage iterators.");
			System.out.println("Command:\tcorrelateCoverageFiles\tCalculate the cross-correlation of two coverage signals.");
			System.out.println();
		} else {

			HelpFormatter hf = new HelpFormatter();
			hf.setLeftPadding(10);
			hf.setDescPadding(2);
			hf.setWidth(160);
			hf.setSyntaxPrefix("Usage:    ");
			hf.printHelp("java -jar x.jar " + CoverageTools.class + " " + subcommand, "Params:", options, "", true);
		}

		if (e != null)
			System.out.println("\nError: " + e);

		System.exit(1);
	}

	/**
	 * @param args
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {

//		String v1doc = "/project/valent/Project_valent/Sample_V1/work/V1.bt2-FINAL.bam.codoc";
//		String v2doc = "/project/valent/Project_valent/Sample_V2/work/V2.bt2-FINAL.bam.codoc";
//		String v3doc = "/project/valent/Project_valent/Sample_V3/work/V3.bt2-FINAL.bam.codoc";
//		String v4doc = "/project/valent/Project_valent/Sample_V4/work/V4.bt2-FINAL.bam.codoc";
//
//		String v1var = "/project/valent/Project_valent/Sample_V1/work/V1_varunion_FINAL.vcf";
//		String v2var = "/project/valent/Project_valent/Sample_V1/work/V1_varunion_FINAL.vcf";
//		String v3var = "/project/valent/Project_valent/Sample_V1/work/V1_varunion_FINAL.vcf";
//		String v4var = "/project/valent/Project_valent/Sample_V1/work/V1_varunion_FINAL.vcf";
//
//		String v2minv1var = "/project/valent/Project_valent/results/V2-minus-V1-tumoronly.vcf";
//		String v2minv1varControl = "/project/valent/Project_valent/results/V2-minus-V1-normalalso.vcf";
//		String v3minv1var = "/project/valent/Project_valent/results/V3-minus-V1-tumoronly.vcf";
//		String v3minv1varControl = "/project/valent/Project_valent/results/V3-minus-V1-normalalso.vcf";
//
//		args = new String[] { "cancerNormalFilter", "-covT", v2doc, "-vcfT", v2var, "-covN", v1doc, "-vcfN", v1var, "-minCoverage", "5", "-tumorOnly",
//				v2minv1var, "-normalAlso", v2minv1varControl };

		// create the command line parser
		CommandLineParser parser = new PosixParser();

		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this usage information.");
		String subcommand = null;

		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args, true);

			// validate that block-size has been set
			if (line.getArgs().length == 0 || line.hasOption("h")) {
				usage(options, subcommand, null);
			}

			subcommand = line.getArgs()[0];

			if (subcommand.equalsIgnoreCase("cancerNormalFilter")) {
				options = new Options();

				Option opt = new Option("covT", true, "Tumor coverage.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("covN", true, "Normal coverage.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("vcfT", true, "Tumor variants (VCF).");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("vcfN", true, "Normal variants (VCF).");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("tumorOnly", true, "Output of filtered variants (VCF).");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("normalAlso", true, "Output of the variants that were found also in the normal (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("lowCoverage", true, "Output of the variants that had too low coverage in the normal (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("minCoverage", true, "Minimum coverage (default:2).");
				opt.setRequired(false);
				options.addOption(opt);

				options.addOption("v", "verbose", false, "be verbose.");

				line = parser.parse(options, args);
				if (line.hasOption("v"))
					debug = true;
				else
					debug = false;

				File covTFile = new File(line.getOptionValue("covT"));
				File vcfTFile = new File(line.getOptionValue("vcfT"));

				File covNFile = new File(line.getOptionValue("covN"));
				File vcfNFile = new File(line.getOptionValue("vcfN"));

				File vcfOutFile = new File(line.getOptionValue("tumorOnly"));

				int minCoverage = line.hasOption("minCoverage") ? Integer.parseInt(line.getOptionValue("minCoverage")) : 2;

				File normalAlsoFile = line.hasOption("normalAlso") ? new File(line.getOptionValue("normalAlso")) : null;
				File lowCoverageFile = line.hasOption("lowCoverage") ? new File(line.getOptionValue("lowCoverage")) : null;

				cancerNormalFilter(covTFile, vcfTFile, covNFile, vcfNFile, vcfOutFile, minCoverage, normalAlsoFile, lowCoverageFile);

				System.exit(0);
			} else if (subcommand.equalsIgnoreCase("combineCoverageFiles")) {
				options = new Options();

				Option opt = new Option("cov1", true, "Compressed coverage 1.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("cov1Vcf", true, "Compressed coverage 1 VCF file (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("cov2", true, "Compressed coverage 2.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("cov2Vcf", true, "Compressed coverage 2 VCF file (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("op", true, "Operator " + Arrays.toString(OPERATOR.values()));
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("o", true, "Output.");
				opt.setRequired(true);
				options.addOption(opt);

				options.addOption("v", "verbose", false, "be verbose.");

				line = parser.parse(options, args);
				if (line.hasOption("v"))
					debug = true;
				else
					debug = false;

				File cov1File = new File(line.getOptionValue("cov1"));
				File cov1VcfFile = line.hasOption("cov1Vcf") ? new File(line.getOptionValue("cov1")) : null;
				File cov2File = new File(line.getOptionValue("cov2"));
				File cov2VcfFile = line.hasOption("cov2Vcf") ? new File(line.getOptionValue("cov2")) : null;
				File covOut = new File(line.getOptionValue("o"));
				OPERATOR op = (OPERATOR) StringUtils.findInEnum(line.getOptionValue("op"), OPERATOR.values());

				combineCoverageFiles(cov1File, cov1VcfFile, cov2File, cov2VcfFile, op, covOut);
				System.exit(0);
			} else if (subcommand.equalsIgnoreCase("correlateCoverageFiles")) {
				options = new Options();

				Option opt = new Option("cov1", true, "Compressed coverage 1.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("cov1Vcf", true, "Compressed coverage 1 VCF file (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("cov2", true, "Compressed coverage 2.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("cov2Vcf", true, "Compressed coverage 2 VCF file (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				opt = new Option("o", true, "Output.");
				opt.setRequired(true);
				options.addOption(opt);

				options.addOption("v", "verbose", false, "be verbose.");

				line = parser.parse(options, args);
				if (line.hasOption("v"))
					debug = true;
				else
					debug = false;

				File cov1File = new File(line.getOptionValue("cov1"));
				File cov1VcfFile = line.hasOption("cov1Vcf") ? new File(line.getOptionValue("cov1")) : null;
				File cov2File = new File(line.getOptionValue("cov2"));
				File cov2VcfFile = line.hasOption("cov2Vcf") ? new File(line.getOptionValue("cov2")) : null;
				PrintStream corrOut = System.out;
				if (!line.getOptionValue("o").equals("-"))
					corrOut = new PrintStream(line.getOptionValue("o"));

				correlateCoverageFiles(cov1File, cov1VcfFile, cov2File, cov2VcfFile, corrOut);

				if (!line.getOptionValue("o").equals("-"))
					corrOut.close();
				System.exit(0);
			} else if (subcommand.equalsIgnoreCase("dumpCoverage")) {
				options = new Options();

				Option opt = new Option("cov1", true, "Compressed coverage 1.");
				opt.setRequired(true);
				options.addOption(opt);

				opt = new Option("cov1Vcf", true, "Compressed coverage 1 VCF file (optional).");
				opt.setRequired(false);
				options.addOption(opt);

				options.addOption("v", "verbose", false, "be verbose.");

				line = parser.parse(options, args);
				if (line.hasOption("v"))
					debug = true;
				else
					debug = false;

				File cov1File = new File(line.getOptionValue("cov1"));
				File cov1VcfFile = line.hasOption("cov1Vcf") ? new File(line.getOptionValue("cov1")) : null;

				dumpIterator(cov1File, cov1VcfFile);
				System.exit(0);
			} else {
				usage(options, subcommand, null);
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
			usage(options, subcommand, e.toString());
		}
	}
}
