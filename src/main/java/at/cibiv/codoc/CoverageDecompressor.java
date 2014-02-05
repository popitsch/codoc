package at.cibiv.codoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import at.cibiv.codoc.CoverageCompressor.STANDARD_STREAM;
import at.cibiv.codoc.io.AbstractDataBlock;
import at.cibiv.codoc.io.FileDataInputBlock;
import at.cibiv.codoc.io.FileDataOutputBlock;
import at.cibiv.codoc.io.FileHeader;
import at.cibiv.codoc.quant.QuantizationFunction;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.sam.iterator.ParseException;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.util.TabIterator;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.wig.WigOutputStream;

/**
 * A decompressor for DOC data.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageDecompressor {

	public static final String CMD = "decompress";
	public static final String CMD_INFO = "Decompresses coverage data.";

	public static enum CMD_COMMANDS {
		HEAD, QUERY, TOWIG, TOBED
	};

	/**
	 * Debug flag.
	 */
	static boolean debug = false;

	/*
	 * OPTIONS
	 */

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

	/**
	 * Input stream for chromosomes
	 */
	List<FileDataInputBlock<String>> chrData = null;

	/**
	 * the knot-point position
	 */
	List<FileDataInputBlock<Integer>> posData = null;

	/**
	 * the knot-point coverage1
	 */
	List<FileDataInputBlock<Integer>> cov1Data = null;

	/**
	 * the knot-point coverage2
	 */
	List<FileDataInputBlock<Integer>> cov2Data = null;

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
	 * interval tree for indexing.
	 */
	private CodeWordIntervalTree itree;

	/**
	 * the first interval in the current block
	 */
	private CodeWordInterval firstIntervalInBlock;

	/**
	 * the last interval in the current block
	 */
	private CodeWordInterval lastIntervalInBlock;

	/**
	 * Configuration of the last decompressed file.
	 */
	private PropertyConfiguration compressedConfig;

	/**
	 * Padding intervals before and after the first/last interval on a
	 * chromosome. The coverage within these intervals is by definition zero.
	 */
	private Map<String, CodeWordInterval> paddingUpstreamIntervals = new HashMap<>();

	/**
	 * Padding intervals before and after the first/last interval on a
	 * chromosome. The coverage within these intervals is by definition zero.
	 */
	private Map<String, CodeWordInterval> paddingDownstreamIntervals = new HashMap<>();

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
	 * Index of the current BIT.
	 */
	private int currentBlockIdx = -1;

	/**
	 * Set to true if in-memory BIT caching should be enabled.
	 */
	private boolean inMemoryBlockCaching = true;

	/**
	 * For converting back to original chromosome names.
	 */
	Map<String, String> chrNameMap = new HashMap<>();

	/**
	 * Scaling factor for the coverage signal.
	 */
	private float scaleFactor = 1.0f;

	/**
	 * Decompresses the passed file using the passed VCF file. The intermediate
	 * files are written to the passed working dir that is deleted after the
	 * decompression unless keepWorkDir is set to true.
	 * 
	 * @param sortedSamFile
	 * @param vcfFile
	 * @param regionsOfInterest
	 * @param out
	 * @throws Throwable
	 * @throws IOException
	 * @throws ParseException
	 */
	public CoverageDecompressor(PropertyConfiguration conf) throws Throwable {
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
	 * @throws Throwable
	 */
	public void dumpHeader(PropertyConfiguration conf) throws Throwable {
		File covFile = new File(conf.getProperty(OPT_COV_FILE));
		FileHeader header = null;
		this.workDir = conf.hasProperty(OPT_TMP_DIR) ? new File(conf.getProperty(OPT_TMP_DIR)) : new File(covFile.getParentFile(), "" + Math.random());

		// prepare work dir
		if (!workDir.exists()) {
			if (!workDir.mkdirs())
				throw new IOException("Could not create temporary directory " + workDir);
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
	 * @throws Throwable
	 */
	public static CoverageDecompressor loadFromFile(File covFile, File vcfFile) throws Throwable {
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
	 * @throws Throwable
	 */
	public void loadBlock(int idx) throws Throwable {

		if (inMemoryBlockCaching)
			if (currentBlockIdx == idx)
				return;
		if (debug)
			System.out.println("LOAD BLOCK " + idx);

		this.itree = new CodeWordIntervalTree("intervals");
		this.currentBlockIdx = idx;

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

		String lastchr = "";
		int icount = 0;
		CodeWordInterval gi0 = null, gi1 = null, gi2 = null;
		firstIntervalInBlock = null;
		lastIntervalInBlock = null;
		long pos1 = 1, lastpos1 = 1;
		int lastcov2 = 0;

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
			boolean intervalFinished = false;

			if (!chr.equals(lastchr)) {
				// switch chromosome
				pos1 = diffpos;

				// save original chr name
				chrNameMap.put(StringUtils.prefixedChr(chr), chr);

				if (vcf != null) {
					// load variants and move to first variant
					currentVariants = vcf.getVariants(chr).iterator();
					currentVariant = positionRightAfter(currentVariants, pos1);
				}
				// emit left padding interval for current chrom
				if ((pos1 >= 1) && (cov1 == 0)) {
					CodeWordInterval padLeft = new CodeWordInterval(StringUtils.prefixedChr(chr), 1l, pos1, chr + "-pad-left", COORD_TYPE.ONEBASED);
					padLeft.setOriginalChrom(chr);
					paddingUpstreamIntervals.put(padLeft.getChr(), padLeft);
					// System.out.println("PAD LEFT " + padLeft);
				} else if (cov1 != 0) {
					CodeWordInterval gi = new CodeWordInterval(StringUtils.prefixedChr(chr), 1l, pos1, "CHROM1");
					gi.setLeftCoverage(cov1);
					gi.setRightCoverage(cov1);
					gi.setOriginalChrom(chr);
					// System.out.println(pos1 + "VAR " + gi);
					// store interval
					if (this.firstIntervalInBlock == null)
						this.firstIntervalInBlock = gi;
					gi0 = gi1;
					gi1 = gi2;
					gi2 = gi;
					if (gi1 != null) {
						if (gi0 != null) {
							gi1.setPrev(gi0);
						}
						if (gi2 != null) {
							gi1.setNext(gi2);
						}
					}
					itree.insert(gi);
				}
				// emit right padding interval for previous chrom (if any).
				if (chromLengths.get(StringUtils.prefixedChr(lastchr)) != null) {
					CodeWordInterval padRight = new CodeWordInterval(StringUtils.prefixedChr(lastchr), lastpos1 + 1, chromLengths.get(StringUtils
							.prefixedChr(lastchr)), lastchr + "-pad-right", COORD_TYPE.ONEBASED);
					padRight.setOriginalChrom(lastchr);
					paddingDownstreamIntervals.put(padRight.getChr(), padRight);
					// System.out.println("PAD RIGHT " + padRight);
				}

				lastpos1 = pos1;
				lastcov2 = cov2;
				lastchr = chr;
				continue;
			}

			if (currentVariant != null) {

				// for every variant between here and the right-border of
				// the interval: create a span
				while (currentVariant.getPosition().longValue() <= pos1) {

					long varPos = currentVariant.getPosition();
					Integer varCov = currentVariant.estimateCoverage();
					if (varCov == null)
						varCov = cov1;
					CodeWordInterval gi = new CodeWordInterval(StringUtils.prefixedChr(chr), lastpos1 + 1, varPos, "VAR");
					gi.setLeftCoverage(lastcov2);
					gi.setRightCoverage(varCov);
					gi.setOriginalChrom(chr);
					// System.out.println(pos1 + "VAR " + gi);
					// store interval
					if (this.firstIntervalInBlock == null)
						this.firstIntervalInBlock = gi;
					gi0 = gi1;
					gi1 = gi2;
					gi2 = gi;
					if (gi1 != null) {
						if (gi0 != null) {
							gi1.setPrev(gi0);
						}
						if (gi2 != null) {
							gi1.setNext(gi2);
						}
					}
					itree.insert(gi);
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
				// now create the interval and continue
				CodeWordInterval gi = new CodeWordInterval(StringUtils.prefixedChr(chr), lastpos1 + 1, pos1, "int");
				gi.setLeftCoverage(lastcov2);
				gi.setRightCoverage(cov1);
				gi.setOriginalChrom(chr);

				// System.out.println(pos1 + "INT " + gi);

				// store interval
				if (this.firstIntervalInBlock == null)
					this.firstIntervalInBlock = gi;
				gi0 = gi1;
				gi1 = gi2;
				gi2 = gi;
				if (gi1 != null) {
					if (gi0 != null) {
						gi1.setPrev(gi0);
					}
					if (gi2 != null) {
						gi1.setNext(gi2);
					}
				}
				itree.insert(gi);
			}
			// update positions
			lastpos1 = pos1;
			lastcov2 = cov2;
			lastchr = chr;

		}

		// interlink last interval
		if (gi2 != null) {
			if (gi1 != null) {
				gi2.setPrev(gi1);
			}
			this.lastIntervalInBlock = gi2;
		}

		// last downstream padding interval
		if (chromLengths.get(StringUtils.prefixedChr(lastchr)) != null) {
			if (lastpos1 < chromLengths.get(StringUtils.prefixedChr(lastchr))) {
				CodeWordInterval padRight = new CodeWordInterval(StringUtils.prefixedChr(lastchr), lastpos1 + 1, chromLengths.get(StringUtils
						.prefixedChr(lastchr)), lastchr + "-pad-right", COORD_TYPE.ONEBASED);
				padRight.setOriginalChrom(lastchr);

				paddingDownstreamIntervals.put(padRight.getChr(), padRight);
				// System.out.println("PAD RIGHT " + padRight);
			}
		}

		// build tree
		itree.buildTree();
		// itree.dump();

		if (debug)
			for (String chr : itree.getChromosomes())
				System.out.println(chr + "->" + itree.getTree(chr));

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

	}

	public int getCurrentBlockIdx() {
		return currentBlockIdx;
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
			if ((workDir != null) && (workDir.exists()))
				org.apache.commons.io.FileUtils.forceDelete(workDir);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		close();
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
	public void decompress(PropertyConfiguration conf) throws Throwable {
		this.covFile = new File(conf.getProperty(OPT_COV_FILE));
		this.workDir = conf.hasProperty(OPT_TMP_DIR) ? new File(conf.getProperty(OPT_TMP_DIR)) : new File(covFile.getParentFile(), "" + Math.random());
		File chrLenFile = conf.hasProperty(OPT_CHR_LEN_FILE) ? new File(conf.getProperty(OPT_CHR_LEN_FILE)) : null;
		this.keepWorkDir = conf.getBooleanProperty(OPT_KEEP_WORKDIR, false);
		this.scaleFactor = Float.parseFloat(conf.getProperty(OPT_SCALE_FACTOR, " 1.0"));
		debug = conf.getBooleanProperty(OPT_VERBOSE, false);
		this.inMemoryBlockCaching = !conf.getBooleanProperty(OPT_NO_CACHING, false);
		if (debug)
			if (!inMemoryBlockCaching)
				System.out.println("In-memory BIT caching was disabled!");

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
		}

		try {
			// start
			if (debug)
				System.out.println("Load header...");
			this.header = FileHeader.fromFile(covFile, workDir);
			if (debug)
				System.out.println("Finished.");
			this.compressedConfig = header.getConfiguration();
			// System.out.println("Config:\n" + compressedConfig);

			// set vcf file
			if (conf.hasProperty(OPT_VCF_FILE) && conf.getProperty(OPT_VCF_FILE).equalsIgnoreCase("AUTO")) {
				// get from compressed file.
				this.vcfFile = (compressedConfig.hasProperty(CoverageCompressor.OPT_VCF_FILE)) ? new File(
						compressedConfig.getProperty(CoverageCompressor.OPT_VCF_FILE)) : null;
			} else
				this.vcfFile = conf.hasProperty(OPT_VCF_FILE) ? new File(conf.getProperty(OPT_VCF_FILE)) : null;
			// Check whether file was compressed with VCF knotpoints
			if (compressedConfig.hasProperty(CoverageCompressor.OPT_VCF_FILE) && vcfFile == null)
				System.err.println(new CodocException("NOTE that file was compressed with the VCF file "
						+ compressedConfig.getProperty(CoverageCompressor.OPT_VCF_FILE)
						+ " but decompression was called without this file. Results will be inaccurate."));

			// create streams.
			if (debug)
				System.out.println("Create streams from blocks:" + header.getBlocks());
			for (FileDataOutputBlock<?> b : header.getBlocks()) {
				if (b.getId().equals(STANDARD_STREAM.CHR.name())) {
					FileDataInputBlock<String> block = new FileDataInputBlock<String>(b.getId(), b.getF1());
					block.setCompressionMethod(AbstractDataBlock.compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
					chrData.add(block);
				} else if (b.getId().equals(STANDARD_STREAM.POS.name())) {
					FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
					block.setCompressionMethod(AbstractDataBlock.compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
					posData.add(block);
				} else if (b.getId().equals(STANDARD_STREAM.COV1.name())) {
					FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
					block.setCompressionMethod(AbstractDataBlock.compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
					cov1Data.add(block);
				} else if (b.getId().equals(STANDARD_STREAM.COV2.name())) {
					FileDataInputBlock<Integer> block = new FileDataInputBlock<Integer>(b.getId(), b.getF1());
					block.setCompressionMethod(AbstractDataBlock.compFromString(compressedConfig.getProperty(b.getId() + "-compression", (String) null)));
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
					GenomicInterval gi1 = new GenomicInterval(p1.getChromosome(), p1.get1Position(), Long.MAX_VALUE, "" + blockId);
					gi1.setAnnotation("blockid", blockId);
					blockIndexTree.insert(gi1);

					// create intervals for all chroms between p1 and p2
					int cp1 = chrOrigList.indexOf(p1.getChromosomeOriginal());
					int cp2 = chrOrigList.indexOf(p2.getChromosomeOriginal());
					if (cp1 < 0)
						throw new RuntimeException("chrom [" + p1.getChromosomeOriginal() + "] was not found in " + Arrays.toString(chrOrigList.toArray()));
					if (cp2 < 0)
						throw new RuntimeException("chrom " + p2.getChromosomeOriginal() + " was not found in " + Arrays.toString(chrOrigList.toArray()));
					for (int x = cp1 + 1; x < cp2; x++) {
						GenomicInterval giAdditional = new GenomicInterval(StringUtils.prefixedChr(chrOrigList.get(x)), 0l, Long.MAX_VALUE, "" + blockId);
						giAdditional.setAnnotation("blockid", blockId);
						blockIndexTree.insert(giAdditional);
					}
					// create interval from start of chrom to p2
					GenomicInterval gi2 = new GenomicInterval(p2.getChromosome(), 0l, p2.get1Position(), "" + blockId);
					gi2.setAnnotation("blockid", blockId);
					blockIndexTree.insert(gi2);
				} else {
					GenomicInterval gi1 = new GenomicInterval(p1.getChromosome(), p1.get1Position(), p2.get1Position(), "" + blockId);
					gi1.setAnnotation("blockid", blockId);
					blockIndexTree.insert(gi1);
				}
				leftBorders.put(blockId, p1);
			}
			blockIndexTree.buildTree();
			if (debug)
				blockIndexTree.dump();
			if (debug)
				System.out.println("Finished loading in " + (System.currentTimeMillis() - startTime) + "ms.");
		} catch (Exception e) {
			// delete temporary directory
			if (!workDir.delete())
				System.err.println("Could not remove temporary directory " + workDir);
			throw e;
		}

	}

	/**
	 * 
	 * @return a coverage iterator.
	 * @throws Throwable
	 */
	public CompressedCoverageIterator getCoverageIterator() throws Throwable {
		return new CompressedCoverageIterator(this);
	}

	/**
	 * 
	 * @return a coverage iterator.
	 * @throws Throwable
	 */
	public CompressedCoverageIterator getCoverageIterator(GenomicPosition startPos) throws Throwable {
		return new CompressedCoverageIterator(this, startPos);
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

	/**
	 * Query the padding regions.
	 * 
	 * @param pos
	 * @return
	 */
	private CoverageHit queryPadding(GenomicPosition pos) {
		CodeWordInterval upstream = paddingUpstreamIntervals.get(pos.getChromosome());
		if (upstream != null)
			if (upstream.contains(pos)) {
				CoverageHit hit = new CoverageHit(scaleFactor);
				hit.setLowerBoundary(0f);
				hit.setUpperBoundary(0f);
				hit.setInterpolatedCoverage(0);
				hit.setPadding(true);
				hit.setInterval(upstream);
				return hit;
			}
		CodeWordInterval downstream = paddingDownstreamIntervals.get(pos.getChromosome());
		if (downstream != null)
			if (downstream.contains(pos)) {
				CoverageHit hit = new CoverageHit(scaleFactor);
				hit.setLowerBoundary(0f);
				hit.setUpperBoundary(0f);
				hit.setInterpolatedCoverage(0);
				hit.setPadding(true);
				hit.setInterval(downstream);
				return hit;
			}

		return null;
	}

	/**
	 * 
	 * @param pos
	 * @return [interpolated-coverage][maxborder][minborder]
	 * @throws Throwable
	 */
	public CoverageHit query(GenomicPosition pos) throws Throwable {

		long start = System.nanoTime();

		Set<? extends GenomicInterval> blocks = blockIndexTree.query1based(pos);
		if ((blocks == null) || (blocks.size() != 1)) {
			// FIXME: the padding intervals might not have been initialized yet!
			return queryPadding(pos);
		}
		GenomicInterval gi = blocks.iterator().next();

		if (debug)
			System.out.println("query " + pos + " => load block " + gi + " / " + gi.getAnnotation("blockid"));

		loadBlock((Integer) gi.getAnnotation("blockid"));

		Set<CodeWordInterval> res = itree.query1based(pos);
		if ((res == null) || (res.isEmpty())) {
			// System.out.println("N/A");
			return queryPadding(pos);
		}

		if (res.size() > 1) {
			for (CodeWordInterval cv : res)
				System.err.println(cv + " lc:" + cv.getLeftCoverage() + " rc:" + cv.getRightCoverage());
			// throw new RuntimeException("Something is wrong...(" + pos + ": "
			// + Arrays.toString(res.toArray()));
		}

		CodeWordInterval iv = res.iterator().next();
		CoverageHit hit = interpolate(iv, pos.get1Position());
		hit.setExecTime(System.nanoTime() - start);

		return hit;
	}

	/**
	 * Interpolation
	 * 
	 * @param iv
	 * @param pos1
	 * @return
	 */
	protected CoverageHit interpolate(CodeWordInterval iv, long pos1) {
		CoverageHit hit = new CoverageHit(scaleFactor);
		Integer leftCoverage = iv.getLeftCoverage();
		Integer rightCoverage = iv.getRightCoverage();
		float width = iv.getWidth().floatValue();
		if (width == 0)
			hit.setInterpolatedCoverage(rightCoverage);
		else {
			float prec = (float) (pos1 - iv.getMin()) / (float) width;
			hit.setInterpolatedCoverage(leftCoverage + ((rightCoverage - leftCoverage) * prec));
		}
		hit.setUpperBoundary((float) quant.getMaxBorder(leftCoverage));
		hit.setLowerBoundary((float) quant.getMinBorder(leftCoverage));
		hit.setInterval(iv);
		return hit;
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

				String chr = cmd.split(":")[0];
				Integer off1 = new Integer(cmd.split(":")[1]);
				GenomicPosition pos = new GenomicPosition(chr, off1, COORD_TYPE.ONEBASED);
				// System.out.println("Query " + pos);\
				CoverageHit hit = query(pos);
				if (hit == null)
					System.out.println("cov:\tN/A");
				else {
					if (hit.isFuzzy()) {
						System.out.println("cov:\t" + hit.getInterpolatedCoverage() + " [" + hit.getLowerBoundary() + "-" + hit.getUpperBoundary() + "]");
					} else
						System.out.println("cov:\t" + hit.getInterpolatedCoverage());

					System.out.println("\tinfo:\t" + hit);
					// System.out.println("isFuzzy: " + hit.isFuzzy());
					// System.out.println("isPadding: " + hit.isPadding());
					// // System.out.println("prev:" +
					// // hit.getInterval().getAnnotation("prev"));
					// // System.out.println("next:" +
					// // hit.getInterval().getAnnotation("next"));
					// System.out.println("prev:" +
					// hit.getInterval().getPrev());
					// System.out.println("next:" +
					// hit.getInterval().getNext());
					// System.out.println("lc: " +
					// hit.getInterval().getLeftCoverage());
					// System.out.println("rc: " +
					// hit.getInterval().getRightCoverage());
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
			CoverageHit h = it.next();
			int cov = (h != null ? Math.round(h.getInterpolatedCoverage()) : 0);
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
					GenomicInterval gi = new GenomicInterval(startPos.getChromosome(), startPos.get0Position(), lastPos.get0Position(), "region" + (rc++));
					gi.setOriginalChrom(startPos.getChromosomeOriginal());
					ret.insert(gi);
					if (inCorridor)
						startPos = pos;
					else
						startPos = null;
				} else if (!inCorridor) {
					// end interval as corridor was left
					GenomicInterval gi = new GenomicInterval(pos.getChromosome(), startPos.get0Position(), lastPos.get0Position(), "region" + (rc++));
					gi.setOriginalChrom(pos.getChromosomeOriginal());
					ret.insert(gi);
					startPos = null;
				}
			}
			lastPos = pos;
		}
		if (startPos != null) {
			// end last interval
			GenomicInterval gi = new GenomicInterval(lastPos.getChromosome(), startPos.get0Position(), lastPos.get0Position(), "region" + (rc++));
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
	public GenomicITree toBED(File bedOutFile, Integer minCoverage, Integer maxCoverage, String name, String description, String additional) throws Throwable {
		@SuppressWarnings("resource")
		PrintStream bedOut = bedOutFile != null ? new PrintStream(bedOutFile) : null;
		if (name == null)
			name = "CoverageRegions";
		if (description == null)
			description = "created by CoverageDecompressor.toBED(min=" + (minCoverage == null ? "unbound" : minCoverage) + ",max="
					+ (maxCoverage == null ? "unbound" : maxCoverage) + ")";
		if (additional == null)
			additional = "";
		GenomicITree regionTree = regionsByCoverage(minCoverage, maxCoverage);
		// regionTree.dump();

		if (bedOut != null) {
			bedOut.println("track name=" + name + " description=\"" + description + "\" " + additional);
			for (GenomicInterval gi : regionTree.getIntervals()) {
				bedOut.println(gi.getOriginalChrom() + "\t" + (gi.getMin()) + "\t" + (gi.getMax() + 1) + "\t" + gi.getUri());
			}
			bedOut.close();
		}
		regionTree.buildTree();

		return regionTree;
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
	 * @throws Throwable
	 */
	public static CoverageDecompressor decompress(File covFile, File vcfFile) throws Throwable {
		PropertyConfiguration conf = CoverageDecompressor.getDefaultConfiguration();
		conf.setProperty(OPT_COV_FILE, covFile.getAbsolutePath());
		if (vcfFile != null)
			conf.setProperty(OPT_VCF_FILE, vcfFile.getAbsolutePath());
		CoverageDecompressor decompressor = new CoverageDecompressor(conf);
		return decompressor;
	}

	/**
	 * @return the first interval
	 */
	public CodeWordInterval getFirstInterval() {
		return firstIntervalInBlock;
	}

	/**
	 * @return the last interval
	 */
	public CodeWordInterval getLastInterval() {
		return lastIntervalInBlock;
	}

	/**
	 * 
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

	/**
	 * Dump the coverage to a WIG file.
	 * 
	 * @param wigFile
	 * @throws Throwable
	 */
	private void toWIG(String wigFile) throws Throwable {
		WigOutputStream out = new WigOutputStream(new PrintStream(wigFile), compressedConfig.getProperty(CoverageCompressor.OPT_OUT_FILE),
				"WIG track created by CoverageDecompressor()", "");
		CompressedCoverageIterator it = getCoverageIterator();
		while (it.hasNext()) {
			CoverageHit hit = it.next();
			// FIXME: this is a hack as normalized chr names are used internally
			GenomicPosition pos = it.getGenomicPosition();
			String origChr = chrNameMap.get(pos.getChromosome());
			if (origChr != null)
				pos.setScaffold(origChr);
			out.push(pos, Math.round(hit.getInterpolatedCoverage()));
		}
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

		// args = new String[] { "tobed", "-cov",
		// "/scratch/projects/codoc/src/test/resources/covcompress/small.compressed",
		// "-vcf",
		// "/scratch/projects/codoc/src/test/resources/covcompress/small.vcf",
		// "-min", "1",
		// "-outFile",
		// "/scratch/projects/codoc/src/test/resources/covcompress/test.codoc.bed",
		// "-v" };

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

					opt = new Option(OPT_KEEP_WORKDIR, false, "Do not delete the work directory (e.g., for debugging purposes).");
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

					opt = new Option(OPT_OUT_FILE, true, "Output file that will contain the rounded approximated coverage values (optional).");
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

					opt = new Option(OPT_KEEP_WORKDIR, false, "Do not delete the work directory (e.g., for debugging purposes).");
					opt.setRequired(false);
					options.addOption(opt);

					opt = new Option(OPT_SCALE_FACTOR, true, "Signal scaling factor (default is 1.0).");
					opt.setLongOpt("scaleFactor");
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

					try {
						decompressor = new CoverageDecompressor(conf);
						decompressor.toWIG(line.getOptionValue(OPT_OUT_FILE));
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

					opt = new Option(OPT_KEEP_WORKDIR, false, "Do not delete the work directory (e.g., for debugging purposes).");
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

					options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

					opt = new Option(OPT_SCALE_FACTOR, true, "Signal scaling factor (default is 1.0).");
					opt.setLongOpt("scaleFactor");
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

					Integer min = line.hasOption("min") ? new Integer(line.getOptionValue("min")) : null;
					Integer max = line.hasOption("max") ? new Integer(line.getOptionValue("max")) : null;

					try {
						decompressor = new CoverageDecompressor(conf);
						decompressor.toBED(new File(line.getOptionValue(OPT_OUT_FILE)), min, max, line.getOptionValue("name"),
								line.getOptionValue("description"), line.getOptionValue("additional"));
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

					opt = new Option(OPT_KEEP_WORKDIR, false, "Do not delete the work directory (e.g., for debugging purposes).");
					opt.setRequired(false);
					options.addOption(opt);

					options.addOption(OPT_VERBOSE, "verbose", false, "be verbose.");

					opt = new Option(OPT_SCALE_FACTOR, true, "Signal scaling factor (default is 1.0).");
					opt.setLongOpt("scaleFactor");
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

					try {
						decompressor = new CoverageDecompressor(conf);
						decompressor.interactiveQuerying();
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
