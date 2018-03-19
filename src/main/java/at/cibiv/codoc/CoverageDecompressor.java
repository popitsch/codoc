package at.cibiv.codoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.broad.igv.Globals;
import org.broad.igv.tools.IgvTools;
import org.broad.igv.track.WindowFunction;
import org.h2.util.MemoryUtils;
import org.json.JSONObject;

import at.cibiv.codoc.CoverageCompressor.STANDARD_STREAM;
import at.cibiv.codoc.io.AbstractDataBlock;
import at.cibiv.codoc.io.FileDataInputBlock;
import at.cibiv.codoc.io.FileDataOutputBlock;
import at.cibiv.codoc.io.FileHeader;
import at.cibiv.codoc.quant.QuantizationFunction;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.bed.SimpleBEDFile;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.sam.iterator.ParseException;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.Histogram;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.util.TabIterator;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.wig.WigOutputStream;

/**
 * A decompressor for CODOC data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageDecompressor {

    public static final String CMD = "decompress";
    public static final String CMD_INFO = "Decompresses coverage data.";

    public static enum CMD_COMMANDS {
	HEAD, QUERY, TOWIG, TOBED, TOTDF, HIST, DEBUG
    };

    /**
     * Debug flag.
     */
    public static boolean debug = false;

    /* OPTIONS */

    public static final String OPT_KEEP_WORKDIR = "keepWorkDir";
    public static final String OPT_COV_FILE = "cov";
    public static final String OPT_OUT_FILE = "o";
    public static final String OPT_VCF_FILE = "vcf";
    public static final String OPT_TMP_DIR = "tmpDir";
    public static final String OPT_RAWCOV_FILE = "rawcoverage";
    public static final String OPT_CREATE_STATS = "createStats";
    public static final String OPT_STATS_FILE = "stats";
    public static final String OPT_CHR_LEN_FILE = "chrLen";
    public static final String OPT_SCALE_FACTOR = "scale";
    public static final String OPT_VERBOSE = "v";
    public static final String OPT_NO_CACHING = "nocache";
    public static final String OPT_DUMP_HEADER_AND_EXIT = "dumpHeaderAndExit";
    public static final String OPT_MAX_CACHED_BLOCKS = "maxCachedBlocks";
    public static final String OPT_ROI = "roi";

    /* DEFAULTS */

    /**
     * Default maximum number of memory-cached blocks (BITs)
     */
    public static int DEFAULT_MAX_CACHED_BLOCKS = 10;

    /* STATE VARIABLES */

    /**
     * Input stream for chromosomes
     */
    List<FileDataInputBlock<String>> chrData = null;

    /**
     * the knot-point position
     */
    private List<FileDataInputBlock<Integer>> posData = null;

    /**
     * the knot-point coverage1
     */
    private List<FileDataInputBlock<Integer>> cov1Data = null;

    /**
     * the knot-point coverage2
     */
    private List<FileDataInputBlock<Integer>> cov2Data = null;

    /**
     * Index for the data blocks.
     */
    private GenomicITree blockIndexTree;

    /**
     * An ordered(!) list of the original chromosome names
     */
    private List<String> chrOrigList;

    /**
     * Maps block ids to their left-most genomic position
     */
    private Map<Integer, GenomicPosition> leftBorders;

    /**
     * Variation file.
     */
    private SimpleVCFFile vcf;

    /**
     * Configuration of the last decompressed file.
     */
    private PropertyConfiguration compressedConfig;

    /**
     * The quantization function.
     */
    private QuantizationFunction quant;

    /**
     * The (compressed) coverage file
     */
    private File covFile;

    /**
     * The vcf file.
     */
    private File vcfFile;

    /**
     * A map of chromosome lengths
     */
    private Map<String, Long> chromLengths;

    /**
     * TRue if the working directory should NOT be removed after decompression.
     */
    private boolean keepWorkDir;

    /**
     * Header of the decompressed file.
     */
    private FileHeader header;

    /**
     * The (temporary) working directory
     */
    private File workDir;

    /**
     * Set to true if in-memory BIT caching should be enabled.
     */
    private boolean inMemoryBlockCaching = true;

    /**
     * For converting back to original chromosome names.
     */
    private Map<String, String> chrNameMap = new HashMap<>();

    /**
     * Scaling factor for the coverage signal.
     */
    private float scaleFactor = 1.0f;

    /**
     * In-memory cache of loaded blocks.
     */
    private LinkedList<CachedBlock> memoryBlockCache = new LinkedList<CachedBlock>();

    /**
     * Maximum number of memory-cached blocks (BITs)
     */
    private int maxCachedBlocks = DEFAULT_MAX_CACHED_BLOCKS;

    /**
     * TRue if the work dir was created (and will be deleted when the
     * decompressor is closed).
     */
    boolean workDirWasCreated = false;

    /**
     * Decompresses the passed file using the passed VCF file. The intermediate
     * files are written to the passed working dir that is deleted after the
     * decompression unless keepWorkDir is set to true.
     * 
     * @param sortedSamFile
     * @param vcfFile
     * @param regionsOfInterest
     * @param out
     * @throws CodocException
     * @throws IOException
     * @throws ParseException
     */
    public CoverageDecompressor(PropertyConfiguration conf) throws CodocException, IOException {
	if (conf == null) {
	    throw new CodocException("Cannot decompress with null configuration");
	}
	// dump header and exit?
	if (conf.getBooleanProperty(OPT_DUMP_HEADER_AND_EXIT, false)) {
	    dumpHeader(conf);
	    return;
	}
	decompress(conf);
	// pre-load the first block!
	loadBlock(0);
    }

    /**
     * Get a compression configuration parameter for the last file that was
     * decompressed.
     * 
     * @param name
     * @return
     */
    public String getCompressionParameter(String name) {
	if (compressedConfig == null)
	    return null;
	return compressedConfig.getProperty(name);
    }

    public PropertyConfiguration getCompressedConfig() {
	return compressedConfig;
    }

    /**
     * @return the working directory
     */
    public File getWorkDir() {
	return workDir;
    }

    /**
     * Dump the header of the passed coverage file and exit.
     * 
     * @param covFile
     * @param workDir
     * @throws IOException
     * @throws CodocException
     */
    public void dumpHeader(PropertyConfiguration conf) throws IOException, CodocException {
	File covFile = new File(conf.getProperty(OPT_COV_FILE));
	FileHeader header = null;
	this.workDir = conf.hasProperty(OPT_TMP_DIR) ? new File(conf.getProperty(OPT_TMP_DIR))
		: new File(covFile.getParentFile(), "" + Math.random());

	// prepare work dir
	if (!workDir.exists()) {
	    if (!workDir.mkdirs())
		throw new IOException("Could not create temporary directory " + workDir);
	    workDirWasCreated = true;
	}

	try {

	    header = FileHeader.fromFile(covFile, workDir);
	    this.compressedConfig = header.getConfiguration();
	    System.out.println("Config:\n" + compressedConfig);
	    this.compressedConfig = null;
	    header.close();
	    header = null;
	} finally {
	    // garbage collect
	    System.gc();
	    close();
	}
    }

    /**
     * Instantiate a decompressor from the passed compressed file. The VCF file
     * specified in the file header will be used for decompression (thus, this
     * works only if this path is available!)
     * 
     * @param covFile
     * @return
     * @throws IOException
     * @throws CodocException
     * @throws Throwable
     */
    public static CoverageDecompressor loadFromFile(File covFile, File vcfFile) throws CodocException, IOException {
	// build config
	PropertyConfiguration conf = getDefaultConfiguration();
	conf.setProperty(OPT_COV_FILE, covFile.getAbsolutePath());
	if (vcfFile != null)
	    conf.setProperty(OPT_VCF_FILE, vcfFile.getAbsolutePath());
	return new CoverageDecompressor(conf);
    }

    /**
     * Load blocks with index idx.
     * 
     * @param idx
     * @throws CodocException
     * @throws IOException
     * @throws Throwable
     */
    public CachedBlock loadBlock(int idx) throws IOException, CodocException {

	if (inMemoryBlockCaching) {

	    if (memoryBlockCache.size() > 0 && memoryBlockCache.get(0).getBlockIdx() == idx) {
		return memoryBlockCache.get(0);
	    }

	    for (CachedBlock cb : memoryBlockCache)
		if (cb.getBlockIdx() == idx) {
		    memoryBlockCache.add(0, cb);
		    if (debug)
			System.out.println("LOAD BLOCK " + idx + " FROM CACHE 2");
		    return memoryBlockCache.get(0);
		}
	}
	if (debug)
	    System.out.println("LOAD BLOCK " + idx);

	int mem = MemoryUtils.getMemoryUsed();
	long start = System.currentTimeMillis();

	Iterator<SimpleVCFVariant> currentVariants = null;
	SimpleVCFVariant currentVariant = null;

	if (!chrData.get(idx).isDecompressed())
	    if (!chrData.get(idx).decompress())
		throw new CodocException("Could not remove decompressed file(s) of " + chrData.get(idx));
	chrData.get(idx).configure(compressedConfig);
	if (!posData.get(idx).isDecompressed())
	    if (!posData.get(idx).decompress())
		throw new CodocException("Could not remove decompressed file(s) of " + posData.get(idx));
	posData.get(idx).configure(compressedConfig);
	if (!cov1Data.get(idx).isDecompressed())
	    if (!cov1Data.get(idx).decompress())
		throw new CodocException("Could not remove decompressed file(s) of " + cov1Data.get(idx));
	cov1Data.get(idx).configure(compressedConfig);
	if (!cov2Data.get(idx).isDecompressed())
	    if (!cov2Data.get(idx).decompress())
		throw new CodocException("Could not remove decompressed file(s) of " + cov2Data.get(idx));
	cov2Data.get(idx).configure(compressedConfig);

	Map<String, List<Integer>> positions = new HashMap<String, List<Integer>>();
	Map<String, List<Integer>> covPrev = new HashMap<String, List<Integer>>();
	Map<String, List<Integer>> covKeypoint = new HashMap<String, List<Integer>>();
	// TODO: init with actual blocksize (get from compressed conf)
	List<Integer> cpos = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);
	List<Integer> ccov = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);
	List<Integer> ccov2 = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);

	String lastchr = null;
	int icount = 0;

	int pos1 = 1, lastpos1 = 1, lastcov2 = 0;
	String firstChrom = null;

	while (chrData.get(idx).getStream().available() > 0) {
	    icount++;
	    if (debug) {
		if (icount % 100000 == 0)
		    System.out.print(".");
		if (icount % 5000000 == 0)
		    System.out.println(icount);
	    }

	    // load data
	    String chr = chrData.get(idx).getStream().pop();
	    int diffpos = posData.get(idx).getStream().pop();
	    int cov1 = cov1Data.get(idx).getStream().pop();
	    int cov2 = cov2Data.get(idx).getStream().pop();

	    pos1 += diffpos;

	    if (firstChrom == null)
		firstChrom = chr;

	    boolean intervalFinished = false;

	    // chr change
	    if (lastchr == null || !chr.equals(lastchr)) {
		// set current position
		pos1 = diffpos + 1;

		// save original chr name
		chrNameMap.put(StringUtils.prefixedChr(chr), chr);

		if (vcf != null) {
		    // load variants and move to first variant
		    currentVariants = vcf.getVariants(chr).iterator();
		    currentVariant = positionRightAfter(currentVariants, pos1);
		}

		// save positions
		if (lastchr != null) {
		    positions.put(lastchr, cpos);
		    covPrev.put(lastchr, ccov);
		    covKeypoint.put(lastchr, ccov2);
		    cpos = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);
		    ccov = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);
		    ccov2 = new ArrayList<>(CoverageCompressor.DEFAULT_BLOCKSIZE);
		}
	    }

	    if (currentVariant != null) {
		// for every variant between here and the right-border of
		// the interval: create a span
		while (currentVariant.getPosition().longValue() <= pos1) {

		    int varPos = currentVariant.getPosition().intValue();
		    Integer varCov = currentVariant.estimateCoverage();
		    if (varCov == null)
			varCov = cov1;

		    // add codewords
		    cpos.add(varPos);
		    ccov.add(lastcov2);
		    ccov2.add(varCov);

		    // update positions
		    lastpos1 = varPos;
		    cov1 = lastcov2;
		    lastcov2 = varCov;
		    lastchr = chr;
		    if (lastpos1 + 1 >= pos1)
			intervalFinished = true;

		    // move to next variant
		    if (currentVariants.hasNext()) {
			// move to next variant with *different* coordinates
			// (in VCF, there may be multiple variants at one
			// genomic position!)
			SimpleVCFVariant nextVariant = currentVariants.next();
			while (nextVariant.getPosition().longValue() == currentVariant.getPosition().longValue()) {
			    // System.err.println("SKIP" + nextVariant);
			    if (!currentVariants.hasNext()) {
				nextVariant = null;
				break;
			    }
			    nextVariant = currentVariants.next();
			}
			currentVariant = nextVariant;
		    } else {
			currentVariant = null;
			currentVariants = null;
			break;
		    }
		} // while
	    }

	    // we need to check for the position here as variants may have
	    // ended the current interval already!
	    if (!intervalFinished) {
		// add codewords
		cpos.add(pos1);
		ccov.add(cov1);
		ccov2.add(cov2);
	    }
	    lastpos1 = pos1;
	    lastcov2 = cov2;
	    lastchr = chr;
	}

	// add last codewords
	positions.put(lastchr, cpos);
	covPrev.put(lastchr, ccov);
	covKeypoint.put(lastchr, ccov2);

	// close streams
	for (FileDataInputBlock<String> block : chrData) {
	    try {
		if (block != null)
		    block.close();
	    } catch (IOException e) {
		throw new CodocException("Cannot close block " + block, e);
	    }
	}
	for (FileDataInputBlock<Integer> block : posData) {
	    try {
		if (block != null)
		    block.close();
	    } catch (IOException e) {
		throw new CodocException("Cannot close block " + block, e);
	    }
	}
	for (FileDataInputBlock<Integer> block : cov1Data) {
	    try {
		if (block != null)
		    block.close();
	    } catch (IOException e) {
		throw new CodocException("Cannot close block " + block, e);
	    }
	}
	for (FileDataInputBlock<Integer> block : cov2Data) {
	    try {
		if (block != null)
		    block.close();
	    } catch (IOException e) {
		throw new CodocException("Cannot close block " + block, e);
	    }
	}

	if (memoryBlockCache.size() > maxCachedBlocks) {
	    memoryBlockCache.removeLast();
	    System.gc();
	}

	CachedBlock cb = new CachedBlock(idx, positions, covKeypoint, covPrev);
	memoryBlockCache.add(0, cb);

	if (debug)
	    System.out.println("CACHED BLOCK " + idx + " at " + firstChrom + ":" + cb.getFirstPos(firstChrom)
		    + " with byte size " + (MemoryUtils.getMemoryUsed() - mem) + " and " + cb.size() + " codewords in "
		    + (System.currentTimeMillis() - start) + " ms.");

	return cb;
    }

    public CachedBlock getCurrentBIT() {
	if (memoryBlockCache.size() == 0)
	    return null;
	return memoryBlockCache.getFirst();
    }

    public Integer getCurrentBlockIdx() {
	CachedBlock bit = getCurrentBIT();
	if (bit == null)
	    return null;
	return bit.getBlockIdx();
    }

    public GenomicITree getBlockIndexTree() {
	return blockIndexTree;
    }

    /**
     * Positions the iterator to the variant right after the passed position
     * 
     * @param it
     * @param pos1
     * @return
     */
    private SimpleVCFVariant positionRightAfter(Iterator<SimpleVCFVariant> it, int pos1) {
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
     * Close the decompressor.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
	if (!keepWorkDir) {
	    // delete temporary files.
	    if (header != null)
		if (!header.deleteFiles())
		    System.err.println(new IOException("Could not remove header file " + header.getF1()));
	    if (chrData != null)
		for (FileDataInputBlock<String> block : chrData) {
		    if (!block.deleteFiles())
			System.err.println(new IOException("Could not remove temporary file(s) of " + block));
		}
	    if (posData != null)
		for (FileDataInputBlock<Integer> block : posData) {
		    if (!block.deleteFiles())
			System.err.println(new IOException("Could not remove temporary file(s) of " + block));
		}
	    if (cov1Data != null)
		for (FileDataInputBlock<Integer> block : cov1Data) {
		    if (!block.deleteFiles())
			System.err.println(new IOException("Could not remove temporary file(s) of " + block));
		}
	    if (cov2Data != null)
		for (FileDataInputBlock<Integer> block : cov2Data) {
		    if (!block.deleteFiles())
			System.err.println(new IOException("Could not remove temporary file(s) of " + block));
		}
	    // delete temporary directory
	    if (workDirWasCreated)
		if ((workDir != null) && (workDir.exists()))
		    org.apache.commons.io.FileUtils.forceDelete(workDir);
	}
    }

    @Override
    protected void finalize() throws Throwable {
	close();
    }

    public QuantizationFunction getQuant() {
	return quant;
    }

    /**
     * Decompress the passed file
     * 
     * @param covFile
     *            the compressed coverage
     * @param vcfFile
     *            the VCF file
     * @param workDir
     *            the workdir (will be a random dir if null)
     * @param keepWorkDir
     *            set to true if you want to keep the working dir (e.g., for
     *            debugging)
     * @param wigFile
     *            optional output file in WIG format
     * @throws Throwable
     */
    public void decompress(PropertyConfiguration conf) throws IOException, CodocException {
	this.covFile = new File(conf.getProperty(OPT_COV_FILE));
	this.workDir = conf.hasProperty(OPT_TMP_DIR) ? new File(conf.getProperty(OPT_TMP_DIR))
		: new File(covFile.getParentFile(), "" + Math.random());
	File chrLenFile = conf.hasProperty(OPT_CHR_LEN_FILE) ? new File(conf.getProperty(OPT_CHR_LEN_FILE)) : null;
	this.keepWorkDir = conf.getBooleanProperty(OPT_KEEP_WORKDIR, false);
	this.maxCachedBlocks = Integer
		.parseInt(conf.getProperty(OPT_MAX_CACHED_BLOCKS, DEFAULT_MAX_CACHED_BLOCKS + ""));
	debug = conf.getBooleanProperty(OPT_VERBOSE, false);
	this.inMemoryBlockCaching = !conf.getBooleanProperty(OPT_NO_CACHING, false);
	if (debug) {
	    System.out.println("Caching options: In-memory BIT caching: " + inMemoryBlockCaching + ", maxCachedBlocks="
		    + maxCachedBlocks);
	}

	long startTime = System.currentTimeMillis();
	if (debug)
	    System.out.println("Decompressing " + covFile);

	chrData = new ArrayList<FileDataInputBlock<String>>();
	posData = new ArrayList<FileDataInputBlock<Integer>>();
	cov1Data = new ArrayList<FileDataInputBlock<Integer>>();
	cov2Data = new ArrayList<FileDataInputBlock<Integer>>();

	// known chrom lengths?
	this.chromLengths = new HashMap<>();
	if ((chrLenFile != null) && (chrLenFile.exists())) {
	    chromLengths = parseChromLengths(chrLenFile);
	}

	// prepare work dir
	if (!workDir.exists()) {
	    if (!workDir.mkdirs())
		throw new IOException("Could not create temporary directory " + workDir);
	    workDirWasCreated = true;
	}

	try {
	    // start
	    if (debug)
		System.out.println("Load header...");
	    this.header = FileHeader.fromFile(covFile, workDir);
	    if (debug)
		System.out.println("Finished.");
	    this.compressedConfig = header.getConfiguration();

	    // set scale factor
	    String sf = conf.getProperty(OPT_SCALE_FACTOR, "1.0");
	    if (sf.toLowerCase().startsWith("median")) {
		// get median from config.
		if (compressedConfig.getProperty(CoverageCompressor.STAT_PSEUDO_MEDIAN_AUTO) == null)
		    throw new CodocException("Stats header does not contain autosomal median. Wrong CODOC format?");
		Float median = Float
			.parseFloat(compressedConfig.getProperty(CoverageCompressor.STAT_PSEUDO_MEDIAN_AUTO));
		if (sf.startsWith("median*")) {
		    float mult = Float.parseFloat(sf.substring("median*".length()));
		    this.scaleFactor = 1 / median * mult;
		} else
		    this.scaleFactor = 1 / median;
		if (debug)
		    System.out.println("Median: " + median + ", scale: " + this.scaleFactor);
	    } else
		this.scaleFactor = Float.parseFloat(conf.getProperty(OPT_SCALE_FACTOR, "1.0"));
	    if (this.scaleFactor == 0f)
		throw new CodocException("Scale factor is zero.");
	    if (this.scaleFactor == Float.POSITIVE_INFINITY || this.scaleFactor == Float.NEGATIVE_INFINITY)
		throw new CodocException("Infinite scale factor not allowed.");
	    if (this.scaleFactor == 0)
		throw new CodocException("Zero scale factor not allowed.");

	    // set vcf file
	    if (conf.hasProperty(OPT_VCF_FILE) && conf.getProperty(OPT_VCF_FILE).equalsIgnoreCase("AUTO")) {
		// get from compressed file.
		this.vcfFile = (compressedConfig.hasProperty(CoverageCompressor.OPT_VCF_FILE))
			? new File(compressedConfig.getProperty(CoverageCompressor.OPT_VCF_FILE)) : null;
	    } else
		this.vcfFile = conf.hasProperty(OPT_VCF_FILE) ? new File(conf.getProperty(OPT_VCF_FILE)) : null;

	    // Check whether file was compressed with VCF knotpoints
	    if (compressedConfig.hasProperty(CoverageCompressor.OPT_VCF_FILE) && vcfFile == null)
		System.err.println(new CodocException("NOTE: that file was compressed with the VCF file "
			+ compressedConfig.getProperty(CoverageCompressor.OPT_VCF_FILE)
			+ " but decompression was called without providing this file. Resulting DOC values might be inaccurate around variant positions."));

	    // create streams.
	    if (debug)
		System.out.println("Create streams from blocks:" + header.getBlocks());
	    for (FileDataOutputBlock<?> b : header.getBlocks()) {
		if (b.getId().equals(STANDARD_STREAM.CHR.name())) {
		    FileDataInputBlock<String> block = new FileDataInputBlock<String>(b.getId(), b.getF1());
		    block.setCompressionMethod(AbstractDataBlock
			    .compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
		    chrData.add(block);
		} else if (b.getId().equals(STANDARD_STREAM.POS.name())) {
		    FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
		    block.setCompressionMethod(AbstractDataBlock
			    .compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
		    posData.add(block);
		} else if (b.getId().equals(STANDARD_STREAM.COV1.name())) {
		    FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
		    block.setCompressionMethod(AbstractDataBlock
			    .compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
		    cov1Data.add(block);
		} else if (b.getId().equals(STANDARD_STREAM.COV2.name())) {
		    FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
		    block.setCompressionMethod(AbstractDataBlock
			    .compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
		    cov2Data.add(block);
		} else
		    throw new IOException("Unknown block id " + b.getId());
		b.close();
	    }

	    // set quantization method
	    this.quant = CoverageCompressor.instantiateQuantizationFunction(this.compressedConfig);

	    // VCF file
	    if (vcfFile != null) {
		this.vcf = new SimpleVCFFile(vcfFile);
	    }

	    // load list of original chromosomes
	    chrOrigList = new ArrayList<>();
	    String[] bb = compressedConfig.getProperty(CoverageCompressor.OPT_CHR_LIST).split(",");
	    if (debug)
		System.out.println("Original-chromosomes:" + Arrays.toString(bb));
	    for (String c : bb)
		chrOrigList.add(c);

	    // build block index tree
	    blockIndexTree = new GenomicITree(null);
	    leftBorders = new HashMap<>();
	    bb = compressedConfig.getProperty(CoverageCompressor.OPT_BLOCK_BORDERS).split(",");
	    if (debug)
		System.out.println("Block-borders:" + Arrays.toString(bb));
	    if (bb.length < 2)
		throw new CodocException("Found less than 1 block!");
	    for (int i = 1; i < bb.length; i++) {
		int blockId = (i - 1);
		GenomicPosition p1 = new GenomicPosition(bb[i - 1].split(":")[0], new Long(bb[i - 1].split(":")[1]));
		GenomicPosition p2 = new GenomicPosition(bb[i].split(":")[0], new Long(bb[i].split(":")[1]) - 1);
		if (!p1.getChromosome().equals(p2.getChromosome())) {
		    // create interval from p1 to end of chrom
		    GenomicInterval gi1 = new GenomicInterval(p1.getChromosome(), p1.get1Position(), Long.MAX_VALUE,
			    "" + blockId);
		    gi1.setOriginalChrom(p1.getChromosomeOriginal());
		    gi1.setAnnotation("blockid", blockId);
		    blockIndexTree.insert(gi1);

		    // create intervals for all chroms between p1 and p2
		    int cp1 = chrOrigList.indexOf(p1.getChromosomeOriginal());
		    int cp2 = chrOrigList.indexOf(p2.getChromosomeOriginal());
		    if (cp1 < 0)
			throw new RuntimeException("chrom [" + p1.getChromosomeOriginal() + "] was not found in "
				+ Arrays.toString(chrOrigList.toArray()));
		    if (cp2 < 0)
			throw new RuntimeException("chrom " + p2.getChromosomeOriginal() + " was not found in "
				+ Arrays.toString(chrOrigList.toArray()));
		    for (int x = cp1 + 1; x < cp2; x++) {
			GenomicInterval giAdditional = new GenomicInterval(StringUtils.prefixedChr(chrOrigList.get(x)),
				0l, Long.MAX_VALUE, "" + blockId);
			giAdditional.setAnnotation("blockid", blockId);
			giAdditional.setOriginalChrom(chrOrigList.get(x));
			blockIndexTree.insert(giAdditional);
		    }
		    // create interval from start of chrom to p2
		    GenomicInterval gi2 = new GenomicInterval(p2.getChromosome(), 0l, p2.get1Position(), "" + blockId);
		    gi2.setAnnotation("blockid", blockId);
		    gi2.setOriginalChrom(p2.getChromosomeOriginal());
		    blockIndexTree.insert(gi2);
		} else {
		    GenomicInterval gi1 = new GenomicInterval(p1.getChromosome(), p1.get1Position(), p2.get1Position(),
			    "" + blockId);
		    gi1.setAnnotation("blockid", blockId);
		    gi1.setOriginalChrom(p1.getChromosomeOriginal());
		    blockIndexTree.insert(gi1);
		}
		leftBorders.put(blockId, p1);
	    }
	    blockIndexTree.buildTree();
	    if (debug) {
		for (GenomicInterval gi : blockIndexTree.getIntervalsSorted())
		    System.out.println(gi + "->" + gi.getAnnotation("blockid"));
	    }
	    if (debug)
		System.out.println("Finished loading in " + (System.currentTimeMillis() - startTime) + "ms.");
	} catch (Exception e) {
	    // delete temporary directory
	    if (workDirWasCreated) {
		System.err.println("Removing work dir " + workDir.getAbsolutePath());
		if (!workDir.delete())
		    System.err.println("Could not remove temporary directory " + workDir);
	    }
	    throw new CodocException(e.toString());
	}

    }

    /**
     * 
     * @return a coverage iterator.
     * @throws IOException
     * @throws CodocException
     * @throws Throwable
     */
    public CompressedCoverageIterator getCoverageIterator() throws CodocException, IOException {
	return new CompressedCoverageIterator(this, chrOrigList, scaleFactor);
    }

    /**
     * 
     * @return a coverage iterator.
     * @throws IOException
     * @throws CodocException
     * @throws Throwable
     */
    public CompressedCoverageIterator getCoverageIterator(GenomicPosition pos) throws CodocException, IOException {
	CompressedCoverageIterator it = new CompressedCoverageIterator(this, chrOrigList, scaleFactor);
	while (it.hasNext()) {
	    it.next();
	    if (it.getGenomicPosition() != null)
		if (it.getGenomicPosition().compareTo(pos) > 0)
		    return it;
	}
	return null;
    }

    public File getCovFile() {
	return covFile;
    }

    public void setCovFile(File covFile) {
	this.covFile = covFile;
    }

    public File getVcfFile() {
	return vcfFile;
    }

    public void setVcfFile(File vcfFile) {
	this.vcfFile = vcfFile;
    }

    public Map<Integer, GenomicPosition> getLeftBorders() {
	return leftBorders;
    }

    public int getMaxCachedBlocks() {
	return maxCachedBlocks;
    }

    public void setMaxCachedBlocks(int maxCachedBlocks) {
	this.maxCachedBlocks = maxCachedBlocks;
    }

    /**
     * Starts an interactive query session.
     * 
     * @throws Throwable
     */
    public void interactiveQuerying() throws Throwable {
	String cmd = null;
	System.out.println("Decompressed " + getCovFile());
	System.out.println("Enter '?' to get a list of available chromosomes and 'q' to quit.");

	do {
	    try {
		System.out.print("Enter position (chr:pos) : ");
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		cmd = input.readLine();

		if (cmd.equalsIgnoreCase("q"))
		    break;

		if (cmd.equalsIgnoreCase("?")) {
		    System.out.println("chr:\t" + chrOrigList);
		    continue;
		}

		String chr = cmd.split(":")[0].trim();
		Integer off1 = new Integer(cmd.split(":")[1].replaceAll(",", ""));
		GenomicPosition pos = new GenomicPosition(chr, off1, COORD_TYPE.ONEBASED);
		// System.out.println("Query " + pos);

		CoverageHit hit = query(pos);
		if (hit == null)
		    System.out.println("cov:\tN/A");
		else {
		    if (hit.isFuzzy()) {
			System.out.println("cov:\t" + hit.getInterpolatedCoverage() + " [" + hit.getLowerBoundary()
				+ "-" + hit.getUpperBoundary() + "]");
		    } else
			System.out.println("cov:\t" + hit.getInterpolatedCoverage());

		    System.out.println("\tinfo:\t" + hit);

		    System.out.println("\ttime:\t" + (hit.getExecTime() / 1000) + "micros.");
		}

	    } catch (NumberFormatException e0) {
		System.err.println("Not a number!");
		System.err.println();
	    } catch (Exception e) {
		e.printStackTrace();
		System.err.println("Error: " + e);
	    }

	} while (!cmd.equalsIgnoreCase("q"));
	System.out.println("Good Bye.");
    }

    /**
     * Determines whether the passed position is in the padding regions.
     * 
     * @param pos
     * @return
     */
    public CoverageHit queryPadding(GenomicPosition pos) {
	Long len = chromLengths.get(StringUtils.prefixedChr(pos.getChromosome()));
	if (len != null && pos.get1Position() > 0 && pos.get1Position() < len) {
	    CoverageHit hit = new CoverageHit(pos.getChromosome(), (int) pos.get1Position(), (int) pos.get1Position(),
		    0, 0, (int) pos.get1Position(), scaleFactor);
	    hit.setLowerBoundary(0f);
	    hit.setUpperBoundary(0f);
	    hit.setPadding(true);
	    return hit;
	}
	return null;
    }

    /**
     * Query.
     * 
     * @param pos
     * @return
     * @throws CodocException
     * @throws IOException
     * @throws Throwable
     */
    public CoverageHit query(GenomicPosition pos) throws IOException, CodocException {

	Set<? extends GenomicInterval> blocks = blockIndexTree.query1based(pos);
	if ((blocks == null) || (blocks.size() != 1)) {
	    return queryPadding(pos);
	}
	GenomicInterval gi = blocks.iterator().next();

	if (debug)
	    System.out.println(
		    "query " + pos.toString1basedOrig() + " => load block " + gi + " / " + gi.getAnnotation("blockid"));

	CachedBlock cb = loadBlock((Integer) gi.getAnnotation("blockid"));

	CoverageHit hit = cb.query(pos, scaleFactor);
	if (hit == null) {
	    return queryPadding(pos);
	}
	hit.decorateWithBoundaries(getQuant());

	return hit;
    }

    /**
     * Queries the current data and returns an interval tree containing regions
     * where the (interpolated) coverage is between the given thresholds.
     * Providing null for a threshold means that it will be ignored.
     * 
     * Example: minCoverage=null, maxCoverage=0 => return all uncovered regions
     * Example: minCoverage=1, maxCoverage=null => return all covered regions
     * 
     * Note that only the positions "covered" by the decompressed data will be
     * considered.
     * 
     * @param minCoverage
     * @param maxCoverage
     * @return
     * @throws Throwable
     */
    public GenomicITree regionsByCoverage(Integer minCoverage, Integer maxCoverage) throws Throwable {
	if (minCoverage == null)
	    minCoverage = 0;
	if (maxCoverage == null)
	    maxCoverage = Integer.MAX_VALUE;
	GenomicITree ret = new GenomicITree(null);
	CompressedCoverageIterator it = getCoverageIterator();
	int rc = 0;
	GenomicPosition startPos = null, lastPos = null;
	while (it.hasNext()) {
	    Float h = it.next();
	    int cov = (h != null ? Math.round(h) : 0);
	    GenomicPosition pos = it.getGenomicPosition();
	    boolean inCorridor = (cov >= minCoverage) && (cov <= maxCoverage);
	    if (startPos == null) {
		if (inCorridor) {
		    // start interval
		    startPos = pos;
		}
	    } else {
		if ((!pos.getChromosome().equals(startPos.getChromosome())) && (lastPos != null)) {
		    // end interval due to chrom change
		    GenomicInterval gi = new GenomicInterval(startPos.getChromosome(), startPos.get0Position(),
			    lastPos.get0Position(), "region" + (rc++));
		    gi.setOriginalChrom(startPos.getChromosomeOriginal());
		    ret.insert(gi);
		    if (inCorridor)
			startPos = pos;
		    else
			startPos = null;
		} else if (!inCorridor) {
		    // end interval as corridor was left
		    GenomicInterval gi = new GenomicInterval(pos.getChromosome(), startPos.get0Position(),
			    lastPos.get0Position(), "region" + (rc++));
		    gi.setOriginalChrom(pos.getChromosomeOriginal());
		    ret.insert(gi);
		    startPos = null;
		}
	    }
	    lastPos = pos;
	}
	if (startPos != null) {
	    // end last interval
	    GenomicInterval gi = new GenomicInterval(lastPos.getChromosome(), startPos.get0Position(),
		    lastPos.get0Position(), "region" + (rc++));
	    gi.setOriginalChrom(lastPos.getChromosomeOriginal());
	    ret.insert(gi);

	}
	return ret;
    }

    /**
     * Parse chromosome lengths from a TSV file.
     * 
     * @param chrLen
     * @return
     * @throws IOException
     */
    private Map<String, Long> parseChromLengths(File chrLen) throws IOException {
	Map<String, Long> ret = new HashMap<String, Long>();
	TabIterator ti = new TabIterator(chrLen, "#");
	while (ti.hasNext()) {
	    String[] t = ti.next();
	    ret.put(StringUtils.prefixedChr(t[0]), new Long(t[1]));
	}
	return ret;
    }

    /**
     * Queries the data to extract regions that match the passed coverage
     * thresholds and writes the data to a single-track BED file. The intervals
     * are returned as 0-based entries in a genomic interval tree.
     * 
     * @param bedOutFile
     *            may be null
     * @param minCoverage
     *            minimum coverage
     * @param maxCoverage
     *            maximum coverage
     * @throws Throwable
     */
    @Deprecated
    public GenomicITree toBEDOLD(File bedOutFile, Integer minCoverage, Integer maxCoverage, String name,
	    String description, String additional, boolean noHeader) throws Throwable {

	PrintStream bedOut = bedOutFile != null ? new PrintStream(bedOutFile) : null;
	if (name == null)
	    name = "CoverageRegions";
	if (description == null)
	    description = "created by CoverageDecompressor.toBED(min=" + (minCoverage == null ? "unbound" : minCoverage)
		    + ",max=" + (maxCoverage == null ? "unbound" : maxCoverage) + ")";
	if (additional == null)
	    additional = "";
	GenomicITree regionTree = regionsByCoverage(minCoverage, maxCoverage);
	// regionTree.dump();

	if (bedOut != null) {
	    if (!noHeader)
		bedOut.println("track name=" + name + " description=\"" + description + "\" " + additional);
	    for (GenomicInterval gi : regionTree.getIntervalsSorted()) {
		bedOut.println(gi.toBED());
	    }
	    bedOut.close();
	}
	regionTree.buildTree();

	return regionTree;
    }

    /**
     * Queries the data to extract regions that match the passed coverage
     * thresholds and writes the data to a single-track BED file. The intervals
     * are returned as 0-based entries in a genomic interval tree.
     * 
     * @param bedOutFile
     *            may be null
     * @param minCoverage
     *            minimum coverage
     * @param maxCoverage
     *            maximum coverage
     * @throws Throwable
     */
    public void toBED(File bedOutFile, Integer minCoverage, Integer maxCoverage, String name, String description,
	    String additional, boolean noHeader) throws Throwable {

	if (minCoverage == null)
	    minCoverage = 0;
	if (maxCoverage == null)
	    maxCoverage = Integer.MAX_VALUE;

	BufferedWriter bedOut = new BufferedWriter(new FileWriter(bedOutFile));
	if (name == null)
	    name = "CoverageRegions";
	if (description == null)
	    description = "created by CoverageDecompressor.toBED(min=" + (minCoverage == null ? "unbound" : minCoverage)
		    + ",max=" + (maxCoverage == null ? "unbound" : maxCoverage) + ")";
	if (additional == null)
	    additional = "";
	if (!noHeader)
	    bedOut.append("track name=" + name + " description=\"" + description + "\" " + additional + "\n");

	CompressedCoverageIterator it = getCoverageIterator();
	int rc = 0;
	GenomicPosition startPos = null, lastPos = null;
	while (it.hasNext()) {
	    Float h = it.next();
	    int cov = (h != null ? Math.round(h) : 0);
	    GenomicPosition pos = it.getGenomicPosition();
	    boolean inCorridor = (cov >= minCoverage) && (cov <= maxCoverage);
	    if (startPos == null) {
		if (inCorridor) {
		    // start interval
		    startPos = pos;
		}
	    } else {
		if ((!pos.getChromosome().equals(startPos.getChromosome())) && (lastPos != null)) {
		    // end interval due to chrom change
		    GenomicInterval gi = new GenomicInterval(startPos.getChromosome(), startPos.get0Position(),
			    lastPos.get0Position(), "region" + (rc++));
		    gi.setOriginalChrom(startPos.getChromosomeOriginal());

		    bedOut.append(gi.toBED() + "\n");

		    if (inCorridor)
			startPos = pos;
		    else
			startPos = null;
		} else if (!inCorridor) {
		    // end interval as corridor was left
		    GenomicInterval gi = new GenomicInterval(pos.getChromosome(), startPos.get0Position(),
			    lastPos.get0Position(), "region" + (rc++));
		    gi.setOriginalChrom(pos.getChromosomeOriginal());

		    bedOut.append(gi.toBED() + "\n");

		    startPos = null;
		}
	    }
	    lastPos = pos;
	}
	if (startPos != null) {
	    // end last interval
	    GenomicInterval gi = new GenomicInterval(lastPos.getChromosome(), startPos.get0Position(),
		    lastPos.get0Position(), "region" + (rc++));
	    gi.setOriginalChrom(lastPos.getChromosomeOriginal());

	    bedOut.append(gi.toBED() + "\n");

	}
	bedOut.close();
    }

    /**
     * @return the default (empty) configuration.
     */
    public static PropertyConfiguration getDefaultConfiguration() {
	StringBuffer sb = new StringBuffer();

	// set default compression algorithm
	sb.append("version=" + Main.VERSION + "\n");

	return PropertyConfiguration.fromString(sb.toString());
    }

    /**
     * Convenience method.
     * 
     * @param covFile
     * @param vcfFile
     * @return
     * @throws IOException
     * @throws CodocException
     * @throws Throwable
     */
    public static CoverageDecompressor decompress(File covFile, File vcfFile) throws CodocException, IOException {
	PropertyConfiguration conf = CoverageDecompressor.getDefaultConfiguration();
	conf.setProperty(OPT_COV_FILE, covFile.getAbsolutePath());
	if (vcfFile != null)
	    conf.setProperty(OPT_VCF_FILE, vcfFile.getAbsolutePath());
	CoverageDecompressor decompressor = new CoverageDecompressor(conf);
	return decompressor;
    }

    /**
     * @return the original chromosome names
     */
    public List<String> getChromosomes() {
	return chrOrigList;
    }

    /**
     * 
     * @return the prefixed chromosome names
     */
    public List<String> getPrefixedChromosomes() {
	List<String> ret = new ArrayList<String>();
	for (String c : chrOrigList)
	    ret.add(StringUtils.prefixedChr(c));
	return ret;
    }

    /**
     * @param chr
     * @return the estimated length of the passed chromosome
     */
    public Long getChromosomeLength(String chr) {
	return chromLengths.get(StringUtils.prefixedChr(chr));
    }

    
    private void toWIG(String wigFile, SortedSet<GenomicInterval> regionsOfInterest) throws Throwable {
	toWIG( wigFile, compressedConfig.getProperty(CoverageCompressor.OPT_OUT_FILE), regionsOfInterest);
    }
    
    /**
     * Dump the coverage to a WIG file.
     * 
     * @param wigFile
     * @throws Throwable
     */
    private void toWIG(String wigFile, String trackTitle, SortedSet<GenomicInterval> regionsOfInterest) throws Throwable {
	WigOutputStream out = new WigOutputStream(new PrintStream(wigFile),
		trackTitle,
		"WIG track created by CODOC CoverageDecompressor", "");
	CompressedCoverageIterator it = getCoverageIterator();
	while (it.hasNext()) {
	    Float hit = it.next();
	    if (hit == null)
		hit = 0f;
	    // This is done as normalized chr names are used internally
	    GenomicPosition pos = it.getGenomicPosition();
	    if (regionsOfInterest != null) {
		boolean found = false;
		for (GenomicInterval g : regionsOfInterest)
		    if (g.contains(pos))
			found = true;
		if (!found)
		    continue;

	    }

	    String origChr = chrNameMap.get(pos.getChromosome());
	    if (origChr != null)
		pos.setScaffold(origChr);
	    out.push(pos, Math.round(hit));

	    // break if we went beyond the last considered ROI
	    if (regionsOfInterest != null)
		if (pos.compareTo(regionsOfInterest.last().getRightPosition()) > 0)
		    break;
	}
	out.close();
    }

    /**
     * Dump the coverage to a TDF file.
     * 
     * @param wigFile
     * @throws Throwable
     */
    private void toTDF(String tdfFile, SortedSet<GenomicInterval> regionsOfInterest, String chrSizeF) throws Throwable {
	// write to a temporary WIG file
	toWIG(tdfFile + ".wig", regionsOfInterest);

	if (!chrSizeF.equals(".chrom.sizes"))
	    System.err.println("Chromosome size file should have suffix '.chrom.sizes' to be IGV-compatible!");

	// close writers and convert to TDF
	Globals.setHeadless(true);
	int maxZoomValue = IgvTools.MAX_ZOOM;
	String probeFile = null;
	String tmpDirName = null;
	// new IgvTools().toTDF("wig", tdfFile + ".wig", tdfFile, probeFile,
	// chrSizeF, maxZoomValue,
	// Arrays.asList(WindowFunction.max, WindowFunction.min,
	// WindowFunction.median), tmpDirName,
	// IgvTools.MAX_RECORDS_IN_RAM);
	new IgvTools().toTDF("wig", tdfFile + ".wig", tdfFile, probeFile, chrSizeF, maxZoomValue,
		Arrays.asList(WindowFunction.max, WindowFunction.min, WindowFunction.median), tmpDirName,
		IgvTools.MAX_RECORDS_IN_RAM);
	// remove wig file
	new File(tdfFile + ".wig").delete();
	if (debug)
	    System.out.println("Finished.");
    }

    /**
     * Extracts the histogram and some other stats as JSON object to the passed
     * file
     * 
     * @throws IOException
     * @throws CodocException
     */
    public void extractHist(File outFile) throws IOException {
	String hautoStr = getCompressionParameter(CoverageCompressor.STAT_HIST_AUTO);
	if (hautoStr == null)
	    throw new NullPointerException("No autosome histogram found! " + getCompressedConfig());
	String hxStr = getCompressionParameter(CoverageCompressor.STAT_HIST_X);
	if (hxStr == null)
	    throw new NullPointerException("No X-histogram found! " + getCompressedConfig());
	String hyStr = getCompressionParameter(CoverageCompressor.STAT_HIST_Y);
	if (hyStr == null)
	    throw new NullPointerException("No Y-histogram found! " + getCompressedConfig());

	String medAuto = getCompressionParameter(CoverageCompressor.STAT_PSEUDO_MEDIAN_AUTO);
	if (medAuto == null)
	    throw new NullPointerException("No autosome median found!" + getCompressedConfig());
	String medX = getCompressionParameter(CoverageCompressor.STAT_PSEUDO_MEDIAN_X);
	if (medX == null)
	    throw new NullPointerException("No X median found!" + getCompressedConfig());
	String medY = getCompressionParameter(CoverageCompressor.STAT_PSEUDO_MEDIAN_Y);
	if (medY == null)
	    throw new NullPointerException("No Y median found!" + getCompressedConfig());

	Histogram<Integer> hauto = Histogram.fromString(hautoStr);
	Histogram<Integer> hx = Histogram.fromString(hxStr);
	Histogram<Integer> hy = Histogram.fromString(hyStr);

	JSONObject obj = new JSONObject();
	obj.put("histogram-autosomes", hauto.toJSON());
	obj.put("histogram-X", hx.toJSON());
	obj.put("histogram-Y", hy.toJSON());
	obj.put("median-autosomes", Long.parseLong(medAuto));
	obj.put("median-X", Long.parseLong(medX));
	obj.put("median-Y", Long.parseLong(medY));

	PrintWriter out = new PrintWriter(outFile);
	out.print(obj.toString());
	out.close();
    }

    /**
     * Print usage information.
     * 
     * @param options
     */
    private static void usage(Options options, CMD_COMMANDS subcommand, String e) {

	if (subcommand == null) {
	    System.out.println("Usage:\t\tjava -jar x.jar " + CMD + " <command> [options]:\t");
	    System.out.println();
	    System.out.println("Command:\t" + CMD_COMMANDS.QUERY + "\tinteractive querying");
	    System.out.println("Command:\t" + CMD_COMMANDS.HEAD + "\tprint header");
	    System.out.println("Command:\t" + CMD_COMMANDS.TOWIG + "\tconvert to WIG file");
	    System.out.println("Command:\t" + CMD_COMMANDS.TOBED + "\tconvert to BED file");
	    System.out.println("Command:\t" + CMD_COMMANDS.TOTDF + "\tconvert to TDF file");
	    System.out.println("Command:\t" + CMD_COMMANDS.HIST + "\tExtract coverage histogram");
	    System.out.println();
	} else {
	    HelpFormatter hf = new HelpFormatter();
	    hf.setLeftPadding(10);
	    hf.setDescPadding(2);
	    hf.setWidth(160);
	    hf.setSyntaxPrefix("Usage:    ");
	    String footer = "\n";
	    switch (subcommand) {
	    case HEAD:
		footer += "Prints the header from a compressed coverage file.";
		break;
	    case QUERY:
		footer += "Starts an interactive query session.";
		break;
	    case TOBED:
		footer += "Creates a BED file containing regions that are above/below given coverage thresholds.";
		break;
	    case TOWIG:
		footer += "Converts the coverage data to a WIG file.";
		break;
	    case TOTDF:
		footer += "Converts the coverage data to a TDF file.";
		break;
	    case HIST:
		footer += "Extracts a JSON file containing a coverage histogram + other summary statistics.";
		break;
	    default:
		break;
	    }
	    hf.printHelp("java -jar x.jar " + CMD + " " + subcommand, "Params:", options, footer, true);

	}
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

	// args = new String[] { "TOTDF", "-cov",
	// "src/test/resources/covcompress/small.compressed", "-chrLen",
	// "T:/Niko/hs37d5.fa.chrSizes.chrom.sizes", "-o",
	// "src/test/resources/covcompress/small.compressed.MED.tdf", "-scale",
	// "median*100", "-v" };

	// args = new String[] { "TOTDF", "-cov",
	// "src/test/resources/covcompress/small.compressed", "-chrLen",
	// "T:/Niko/hs37d5.fa.chrSizes.chrom.sizes", "-o",
	// "src/test/resources/covcompress/small.compressed.tdf", "-v" };

	CommandLineParser parser = new PosixParser();

	// create the Options
	Options options = new Options();
	options.addOption("h", "help", false, "Print this usage information.");
	CMD_COMMANDS subcommand = null;

	try {

	    // parse the command line arguments
	    CommandLine line = parser.parse(options, args, true);

	    // validate that block-size has been set
	    if (line.getArgs().length == 0 || line.hasOption("h")) {
		usage(options, null, null);
	    }

	    subcommand = (CMD_COMMANDS) StringUtils.findInEnum(line.getArgs()[0], CMD_COMMANDS.values());
	    // usage
	    if (line.hasOption("h")) {
		usage(options, null, null);
	    }
	    CoverageDecompressor decompressor = null;

	    if (subcommand == null) {
		usage(options, null, "Unknown command " + line.getArgs()[0]);
	    } else
		switch (subcommand) {
		case HEAD:
		    options = new Options();

		    Option opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    line = parser.parse(options, args);

		    PropertyConfiguration conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    conf.setProperty(OPT_DUMP_HEADER_AND_EXIT, "true");

		    try {
			decompressor = new CoverageDecompressor(conf);
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case TOWIG:
		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_OUT_FILE, true,
			    "Output file that will contain the rounded approximated coverage values (optional).");
		    opt.setLongOpt("outFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_CHR_LEN_FILE, true,
			    "Chromosome lengths (file format: chr\\tlength\\n). Use to get proper padding for zero-coverage regions.");
		    opt.setLongOpt("chrLenFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_SCALE_FACTOR, true,
			    "Signal scaling factor. Float number or 'median*X' for scaling to the median of the autosomal coverage times X (default is 1.0).");
		    opt.setLongOpt("scaleFactor");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_ROI, true,
			    "Optional regions of interest. WIG signal will be extracted only for these intervals, e.g.: 1:100-200 (default: NONE).");
		    opt.setLongOpt("regionOfInterest");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));

		    if (line.hasOption(OPT_CHR_LEN_FILE))
			conf.setProperty(OPT_CHR_LEN_FILE, line.getOptionValue(OPT_CHR_LEN_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_SCALE_FACTOR))
			conf.setProperty(OPT_SCALE_FACTOR, line.getOptionValue(OPT_SCALE_FACTOR));
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    SortedSet<GenomicInterval> rois = line.hasOption(OPT_ROI) ? new TreeSet<GenomicInterval>() : null;
		    if (rois != null)
			for (String s : line.getOptionValues(OPT_ROI)) {
			    GenomicInterval g = GenomicInterval.fromString(s);
			    // move 1bp downstream to match 1-based coords
			    GenomicInterval g1 = new GenomicInterval(g.getChr(), g.getMin() - 1, g.getMax() - 1, null);
			    rois.add(g1);
			}
		    try {
			decompressor = new CoverageDecompressor(conf);
			decompressor.toWIG(line.getOptionValue(OPT_OUT_FILE), rois);
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case TOTDF:
		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_OUT_FILE, true,
			    "Output file that will contain the rounded approximated coverage values (optional).");
		    opt.setLongOpt("outFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_CHR_LEN_FILE, true,
			    "Chromosome lengths (file format: chr\\tlength\\n). Use to get proper padding for zero-coverage regions.");
		    opt.setLongOpt("chrLenFile");
		    opt.setRequired(true); // mandatory for toTDF!
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_SCALE_FACTOR, true,
			    "Signal scaling factor. Float number or 'median*X' for scaling to the median of the autosomal coverage times X (default is 1.0).");
		    opt.setLongOpt("scaleFactor");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_ROI, true,
			    "Optional regions of interest. Coverage signal will be extracted only for these intervals, e.g.: 1:100-200,2:200-300 (using 1-based coordinates) or an existing bed file (default: NONE).");
		    opt.setLongOpt("regionOfInterest");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));

		    if (line.hasOption(OPT_CHR_LEN_FILE))
			conf.setProperty(OPT_CHR_LEN_FILE, line.getOptionValue(OPT_CHR_LEN_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_SCALE_FACTOR))
			conf.setProperty(OPT_SCALE_FACTOR, line.getOptionValue(OPT_SCALE_FACTOR));
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    rois = line.hasOption(OPT_ROI) ? new TreeSet<GenomicInterval>() : null;
		    if (rois != null)
			for (String s : line.getOptionValues(OPT_ROI)) {
			    if (new File(s).exists()) {
				for (GenomicInterval g : (new SimpleBEDFile(new File(s))).getIntervalsList()) {
				    rois.add(g);
				}
			    } else {
				GenomicInterval g = GenomicInterval.fromString(s);
				if (g == null)
				    throw new CodocException("Could not parse ROI from string " + s);
				// move 1bp downstream to match 1-based coords
				GenomicInterval g1 = new GenomicInterval(g.getChr(), g.getMin() - 1, g.getMax() - 1,
					null);
				rois.add(g1);
			    }
			}
		    try {
			decompressor = new CoverageDecompressor(conf);
			decompressor.toTDF(line.getOptionValue(OPT_OUT_FILE), rois,
				line.getOptionValue(OPT_CHR_LEN_FILE));
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case TOBED:
		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_OUT_FILE, true, "Output file that will contain the BED intervals.");
		    opt.setLongOpt("outFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_CHR_LEN_FILE, true,
			    "Chromosome lengths (file format: chr\\tlength\\n). Use to get proper padding for zero-coverage regions.");
		    opt.setLongOpt("chrLenFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option("min", true, "minimum coverage (omit to leave unrestricted).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option("max", true, "maximum coverage (omit to leave unrestricted).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option("name", true, "BED track name (optional).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option("description", true, "BED description (optional).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option("additional", true, "Additional BED track info (optional).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption("noHeader", false, "If set, no BED track header will be written.");

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    opt = new Option(OPT_SCALE_FACTOR, true,
			    "Signal scaling factor. Float number or 'median*X' for scaling to the median of the autosomal coverage times X (default is 1.0).");
		    opt.setLongOpt("scaleFactor");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));
		    if (line.hasOption(OPT_CHR_LEN_FILE))
			conf.setProperty(OPT_CHR_LEN_FILE, line.getOptionValue(OPT_CHR_LEN_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_SCALE_FACTOR))
			conf.setProperty(OPT_SCALE_FACTOR, line.getOptionValue(OPT_SCALE_FACTOR));
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    Integer min = line.hasOption("min") ? new Integer(line.getOptionValue("min")) : null;
		    Integer max = line.hasOption("max") ? new Integer(line.getOptionValue("max")) : null;

		    try {
			decompressor = new CoverageDecompressor(conf);
			decompressor.toBED(new File(line.getOptionValue(OPT_OUT_FILE)), min, max,
				line.getOptionValue("name"), line.getOptionValue("description"),
				line.getOptionValue("additional"), line.hasOption("noHeader"));
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case QUERY:

		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_CHR_LEN_FILE, true,
			    "Chromosome lengths (file format: chr\\tlength\\n). Use to get proper padding for zero-coverage regions.");
		    opt.setLongOpt("chrLenFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    opt = new Option(OPT_SCALE_FACTOR, true,
			    "Signal scaling factor. Float number or 'median*X' for scaling to the median of the autosomal coverage times X (default is 1.0).");
		    opt.setLongOpt("scaleFactor");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));
		    if (line.hasOption(OPT_CHR_LEN_FILE))
			conf.setProperty(OPT_CHR_LEN_FILE, line.getOptionValue(OPT_CHR_LEN_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_SCALE_FACTOR))
			conf.setProperty(OPT_SCALE_FACTOR, line.getOptionValue(OPT_SCALE_FACTOR));
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    try {
			decompressor = new CoverageDecompressor(conf);
			decompressor.interactiveQuerying();
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case HIST:

		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file. NOTE: this method works for human data only!");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_OUT_FILE, true, "Output TSV file.");
		    opt.setLongOpt("outFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_CHR_LEN_FILE, true,
			    "Chromosome lengths (file format: chr\\tlength\\n). Use to get proper padding for zero-coverage regions.");
		    opt.setLongOpt("chrLenFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    opt = new Option(OPT_SCALE_FACTOR, true,
			    "Signal scaling factor. Float number or 'median*X' for scaling to the median of the autosomal coverage times X (default is 1.0).");
		    opt.setLongOpt("scaleFactor");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));
		    if (line.hasOption(OPT_CHR_LEN_FILE))
			conf.setProperty(OPT_CHR_LEN_FILE, line.getOptionValue(OPT_CHR_LEN_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_SCALE_FACTOR))
			conf.setProperty(OPT_SCALE_FACTOR, line.getOptionValue(OPT_SCALE_FACTOR));
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    try {
			decompressor = new CoverageDecompressor(conf);
			decompressor.extractHist(new File(line.getOptionValue(OPT_OUT_FILE)));
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		case DEBUG:

		    options = new Options();

		    opt = new Option(OPT_COV_FILE, true, "Compressed coverage input file.");
		    opt.setLongOpt("covFile");
		    opt.setRequired(true);
		    options.addOption(opt);

		    opt = new Option(OPT_VCF_FILE, true, "VCF file used for the compression (optional).");
		    opt.setLongOpt("vcfFile");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_TMP_DIR, true, "Temporary working directory (default is current dir).");
		    opt.setLongOpt("temp");
		    opt.setRequired(false);
		    options.addOption(opt);

		    opt = new Option(OPT_KEEP_WORKDIR, false,
			    "Do not delete the work directory (e.g., for debugging purposes).");
		    opt.setRequired(false);
		    options.addOption(opt);

		    options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

		    opt = new Option(OPT_MAX_CACHED_BLOCKS, true,
			    "Maximum number of memory-cached data blocks (BITs) (default is "
				    + DEFAULT_MAX_CACHED_BLOCKS + ").");
		    opt.setLongOpt("maxCachedBlocks");
		    opt.setRequired(false);
		    options.addOption(opt);

		    line = parser.parse(options, args);

		    conf = CoverageDecompressor.getDefaultConfiguration();
		    conf.setProperty(OPT_COV_FILE, line.getOptionValue(OPT_COV_FILE));
		    if (line.hasOption(OPT_VCF_FILE))
			conf.setProperty(OPT_VCF_FILE, line.getOptionValue(OPT_VCF_FILE));
		    if (line.hasOption(OPT_TMP_DIR))
			conf.setProperty(OPT_TMP_DIR, line.getOptionValue(OPT_TMP_DIR));
		    conf.setProperty(OPT_KEEP_WORKDIR, line.hasOption(OPT_KEEP_WORKDIR) + "");
		    conf.setProperty(OPT_VERBOSE, line.hasOption(OPT_VERBOSE) + "");
		    if (line.hasOption(OPT_MAX_CACHED_BLOCKS))
			conf.setProperty(OPT_MAX_CACHED_BLOCKS, line.getOptionValue(OPT_MAX_CACHED_BLOCKS));

		    try {
			decompressor = new CoverageDecompressor(conf);
			CompressedCoverageIterator it = decompressor.getCoverageIterator();
			Long start = System.currentTimeMillis();
			long c = 0;
			while (it.hasNext()) {
			    it.next();
			    c++;
			}
			System.out.println("Took " + (System.currentTimeMillis() - start) + " ms. to iterate " + c
				+ " positions.");
		    } finally {
			if (decompressor != null)
			    decompressor.close();
		    }
		    break;

		default:
		    break;
		}

	} catch (MissingOptionException e) {
	    e.printStackTrace();
	    System.err.println();
	    usage(options, subcommand, e.toString());
	} catch (Throwable e) {
	    e.printStackTrace();
	    System.err.println();
	    usage(options, subcommand, e.toString());
	}

    }

}
