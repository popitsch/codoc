package at.cibiv.codoc;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import net.sf.samtools.SAMFileReader.ValidationStringency;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.io.CompressedFile;
import at.cibiv.codoc.io.FileDataOutputBlock;
import at.cibiv.codoc.io.FileHeader;
import at.cibiv.codoc.io.GolombOutputStream;
import at.cibiv.codoc.quant.LogarithmicGridQuatization;
import at.cibiv.codoc.quant.PercentageQuatization;
import at.cibiv.codoc.quant.QuantizationFunction;
import at.cibiv.codoc.quant.RegularGridQuatization;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.codoc.utils.SamplingTools;
import at.cibiv.ngs.tools.bed.BedToolsCoverageIterator;
import at.cibiv.ngs.tools.bed.SimpleBEDFile;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.sam.iterator.AbstractRead;
import at.cibiv.ngs.tools.sam.iterator.AttributeFilter;
import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.sam.iterator.FastBamCoverageIterator;
import at.cibiv.ngs.tools.sam.iterator.ParseException;
import at.cibiv.ngs.tools.util.CanonicalChromsomeComparator;
import at.cibiv.ngs.tools.util.CanonicalChromsomeComparator.CHROM_CATEGORY;
import at.cibiv.ngs.tools.util.FileUtils;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.Histogram;
import at.cibiv.ngs.tools.util.Statistics;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.vcf.gvcf.GVCFCoverageIterator;
import at.cibiv.ngs.tools.wig.WigIterator;

/**
 * A compressor for DOC data.
 * 
 * FIXME: there seems to be a bug with the first read of a filtered alignment
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageCompressor implements ChromosomeIteratorListener {

	/**
	 * Debug flag.
	 */
	static boolean debug = false;

	public static final String CMD = "compress";
	public static final String CMD_INFO = "Extracts and compressed coverage data from a SAM/BAM file.";

	public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

	/**
	 * The compression method used for envelope-compression. Note that each
	 * stream may have its own compression method configured! Generic streams,
	 * however, use this method.
	 */
	public BLOCK_COMPRESSION_METHOD defaultCompressionMethod = FileDataOutputBlock.DEFAULT_COMPRESSION_METHOD;

	/**
	 * List of standard streams
	 */
	public static enum STANDARD_STREAM {
		CHR, POS, COV1, COV2
	}

	/**
	 * List of quantization methods
	 */
	public static enum QUANT_METHOD {
		PERC, GRID, LOG2
	}

	public static final String OPT_BEST_COMPRESSION = "best";
	public static final String OPT_BLOCKSIZE = "blockSize";
	public static final String OPT_COMPRESSION_ALGORITHM = "compressionAlgorithm";
	public static final String OPT_QUANT_METHOD = "qmethod";
	public static final String OPT_QUANT_PARAM = "qparam";
	public static final String OPT_KEEP_WORKDIR = "keepWorkDir";
	public static final String OPT_CREATE_STATS = "createStats";
	public static final String OPT_STATS_FILE = "statsFile";
	public static final String OPT_DUMP_RAW = "dumpRawCoverage";
	public static final String OPT_COVERAGE_FILE = "covFile";
	public static final String OPT_OUT_FILE = "outFile";
	public static final String OPT_VCF_FILE = "vcfFile";
	public static final String OPT_BED_FILE = "bedFile";
	public static final String OPT_BAM_FILTER = "bamFilter";
	public static final String OPT_TMP_DIR = "tmpDir";
	public static final String OPT_SCALE_COVERAGE = "scaleCoverage";
	public static final String OPT_MANUAL_GOLOMB_K = "manualGolombK";
	public static final String OPT_VALIDATION_STRINGENCY = "validationStringency";
	public static final String OPT_WEIGHT_BY_X0 = "weightByX0";
	public static final String OPT_VERBOSE = "v";

	public static final String OPT_BLOCK_BORDERS = "blockBorders";
	public static final String OPT_CHR_LIST = "originalChromosomes";

	public static final String STAT_MIN = "minimum-coverage-autosomes";
	public static final String STAT_MAX = "maximum-coverage-autosomes";

	public static final String STAT_PSEUDO_MEDIAN_AUTO = "pseudo-median-coverage-autosomes";
	public static final String STAT_PSEUDO_MEDIAN_X = "pseudo-median-coverage-X";
	public static final String STAT_PSEUDO_MEDIAN_Y = "pseudo-median-coverage-Y";

	public static final String STAT_MEAN_AUTO = "mean-coverage-autosomes";
	public static final String STAT_MEAN_X = "mean-coverage-X";
	public static final String STAT_MEAN_Y = "mean-coverage-Y";

	public static final String STAT_COUNT_AUTO = "count-coverage-autosomes";
	public static final String STAT_COUNT_X = "count-coverage-X";
	public static final String STAT_COUNT_Y = "count-coverage-Y";

	public static final String STAT_HIST_AUTO = "count-histogram-autosomes";
	public static final String STAT_HIST_X = "count-histogram-X";
	public static final String STAT_HIST_Y = "count-histogram-Y";

	/**
	 * # of codewords per block.
	 */
	public static int DEFAULT_BLOCKSIZE = 1000000;

	/**
	 * number of reads/positions that are taken into account for the estimation
	 * of the golomb-encoding parameter estimation
	 */
	public final static int GOLOMB_MU_ESTIMATE_SAMPLE_SIZE = 10000;

	/**
	 * The quantization function.
	 */
	private QuantizationFunction quant;

	/**
	 * Output stream for chromosomes
	 */
	private List<FileDataOutputBlock<String>> chrData = new ArrayList<>();

	/**
	 * the knot-point position
	 */
	private List<FileDataOutputBlock<Integer>> posData = new ArrayList<>();

	/**
	 * the knot-point coverage 1
	 */
	private List<FileDataOutputBlock<Integer>> cov1Data = new ArrayList<>();

	/**
	 * the knot-point coverage 2
	 */
	private List<FileDataOutputBlock<Integer>> cov2Data = new ArrayList<>();

	/**
	 * The coverage iterator
	 */
	private CoverageIterator<?> coverageIterator;

	/**
	 * ROI file
	 */
	private SimpleBEDFile roiFile;

	/**
	 * The header of the compressed file.
	 */
	private CompressionHeader header;

	/**
	 * A list of messages.
	 */
	private Collection<String> messages = new ArrayList<String>();

	/**
	 * A list of warning messages.
	 */
	private Collection<Throwable> warnings = new ArrayList<Throwable>();

	/**
	 * Statistics.
	 */
	private Statistics statistics;

	/**
	 * last written coordinate.
	 */
	private Long lastwrittenpos1 = null;

	/**
	 * index of the current data blocks
	 */
	private int currentBlockIndex;

	/**
	 * Chrom was changed right now.
	 */
	private boolean chromWasChanged = false;

	/**
	 * The working dir
	 */
	private File workDir;

	/**
	 * Current configuration.
	 */
	private PropertyConfiguration config;

	/**
	 * A list of the block border positions.
	 */
	private List<GenomicPosition> blockBorders;

	/**
	 * An ordered(!) list of the original chromosome names
	 */
	private List<String> chrOrigList;

	/**
	 * The number of codewords per data block
	 */
	private double blockSize = DEFAULT_BLOCKSIZE;

	/**
	 * Constructor.
	 * 
	 * @param sortedSamFile
	 * @param vcfFile
	 * @param regionsOfInterest
	 * @param out
	 * @throws IOException
	 * @throws ParseException
	 */
	public CoverageCompressor(PropertyConfiguration config) throws Throwable {
		this.config = config;

		CoverageIterator<?> it = null;

		File coverageFile = new File(config.getProperty(OPT_COVERAGE_FILE));
		String ext = coverageFile.getName().lastIndexOf('.') > 0 ? coverageFile.getName().substring(coverageFile.getName().lastIndexOf('.')) : "";

		if (ext.equalsIgnoreCase(".bam") || ext.equalsIgnoreCase(".sam")) {

			// get BAM filters
			ArrayList<AttributeFilter> filters = null;
			if (config.getProperty(OPT_BAM_FILTER, (String) null) != null) {
				filters = new ArrayList<AttributeFilter>();
				String tmp[] = config.getProperty(OPT_BAM_FILTER).split(";");
				for (String t : tmp) {
					AttributeFilter f = AttributeFilter.parseFromString(t);
					if (f != null)
						filters.add(f);
					else
						warnings.add(new CodocException("Unable to parse filter string " + t + " - IGNORING"));
				}
			}

			if (config.hasProperty(OPT_MANUAL_GOLOMB_K)) {
				config.setProperty(STANDARD_STREAM.POS + "-k", config.getProperty(OPT_MANUAL_GOLOMB_K));
			} else {
				// estimate golomb parameter from the first 10000 reads
				float mu = SamplingTools.calcStartPosDiffMuFromSam(coverageFile, GOLOMB_MU_ESTIMATE_SAMPLE_SIZE);
				int k = GolombOutputStream.calcK(mu);
				addMessage("Estimated Golomb param k as " + k + " from mu " + mu);
				config.setProperty(STANDARD_STREAM.POS + "-k", k + "");
			}

			// Iterator pointing to the current pileup.
			if (debug)
				System.out.println("Extracting and compressing coverage from SAM/BAM file: " + coverageFile);
			ValidationStringency vs = ValidationStringency.LENIENT;
			if (config.hasProperty(OPT_VALIDATION_STRINGENCY)) {
				if (config.getProperty(OPT_VALIDATION_STRINGENCY).equalsIgnoreCase("SILENT"))
					vs = ValidationStringency.SILENT;
				else if (config.getProperty(OPT_VALIDATION_STRINGENCY).equalsIgnoreCase("STRICT"))
					vs = ValidationStringency.STRICT;
			}
			boolean weightByX0 = config.getProperty(OPT_WEIGHT_BY_X0, "false").equalsIgnoreCase("true");
			boolean useFirstOfPairStrand = false;
			boolean countProperPairs = false;
			it = new FastBamCoverageIterator(coverageFile, filters, weightByX0, useFirstOfPairStrand, countProperPairs, vs);

		} else if (ext.equalsIgnoreCase(".wig")) {
			if (debug)
				System.out.println("Extracting and compressing coverage from WIG file: " + coverageFile);
			float scaleFactor = 1.0f;
			if (config.hasProperty(OPT_SCALE_COVERAGE))
				scaleFactor = Float.parseFloat(config.getProperty(OPT_SCALE_COVERAGE));

			if (config.hasProperty(OPT_MANUAL_GOLOMB_K)) {
				config.setProperty(STANDARD_STREAM.POS + "-k", config.getProperty(OPT_MANUAL_GOLOMB_K));
			} else {
				// estimate golomb parameter from the first 10000 reads
				float mu = SamplingTools.calcStartPosDiffMuFromWig(coverageFile, scaleFactor, GOLOMB_MU_ESTIMATE_SAMPLE_SIZE);

				int k = GolombOutputStream.calcK(mu);
				addMessage("Estimated Golomb param k as " + k + " from mu " + mu);
				config.setProperty(STANDARD_STREAM.POS + "-k", k + "");
			}

			it = new WigIterator(coverageFile, scaleFactor);
		} else if (ext.equalsIgnoreCase(".vcf") || ext.equalsIgnoreCase(".gz") || ext.equalsIgnoreCase(".gvcf")) {
			if (debug)
				System.out.println("Extracting and compressing coverage from gVCF file: " + coverageFile);
			float scaleFactor = 1.0f;
			if (config.hasProperty(OPT_SCALE_COVERAGE))
				scaleFactor = Float.parseFloat(config.getProperty(OPT_SCALE_COVERAGE));

			if (config.hasProperty(OPT_MANUAL_GOLOMB_K)) {
				config.setProperty(STANDARD_STREAM.POS + "-k", config.getProperty(OPT_MANUAL_GOLOMB_K));
			} else {
				// FIXME estimate golomb parameter from the first 10000 VCF
				// blocks
				float mu = 1;
				int k = GolombOutputStream.calcK(mu);
				addMessage("Estimated Golomb param k as " + k + " from mu " + mu);
				config.setProperty(STANDARD_STREAM.POS + "-k", k + "");
			}

			it = new GVCFCoverageIterator(coverageFile, scaleFactor);
		} else {
			if (debug)
				System.out.println("Extracting and compressing coverage from coverage file: " + coverageFile);
			float scaleFactor = 1.0f;
			if (config.hasProperty(OPT_SCALE_COVERAGE))
				scaleFactor = Float.parseFloat(config.getProperty(OPT_SCALE_COVERAGE));

			if (config.hasProperty(OPT_MANUAL_GOLOMB_K)) {
				config.setProperty(STANDARD_STREAM.POS + "-k", config.getProperty(OPT_MANUAL_GOLOMB_K));
			} else {
				// estimate golomb parameter from the first 10000 reads
				float mu = SamplingTools.calcStartPosDiffMuFromBedToolsCov(coverageFile, scaleFactor, GOLOMB_MU_ESTIMATE_SAMPLE_SIZE);
				int k = GolombOutputStream.calcK(mu);
				addMessage("Estimated Golomb param k as " + k + " from mu " + mu);
				config.setProperty(STANDARD_STREAM.POS + "-k", k + "");
			}

			it = new BedToolsCoverageIterator(coverageFile, COORD_TYPE.ONEBASED, scaleFactor);
		}

		it.addListener(this);
		compress(it);
	}

	/**
	 * Constructor.
	 * 
	 * @param sortedSamFile
	 * @param vcfFile
	 * @param regionsOfInterest
	 * @param out
	 * @throws IOException
	 * @throws ParseException
	 */
	public CoverageCompressor(CoverageIterator<?> it, PropertyConfiguration config) throws Throwable {
		this.config = config;
		it.addListener(this);
		compress(it);
	}

	/**
	 * Constructor.
	 * 
	 * @param sortedSamFile
	 * @param vcfFile
	 * @param regionsOfInterest
	 * @param out
	 * @throws IOException
	 * @throws ParseException
	 */
	public CoverageCompressor(CoverageIterator<?> it, File vcfFile, File outFile) throws Throwable {
		it.addListener(this);
		PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
		if (vcfFile != null)
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, vcfFile.getAbsolutePath());
		config.setProperty(CoverageCompressor.OPT_OUT_FILE, outFile.getAbsolutePath());
		config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
		config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
		config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.2");
		this.config = config;

		compress(it);
	}

	/**
	 * Create a new indexed block.
	 * 
	 * @throws IOException
	 */
	private void createIndexedBlock() throws IOException {
		try {
			if (currentBlockIndex >= 0) {
				chrData.get(currentBlockIndex).close();
				posData.get(currentBlockIndex).close();
				cov1Data.get(currentBlockIndex).close();
				cov2Data.get(currentBlockIndex).close();

				chrData.get(currentBlockIndex).compress();
				posData.get(currentBlockIndex).compress();
				cov1Data.get(currentBlockIndex).compress();
				cov2Data.get(currentBlockIndex).compress();
			}

			chrData.add(new FileDataOutputBlock<String>(STANDARD_STREAM.CHR.name(), FileUtils.createTempFile(workDir, STANDARD_STREAM.CHR.name() + "_")));
			posData.add(new FileDataOutputBlock<Integer>(STANDARD_STREAM.POS.name(), FileUtils.createTempFile(workDir, STANDARD_STREAM.POS.name() + "_")));
			cov1Data.add(new FileDataOutputBlock<Integer>(STANDARD_STREAM.COV1.name(), FileUtils.createTempFile(workDir, STANDARD_STREAM.COV1.name() + "_")));
			cov2Data.add(new FileDataOutputBlock<Integer>(STANDARD_STREAM.COV2.name(), FileUtils.createTempFile(workDir, STANDARD_STREAM.COV2.name() + "_")));

			currentBlockIndex = chrData.size() - 1;

			// configure
			chrData.get(currentBlockIndex).configure(config);
			posData.get(currentBlockIndex).configure(config);
			cov1Data.get(currentBlockIndex).configure(config);
			cov2Data.get(currentBlockIndex).configure(config);

			// update index
			if (coverageIterator.getGenomicPosition() == null) {
				// this happens if there was not single codeword written!
				throw new IOException("Empty coverage profile");
			}
			blockBorders.add(coverageIterator.getGenomicPosition());

		} catch (Throwable e) {
			throw new IOException("Error creating new index block!", e);
		}
	}

	/**
	 * Write a codeword.
	 * 
	 * @param out
	 * @param chr
	 * @param pos1
	 * @param lc
	 * @param cov
	 * @return
	 * @throws IOException
	 */
	protected boolean writeCodeword(String chr, Long pos1, int lc, int cov, String type) throws IOException {

		// create first data blocks
		if (chrData.size() == 0) {
			createIndexedBlock();
		}

		double cw = statistics.inc("codewords");

		if (lastwrittenpos1 == null)
			lastwrittenpos1 = 1l;
		int posDiff = (int) (pos1 - lastwrittenpos1);
		if (posDiff <= 0) {
			// System.out.println("SKIP CODE: " + chr + ":" + pos1 + "\t" +
			// posDiff + "\tlc:" + lc + "\trc:" + cov + "\t" + type);
			return false;
		}

		// System.out.println("CODE: " + chr + ":" + pos1 + "\t" + posDiff +
		// "\tlc:" + lc + "\trc:" + cov + "\t" + type);

		chrData.get(currentBlockIndex).getStream().push(chr);
		posData.get(currentBlockIndex).getStream().push(posDiff);
		cov1Data.get(currentBlockIndex).getStream().push(lc);
		cov2Data.get(currentBlockIndex).getStream().push(cov);

		if (cw % blockSize == 0) {
			createIndexedBlock();
			lastwrittenpos1 = null;
			if ((!chromWasChanged) && (!type.equals("last")))
				writeCodeword(chr, pos1, lc, cov, "firstofblock");
		} else {
			lastwrittenpos1 = pos1;
		}

		return true;
	}

	/**
	 * Called when a new chromosome was detected.
	 */
	public void notifyChangedChromosome(String chr) throws IOException {
		chromWasChanged = true;
	}

	/**
	 * Called when a new read was added
	 */
	public void notifyAddedRead(AbstractRead p) throws IOException {
	}

	/**
	 * Called when a read was finished
	 */
	public void notifyRemovedRead(AbstractRead p) throws IOException {
	}

	public void addWarning(Throwable warning) {
		warnings.add(warning);
	}

	public Collection<Throwable> getWarnings() {
		return warnings;
	}

	public CoverageIterator<?> getCoverageIterator() {
		return coverageIterator;
	}

	public void addMessage(String message) {
		messages.add(message);
	}

	public Collection<String> getMessages() {
		return messages;
	}

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

	public Statistics getStatistics() {
		return statistics;
	}

	/**
	 * Default configuration
	 * 
	 * @return
	 * @throws IOException
	 */
	public static PropertyConfiguration getDefaultConfiguration(BLOCK_COMPRESSION_METHOD compression) {
		StringBuffer sb = new StringBuffer();

		// set default compression algorithm
		sb.append("codoc-version=" + Main.VERSION + "\n");

		// set creation time
		sb.append("created=" + sdf.format(new Date()) + "\n");

		// chr
		sb.append(createConfigString(STANDARD_STREAM.CHR, "type=rle", "datatype=string", "lengthtype=integer", "debug=false", "compression=" + compression));

		// pos
		sb.append(createConfigString(STANDARD_STREAM.POS, "type=golomb", "k=2", "datatype=integer", "debug=false", "compression=" + compression));

		// cov1
		sb.append(createConfigString(STANDARD_STREAM.COV1, "type=standard", "datatype=integer", "lengthtype=byte", "debug=false", "compression=" + compression));

		// cov2
		sb.append(createConfigString(STANDARD_STREAM.COV2, "type=standard", "datatype=integer", "lengthtype=byte", "debug=false", "compression=" + compression));

		return PropertyConfiguration.fromString(sb.toString());
	}

	/**
	 * Instantiate a quantization function.
	 * 
	 * @param qm
	 * @param qp
	 * @return
	 * @throws CodocException
	 */
	protected static QuantizationFunction instantiateQuantizationFunction(PropertyConfiguration config) throws CodocException {
		QUANT_METHOD qm = (QUANT_METHOD) StringUtils.findInEnum(config.getProperty(OPT_QUANT_METHOD), QUANT_METHOD.values());
		if (qm == null)
			throw new CodocException("No valid quantization method provided (" + config.getProperty(OPT_QUANT_METHOD) + "). Set any of "
					+ Arrays.toString(QUANT_METHOD.values()));

		switch (qm) {
		case PERC:
			if (config.getProperty(OPT_QUANT_PARAM) == null)
				throw new CodocException("No valid quantization parameter (percentage) provided. Set a float value.");
			double qp = Double.parseDouble(config.getProperty(OPT_QUANT_PARAM));
			return new PercentageQuatization(qp);
		case GRID:
			if (config.getProperty(OPT_QUANT_PARAM) == null)
				throw new CodocException("No valid quantization parameter (grid height)  provided. Set a float value.");
			qp = Double.parseDouble(config.getProperty(OPT_QUANT_PARAM));
			return new RegularGridQuatization(qp);
		case LOG2:
			return new LogarithmicGridQuatization();
		}
		throw new CodocException("No valid quantization method passed " + qm);
	}

	/**
	 * Positions the iterator to the variant right after the passed position
	 * 
	 * @param it
	 * @param pos1
	 * @return
	 */
	private SimpleVCFVariant positionRightAfter(Iterator<SimpleVCFVariant> it, Long pos1) {
		if (!it.hasNext())
			return null;
		SimpleVCFVariant ret = it.next();
		while (ret.getPosition().longValue() <= pos1) {
			if (!it.hasNext())
				return null;
			ret = it.next();
		}
		return ret;
	}

	/**
	 * Compression
	 * 
	 * @param sortedSamFile
	 * @param outFile
	 * @param filters
	 * @param vcfFile
	 * @param regionsOfInterest
	 * @param workDir
	 * @param config
	 * @throws Throwable
	 */
	protected void compress(CoverageIterator<?> coverageIterator) throws Throwable {
		this.coverageIterator = coverageIterator;
		long startTime = System.currentTimeMillis();
		boolean keepWorkDir = config.getBooleanProperty(OPT_KEEP_WORKDIR, false);
		boolean createStats = config.getBooleanProperty(OPT_CREATE_STATS, false);
		boolean dumpRawCoverage = config.getBooleanProperty(OPT_DUMP_RAW, false);
		debug = config.getBooleanProperty(OPT_VERBOSE, false);
		this.blockSize = config.getIntProperty(OPT_BLOCKSIZE, DEFAULT_BLOCKSIZE);
		if (blockSize <= 1) // safety
			throw new RuntimeException("Minimum Block size is 1!");

		// set quantization method
		this.quant = instantiateQuantizationFunction(config);

		// prepare working files
		File outFile = new File(config.getProperty(OPT_OUT_FILE));
		File vcfFile = config.hasProperty(OPT_VCF_FILE) ? new File(config.getProperty(OPT_VCF_FILE)) : null;
		File regionsOfInterest = config.hasProperty(OPT_BED_FILE) ? new File(config.getProperty(OPT_BED_FILE)) : null;
		this.workDir = config.hasProperty(OPT_TMP_DIR) ? new File(config.getProperty(OPT_TMP_DIR)) : new File(outFile.getParentFile(), "" + Math.random());
		if (!workDir.exists()) {
			if (!workDir.mkdirs())
				throw new IOException("Could not create temporary directory " + workDir);
		}
		File rawCoverageFile = null;
		if (dumpRawCoverage)
			rawCoverageFile = new File(outFile.getAbsolutePath() + ".rawcoverage");
		File statsFile = null;
		if (createStats) {
			statsFile = new File(config.getProperty(OPT_STATS_FILE, outFile.getAbsolutePath() + ".stats"));
		}

		currentBlockIndex = -1;
		lastwrittenpos1 = null;
		// statistics
		statistics = new Statistics();
		try {
			this.header = new CompressionHeader();
			this.blockBorders = new ArrayList<>();
			this.chrOrigList = new ArrayList<>();

			// Init VCF data
			SimpleVCFVariant currentVariant = null;
			Iterator<SimpleVCFVariant> variants = null;
			SimpleVCFFile vcf = (vcfFile != null) ? new SimpleVCFFile(vcfFile) : null;

			// print raw coverage?
			PrintStream rawCoverage = null;
			if (rawCoverageFile != null)
				rawCoverage = new PrintStream(rawCoverageFile);

			// init regions of interest
			GenomicInterval currentRoi = null;
			int maxRois = 0;
			boolean inRoi = false;
			boolean skipChrom = false;
			Iterator<GenomicInterval> rois = null;
			if (regionsOfInterest != null) {
				this.roiFile = new SimpleBEDFile(regionsOfInterest);
				maxRois = roiFile.getIntervalsList().size();
			} else {
				inRoi = true;
			}

			long c = 0;

			int coverage = 0, lastCoverage = 0;
			int minBorder = quant.getMinBorder(coverage);
			int maxBorder = quant.getMaxBorder(coverage);
			long pos1 = 0, lastPos1 = 0;
			String chr = null;
			String lastChr = null;
			// Indicates whether the profile iterator is currently in the zone
			// of 0-coverage at the beginning of a chromosome.
			boolean headingzeros = true;
			chromWasChanged = true;
			double coverageSumAuto = 0, coverageCountAuto = 0;
			double coverageSumX = 0, coverageCountX = 0;
			double coverageSumY = 0, coverageCountY = 0;

			// for calculating the histogram and the pseudo-median.
			Histogram<Integer> histAutosomes = new Histogram<>();
			Histogram<Integer> histX = new Histogram<>();
			Histogram<Integer> histY = new Histogram<>();
			CHROM_CATEGORY chromCat = CHROM_CATEGORY.OTHER;

			while (coverageIterator.hasNext()) {
				statistics.inc("coverage-values");
				c++;
				if (debug) {
					if (c % 100000 == 0)
						System.out.print(".");
					if (c % 10000000 == 0)
						System.out.println();
				}

				// skip if chrom is finished.
				if (skipChrom) {
					if (chromWasChanged)
						skipChrom = false;
					else {
						coverageIterator.skip();
						statistics.inc("skipped-values");
						continue;
					}
				}

				// ..................................................................
				// Load next coverage
				// ..................................................................
				coverage = coverageIterator.nextCoverage();
				pos1 = coverageIterator.getOffset();
				chr = coverageIterator.getCurrentReference();
				boolean isExternalCoverage = false;

				// ..................................................................
				// Chromosome change. (i.e., chr is the new chrom already)
				// ..................................................................
				if (chromWasChanged) {
					// finish chrom!
					writeCodeword(lastChr, lastPos1 + 1, lastCoverage, 0, "last1");

					String prefixedChr = StringUtils.prefixedChr(chr);

					// load ROIs
					if (roiFile != null) {
						if (roiFile.getIntervalsPerChrom().get(prefixedChr) != null) {
							SortedSet<GenomicInterval> tmp = new TreeSet<GenomicInterval>(roiFile.getIntervalsPerChrom().get(prefixedChr));
							rois = tmp.iterator();
						} else {
							skipChrom = true;
						}
					}

					// load VCF data
					if (vcf != null) {
						List<SimpleVCFVariant> vars = vcf.getVariants(prefixedChr);
						if ((vars != null) && (vars.size() > 0)) {
							SortedSet<SimpleVCFVariant> tmp = new TreeSet<SimpleVCFVariant>(vars);
							variants = tmp.iterator();
							currentVariant = positionRightAfter(variants, pos1);
						}
					}

					// update values
					if (!chrOrigList.contains(chr))
						chrOrigList.add(chr);
					chromWasChanged = false;
					lastPos1 = 0;
					lastwrittenpos1 = null;
					lastCoverage = 0;
					minBorder = quant.getMinBorder(lastCoverage);
					maxBorder = quant.getMaxBorder(lastCoverage);
					headingzeros = true;
					header.addValue("sequence", prefixedChr);
					// chromosomal category
					chromCat = CanonicalChromsomeComparator.getCategory(chr);
				}

				// ..................................................................
				// check whether we are in a ROI
				// ..................................................................
				if (roiFile != null) {
					// System.out.println(pos1 + "/" + currentRoi + "/" +
					// (currentRoi!=null?currentRoi.contains(pos1 - 1):null));
					if (currentRoi == null) {
						// is there a roi?
						if ((rois == null) || (!rois.hasNext())) {
							continue;
						}
						currentRoi = rois.next();
						statistics.inc("ROIs");
					}
					// are we within the current ROI?
					if (inRoi) {

						// NOTE that BED intervals are 0-based
						inRoi = currentRoi.contains(pos1 - 1);
						if (!inRoi) {
							// LEFT ROI
							//System.out.println("LEFT ROI");
							writeCodeword(lastChr, lastPos1, lastCoverage, coverage, "left roi");							
							writeCodeword(lastChr, pos1, coverage, 0, "left roi");
							lastCoverage=-1;
							currentRoi = null;
							maxRois--;
							if (maxRois <= 0)
								break;
							if ((rois == null) || (!rois.hasNext())) {
								skipChrom = true;
							}
						}
					} else {
						// NOTE that BED intervals are 0-based
						inRoi = currentRoi.contains(pos1 - 1);
//						if (inRoi) {
//							// ENTERED a ROI
//							System.out.println("Entered "+ currentRoi);
//							//writeCodeword(lastChr, pos1-1, 0, coverage, "entered roi");
//							//writeCodeword(lastChr, pos1, coverage, coverage, "entered roi");
//							// System.out.println("ENTERED ROI " + currentRoi);
//							System.out.println("ok");
//						}
					}
				}
				if (!inRoi) {
					//System.out.println("skip " + pos1);
					continue;
				}

				// ..................................................................
				// check whether we "HIT" a variant
				// ..................................................................
				if (currentVariant != null) {
					if (pos1 == currentVariant.getPosition().intValue()) {
						statistics.inc("hit-variants");
						// get actual coverage from Variant!
						Integer varCov = currentVariant.estimateCoverage();
						if (varCov != null) {
							coverage = varCov;
							// set isExternalCoverage to true => Borders will be
							// adapted!
							isExternalCoverage = true;
						}
						// move to next variant with *different* coordinates
						// (in VCF, there may be multiple variants at one
						// genomic position
						// wr!)
						currentVariant = positionRightAfter(variants, pos1 + 1);
					}
				}

				// ..................................................................
				// raw coverages
				// ..................................................................
				if (coverage > 0)
					headingzeros = false;
				if ((rawCoverage != null) && (!headingzeros))
					rawCoverage.println(chr + "\t" + pos1 + "\t" + coverage);

				//System.out.println(chr + "\t" + pos1 + "\t" + coverage);

				// ..................................................................
				// calculate stats
				// ..................................................................
				statistics.setMin(STAT_MIN, coverage);
				statistics.setMax(STAT_MAX, coverage);
				switch (chromCat) {
				case AUTOSOME:
					histAutosomes.push(coverage);
					coverageSumAuto += coverage;
					coverageCountAuto++;
					break;
				case X:
					histX.push(coverage);
					coverageSumX += coverage;
					coverageCountX++;
					break;
				case Y:
					histY.push(coverage);
					coverageSumY += coverage;
					coverageCountY++;
					break;
				default:
				}

				// ..................................................................
				// check coverages
				// ..................................................................
				if (coverage < minBorder) {
					// write code
					writeCodeword(chr, pos1, lastCoverage, coverage, "min border " + minBorder);
					minBorder = quant.getMinBorder(coverage);
					maxBorder = quant.getMaxBorder(coverage);
					statistics.inc("left-lower-border");
				} else if (coverage > maxBorder) {
					// store point right after error-interval is left
					writeCodeword(chr, pos1, lastCoverage, coverage, "max border " + maxBorder);
					minBorder = quant.getMinBorder(coverage);
					maxBorder = quant.getMaxBorder(coverage);
					statistics.inc("left-upper-border");
				}

				if (isExternalCoverage) {
					minBorder = quant.getMinBorder(coverage);
					maxBorder = quant.getMaxBorder(coverage);
				}

				// store values
				lastCoverage = coverage;
				lastPos1 = pos1;
				lastChr = chr;
			}

			// ..................................................................
			// Finished
			// ..................................................................
			if (statistics.get("codewords") == null) {
				System.out.println("CODOC wrote no single codeword, maybe input is empty or contains only unmapped/filtered reads?");
				return;
			}

			// Set statistics
			statistics.set(STAT_MEAN_AUTO, nonull(coverageSumAuto / coverageCountAuto));
			statistics.set(STAT_COUNT_AUTO, c);
			statistics.set(STAT_PSEUDO_MEDIAN_AUTO, nonull(histAutosomes.estimateMedian()));

			statistics.set(STAT_MEAN_X, nonull(coverageSumX / coverageCountX));
			statistics.set(STAT_COUNT_X, c);
			statistics.set(STAT_PSEUDO_MEDIAN_X, nonull(histX.estimateMedian()));

			statistics.set(STAT_MEAN_Y, nonull(coverageSumY / coverageCountY));
			statistics.set(STAT_COUNT_Y, c);
			statistics.set(STAT_PSEUDO_MEDIAN_Y, nonull(histY.estimateMedian()));

			// write statistics to compressed header.
			config.setProperty(STAT_MIN, statistics.get(STAT_MIN).intValue() + "");
			config.setProperty(STAT_MAX, statistics.get(STAT_MAX).intValue() + "");
			config.setProperty(STAT_MEAN_AUTO, nonull(coverageSumAuto / coverageCountAuto) + "");
			config.setProperty(STAT_COUNT_AUTO, c + "");
			config.setProperty(STAT_PSEUDO_MEDIAN_AUTO, nonull(histAutosomes.estimateMedian()) + "");
			config.setProperty(STAT_MEAN_X, nonull(coverageSumX / coverageCountX) + "");
			config.setProperty(STAT_COUNT_X, c + "");
			config.setProperty(STAT_PSEUDO_MEDIAN_X, nonull(histX.estimateMedian()) + "");
			config.setProperty(STAT_MEAN_Y, nonull(coverageSumY / coverageCountY) + "");
			config.setProperty(STAT_COUNT_Y, c + "");
			config.setProperty(STAT_PSEUDO_MEDIAN_Y, nonull(histY.estimateMedian()) + "");
			// write histograms to compressed header.
			config.setProperty(STAT_HIST_AUTO, histAutosomes.toString());
			config.setProperty(STAT_HIST_X, histX.toString());
			config.setProperty(STAT_HIST_Y, histY.toString());

			// write last codeword
			writeCodeword(lastChr, lastPos1 + 1, lastCoverage, 0, "last1");

			// write block borders.
			blockBorders.add(new GenomicPosition(chr, pos1));
			String tmp = null;
			for (GenomicPosition bb : blockBorders) {
				if (tmp == null)
					tmp = bb.toStringOrig();
				else
					tmp += "," + bb.toStringOrig();
			}
			config.setProperty(OPT_BLOCK_BORDERS, tmp);
			// write chromosome list
			tmp = null;
			for (String co : chrOrigList) {
				if (tmp == null)
					tmp = co;
				else
					tmp += "," + co;
			}
			if (tmp != null)
				config.setProperty(OPT_CHR_LIST, tmp);

			if (debug)
				System.out.println("block borders: " + config.getProperty(OPT_BLOCK_BORDERS));
			double t = (System.currentTimeMillis() - startTime);
			statistics.set("time [ms]", t);
			statistics.set("sampled positions", (double) c);
			statistics.set("speed [pos/ms]", ((double) c / t));

		} finally {

			// garbage collect
			System.gc();
			FileHeader header = null;

			// skip file creation of no codewords were written
			if (statistics.get("codewords") == null) {
				System.err.println("No codewords were written - skipping CODOC file creation");
			} else {
				// close and compress individual blocks
				if (currentBlockIndex >= 0) {
					// close block to flush buffers.
					chrData.get(currentBlockIndex).close();
					posData.get(currentBlockIndex).close();
					cov1Data.get(currentBlockIndex).close();
					cov2Data.get(currentBlockIndex).close();

					// compress the block
					chrData.get(currentBlockIndex).compress();
					posData.get(currentBlockIndex).compress();
					cov1Data.get(currentBlockIndex).compress();
					cov2Data.get(currentBlockIndex).compress();
				}

				// get statistics
				int val = 0;
				for (FileDataOutputBlock<String> block : chrData) {
					val += block.getStream().getEncodedEntities();
				}
				statistics.set("encodedChroms", val);
				val = 0;
				for (FileDataOutputBlock<Integer> block : posData) {
					val += block.getStream().getEncodedEntities();
				}
				statistics.set("encodedPos", val);
				val = 0;
				for (FileDataOutputBlock<Integer> block : cov1Data) {
					val += block.getStream().getEncodedEntities();
				}
				statistics.set("encodedCov1", val);
				val = 0;
				for (FileDataOutputBlock<Integer> block : cov2Data) {
					val += block.getStream().getEncodedEntities();
				}
				statistics.set("encodedCov2", val);

				// create Header
				header = new FileHeader(FileUtils.createTempFile(workDir, "header_"));
				header.setConfiguration(config);
				for (FileDataOutputBlock<?> b : chrData)
					header.addBlock(b);
				for (FileDataOutputBlock<?> b : posData)
					header.addBlock(b);
				for (FileDataOutputBlock<?> b : cov1Data)
					header.addBlock(b);
				for (FileDataOutputBlock<?> b : cov2Data)
					header.addBlock(b);
				header.toFile();
				// merge all blocks
				CompressedFile cf = new CompressedFile(header);
				cf.toFile(outFile);
			}

			// delete temporary files.
			if (!keepWorkDir) {
				if (header != null)
					if (!header.deleteFiles())
						addWarning(new CodocException("Could not remove header file " + header.getF1()));
				for (FileDataOutputBlock<?> b : chrData) {
					b.close();
					if (!b.deleteFiles())
						addWarning(new CodocException("Could not remove temporary file(s) of " + b));
				}
				for (FileDataOutputBlock<?> b : posData) {
					b.close();
					if (!b.deleteFiles())
						addWarning(new CodocException("Could not remove temporary file(s) of " + b));
				}
				for (FileDataOutputBlock<?> b : cov1Data) {
					b.close();
					if (!b.deleteFiles())
						addWarning(new CodocException("Could not remove temporary file(s) of " + b));
				}
				for (FileDataOutputBlock<?> b : cov2Data) {
					b.close();
					if (!b.deleteFiles())
						addWarning(new CodocException("Could not remove temporary file(s) of " + b));
				}

				// delete temporary directory
				if (!workDir.delete())
					addMessage("Could not remove temporary directory " + workDir);
			}

			// stats
			statistics.set("TOTAL (compressed) [byte]", outFile.length());
			if (statsFile != null) {
				PrintStream statsOut = new PrintStream(statsFile);
				statsOut.println(statistics);
				statsOut.println(config);
				statsOut.close();
			}
		}
	}

	/**
	 * Replace null with 0
	 * 
	 * @param val
	 * @return
	 */
	private Number nonull(Number val) {
		if (val == null)
			return 0;
		if (val instanceof Double && ((Double) val == Double.NaN))
			return 0;
		return val;
	}

	/**
	 * Convenience method. Will block-compress with GZIP.
	 * 
	 * filter: FLAGS^^1024;FLAGS^^51 qmethod: perc qparam: 0.2
	 */
	public static boolean compress(File sortedBam, File vcfFile, File outFile, boolean verbose, String... flags) {

		PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
		config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, sortedBam.getAbsolutePath());
		config.setProperty(CoverageCompressor.OPT_VCF_FILE, vcfFile.getAbsolutePath());
		config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
		config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
		config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.2");

		for (String f : flags)
			config.setProperty(f, "true");

		return compress(config, outFile, verbose);
	}

	/**
	 * Convenience method. Will compress with the passed configuration and write
	 * warnings and errors to stderr.
	 */
	public static boolean compress(PropertyConfiguration config, File outFile, boolean verbose) {
		config.setProperty(CoverageCompressor.OPT_OUT_FILE, outFile.getAbsolutePath());
		if (verbose)
			debug = true;
		CoverageCompressor compressor = null;
		try {
			compressor = new CoverageCompressor(config);
			return true;
		} catch (Throwable e) {
			if (debug)
				e.printStackTrace();
			return false;
		} finally {
			if (debug) {
				if (compressor != null)
					for (String m : compressor.getMessages())
						System.err.println("> " + m);
			}
			if ((compressor != null) && (compressor.getWarnings().size()) > 0) {
				System.err.println("Warnings: ");
				compressor.dumpWarnings();
			}
		}
	}

	/**
	 * Dump all warnings to stderr.
	 */
	public void dumpWarnings() {
		for (Throwable w : getWarnings())
			w.printStackTrace(System.err);
	}

	/**
	 * Print usage information.
	 * 
	 * @param options
	 */
	@SuppressWarnings({ "unchecked" })
	private static void usage(Options options, String subcommand, String e) {
		int w = 120;

		Options mandatory = new Options();

		for (Object o : options.getRequiredOptions())
			if (o instanceof Option) {
				if (((Option) o).isRequired())
					mandatory.addOption((Option) o);
			} else if (o instanceof OptionGroup) {
				if (((OptionGroup) o).isRequired())
					mandatory.addOptionGroup((OptionGroup) o);
			}
		for (Option o : (Collection<Option>) options.getOptions())
			if (o.isRequired())
				mandatory.addOption(o);
		PrintWriter pw = new PrintWriter(System.out);
		HelpFormatter hf = new HelpFormatter();
		hf.setSyntaxPrefix("");

		hf.printUsage(pw, w, "Usage:  java -jar x.jar " + CMD, options);
		pw.println();
		pw.flush();

		hf.printHelp(pw, w, "Mandatory Params", "", mandatory, 8, 2, "");
		pw.flush();
		pw.println();

		hf.printHelp(pw, w, "Params", "", options, 8, 2, "");

		pw.flush();

		System.out.println();

		// hf.printOptions(pw, width, options, leftPad, descPad)

		System.out.println();
		System.out.println("\t-------------------------------- FILTERS -----------------------------------");
		System.out.println("\tFilters can be used to restrict the coverage extraction from am SAM/BAM file");
		System.out.println("\tto reads that match the given filter criteria. Multiple filters are combined");
		System.out.println("\tby a logical AND. Filters are of the form <FIELD><OPERATOR><VALUE>.");
		System.out.println();
		System.out.println("\tPossible fields:");
		System.out.println("\tCHR\tChromosome");
		System.out.println("\tMAPQ\tthe mapping quality");
		System.out.println("\tFLAGS\tthe read flags");
		System.out.println("\tSTRAND\tthe read strand (+/-)");
		System.out.println("\tFOPSTRAND\tthe first-of-pair read strand (+/-)");
		System.out.println("\tOther names will be mapped directly to the optional field name in the SAM file.");
		System.out.println("\tUse e.g., NM for the 'number of mismatches' field. Reads that do not have a field");
		System.out.println("\tset will be included. @see http://samtools.sourceforge.net/SAMv1.pdf");
		System.out.println();
		System.out.println("\tPossible operators:");
		System.out.println("\t<, <=, =, !=, >, >=, ^^ (flag unset), && (flag set)");
		System.out.println();
		System.out.println("\tExample: (do NOT use reads with mapping quality <= 20, or multiple perfect hits)");
		System.out.println("\t-filter 'MAPQ>20' -filter 'H0=1'");
		System.out.println("\t----------------------------------------------------------------------------");

		System.out.println();
		System.out.println("\tCODOC " + (Main.VERSION == null ? "" : Main.VERSION) + " (c) 2013-2015");

		if (e != null)
			System.out.println("\nError: " + e);
		System.exit(1);
	}

	/**
	 * @param args
	 * @throws ParseException
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException, ParseException {
		//
		// args = new String[] { "-cov",
		// "src/test/resources/covcompress/small.bam", "-o",
		// "src/test/resources/covcompress/small.compressed",
		// "-roi", "src/test/resources/covcompress/small.roi",
		// "-v"};

		CommandLineParser parser = new PosixParser();

		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this usage information.");

		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args, true);

			options = new Options();

			Option opt = new Option("cov", true, "Input coverage file. Supported file types are SAM, BAM, WIG, gVCF and bedtools coverage files.");
			opt.setLongOpt(OPT_COVERAGE_FILE);
			opt.setRequired(true);
			options.addOption(opt);

			opt = new Option("vcf", true, "Input VCF file (optional)");
			opt.setLongOpt(OPT_VCF_FILE);
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_BED_FILE, true, "Input BED file containing regions of interest (optional). The file will be interpreted 0-based!");
			opt.setLongOpt("roi");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option("o", true, "Output file");
			opt.setLongOpt("out");
			opt.setRequired(true);
			options.addOption(opt);

			opt = new Option("t", true, "Temporary working directory (default is current dir)");
			opt.setLongOpt("temp");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_KEEP_WORKDIR, false, "Do not delete the work directory (e.g., for debugging purposes)");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_CREATE_STATS, false, "Create a statistics file (default: false)");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_STATS_FILE, true, "Statistics file name (default: <outfile>.stats)");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_DUMP_RAW, false,
					"Create raw coverage file (<outfile>.rawcoverage). No heading or trailing 0-coverage zones will be included. Output will have 1-based coordinates.");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_QUANT_METHOD, true, "Quantization method (PERC, GRID, LOG2); default: PERC");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_QUANT_PARAM, true, "Quantization parameter (default: 0.2)");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_BLOCKSIZE, true, "Number of codewords per data block (default: " + DEFAULT_BLOCKSIZE + ")");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_SCALE_COVERAGE, true,
					"Optional parameter for scaling the coverage values. Applicable only for coverage file input (not for BAM files). (default: none)");
			opt.setRequired(false);
			options.addOption(opt);

			opt = new Option(OPT_MANUAL_GOLOMB_K, true,
					"Optional parameter for manual determination of the Golomb-encoding k parameter (if unset, it will be estimated from the data). (default: none)");
			opt.setRequired(false);
			options.addOption(opt);

			Option opt_compalgo = new Option(OPT_COMPRESSION_ALGORITHM, true, "Used envelope compression algorithm "
					+ Arrays.toString(FileDataOutputBlock.BLOCK_COMPRESSION_METHOD.values()) + ". Default method is "
					+ FileDataOutputBlock.DEFAULT_COMPRESSION_METHOD);
			opt_compalgo.setRequired(false);
			options.addOption(opt_compalgo);

			Option opt_best = new Option(OPT_BEST_COMPRESSION, false,
					"Used best (bzip2) compression method. Compression/decompression times might be slighly slower with this method.");
			opt_best.setRequired(false);
			options.addOption(opt_best);

			opt = new Option(OPT_VALIDATION_STRINGENCY, true, "Used validation stringency when reading the coverage from a SAM/BAM file. Default: LENIENT");
			opt.setRequired(false);
			options.addOption(opt);

			// example: filter reads that were marked as PCR dupl or failed QC:
			// -filter FLAGS^^1024 -filter "FLAGS^^512
			Option filter = new Option("filter", true, "Filter used reads by SAM attribute (applicable only in combination with SAM/BAM files). "
					+ "Multiple filters can be provided that will be combined by a logical AND. Note that filters have "
					+ "no effect on reads that have no value for the according attribute. Examples: 'X0>9', 'X1=ABC', 'STRAND=+'"
					+ "'FLAGS^^512', 'none',... [default: -filter FLAGS^^1024 -filter FLAGS^^512]." + "See below for more help on filters.");
			filter.setRequired(false);
			options.addOption(filter);

			opt = new Option(OPT_WEIGHT_BY_X0, false, "Weight the coverage by X0 values. Used only if input coverage file is SAM/BAM (default: false)");
			opt.setRequired(false);
			options.addOption(opt);

			options.addOption(OPT_VERBOSE, "verbose", false, "be verbose");

			if (line.hasOption("h")) {
				usage(options, null, null);
			}

			line = parser.parse(options, args);

			// determine envelope compression algorithm
			BLOCK_COMPRESSION_METHOD compressionMethod = FileDataOutputBlock.compFromString(line.getOptionValue(OPT_COMPRESSION_ALGORITHM));
			if (line.hasOption(OPT_BEST_COMPRESSION))
				compressionMethod = BLOCK_COMPRESSION_METHOD.BZIP2;

			// parse filter string
			String[] tmp = line.getOptionValues("filter");
			String filters = null;
			if (tmp == null) {
				filters = "FLAGS^^1024;FLAGS^^512";
			} else if (!tmp[0].equalsIgnoreCase("none")) {
				for (int i = 0; i < tmp.length; i++) {
					if (filters == null)
						filters = tmp[i];
					else
						filters += ";" + tmp[i];
				}
			}

			// create configuration
			PropertyConfiguration conf = CoverageCompressor.getDefaultConfiguration(compressionMethod);
			conf.setProperty(OPT_COVERAGE_FILE, line.getOptionValue(OPT_COVERAGE_FILE));
			conf.setProperty(OPT_OUT_FILE, line.getOptionValue("o"));
			if (line.hasOption("t"))
				conf.setProperty(OPT_TMP_DIR, line.getOptionValue("t"));
			if (line.hasOption("vcf"))
				conf.setProperty(OPT_VCF_FILE, line.getOptionValue("vcf"));
			if (line.hasOption(OPT_BED_FILE))
				conf.setProperty(OPT_BED_FILE, line.getOptionValue(OPT_BED_FILE));
			if (filters != null)
				conf.setProperty(OPT_BAM_FILTER, filters);
			if (line.hasOption(OPT_STATS_FILE))
				conf.setProperty(OPT_STATS_FILE, line.getOptionValue(OPT_STATS_FILE));
			if (line.hasOption(OPT_BLOCKSIZE))
				conf.setProperty(OPT_BLOCKSIZE, line.getOptionValue(OPT_BLOCKSIZE));
			conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
			conf.setProperty(OPT_CREATE_STATS, line.hasOption(OPT_CREATE_STATS) + "");
			conf.setProperty(OPT_DUMP_RAW, line.hasOption(OPT_DUMP_RAW) + "");
			conf.setProperty(OPT_QUANT_METHOD, line.hasOption(OPT_QUANT_METHOD) ? line.getOptionValue(OPT_QUANT_METHOD) : QUANT_METHOD.PERC.name());
			conf.setProperty(OPT_QUANT_PARAM, line.hasOption(OPT_QUANT_PARAM) ? line.getOptionValue(OPT_QUANT_PARAM) : "0.2");
			if (line.hasOption(OPT_SCALE_COVERAGE))
				conf.setProperty(OPT_SCALE_COVERAGE, line.getOptionValue(OPT_SCALE_COVERAGE));
			if (line.hasOption(OPT_MANUAL_GOLOMB_K))
				conf.setProperty(OPT_MANUAL_GOLOMB_K, line.getOptionValue(OPT_MANUAL_GOLOMB_K));
			if (line.hasOption(OPT_VALIDATION_STRINGENCY))
				conf.setProperty(OPT_VALIDATION_STRINGENCY, line.getOptionValue(OPT_VALIDATION_STRINGENCY));
			conf.setProperty(OPT_WEIGHT_BY_X0, line.hasOption(OPT_WEIGHT_BY_X0) + "");

			conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");

			CoverageCompressor compressor = new CoverageCompressor(conf);
			compressor.dumpWarnings();
			System.out.println("Finished.");
			return;

		} catch (MissingOptionException e) {
			System.err.println("Error: " + e.getMessage());
			System.err.println();
			usage(options, null, e.toString());
		} catch (Throwable e) {
			e.printStackTrace();
			System.err.println();
			usage(options, null, e.toString());
		}

	}

}
