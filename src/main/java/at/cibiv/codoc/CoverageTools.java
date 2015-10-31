package at.cibiv.codoc;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import at.ac.univie.cs.mis.lds.index.itree.ITree;
import at.ac.univie.cs.mis.lds.index.itree.Interval;
import at.cibiv.codoc.CoverageCompressor.QUANT_METHOD;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.io.CoverageInputStream;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.bed.BedIterator;
import at.cibiv.ngs.tools.bed.BedTools;
import at.cibiv.ngs.tools.bed.SimpleBEDFile;
import at.cibiv.ngs.tools.exon.ExonChromosomeTree;
import at.cibiv.ngs.tools.exon.ExonInterval;
import at.cibiv.ngs.tools.exon.Gene;
import at.cibiv.ngs.tools.exon.RefSeqDb;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.BinnedHistogram;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.util.TabIterator;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant.ZYGOSITY;
import at.cibiv.ngs.tools.vcf.VCFIterator;
import at.cibiv.ngs.tools.wig.WigOutputStream;

/**
 * Various DOC tools.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageTools {

    public static boolean debug = false;
    public static final String CMD = "tools";
    public static final String CMD_INFO = "Various DOC tools.";

    /**
     * Annotate a VCF with coverage information from the given CODOC.
     * 
     * @param covFile
     * @param vcfFile
     * @param field
     * @param outFile
     * @throws IOException
     * @throws CodocException
     */
    private static void annotateVCF(File covFile, File vcfFile, String field, File outFile) throws IOException, CodocException {

	VCFIterator vi = new VCFIterator(vcfFile);
	StringBuffer head = new StringBuffer(vi.getHeader());
	head.insert(head.lastIndexOf("#CHROM"), "##INFO=<ID=" + field + ",Number=1,Type=Integer,Description=\"Coverage as extracted from " + covFile + ".\">\n");
	PrintStream vout = new PrintStream(outFile);
	vout.print(head.toString());

	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(covFile, null);
	StringBuffer buf = new StringBuffer();
	try {
	    while (vi.hasNext()) {
		SimpleVCFVariant v = vi.next();
		CoverageHit h = cov1.query(v.getGenomicPosition());
		if (h != null) {
		    int cov = h.getRoundedCoverage();
		    v.addField(field, cov + "");
		}
		buf.append(v.toStringAsIs() + "\n");
		if (buf.length() > 1000000) {
		    vout.print(buf.toString());
		    buf = new StringBuffer();
		}
	    }
	    vout.print(buf.toString());
	    vout.close();
	    if (debug)
		System.out.println(" Finished.");
	} finally {
	    cov1.close();
	}
    }

    /**
     * Dump the iterator to the given output stream
     * 
     * @param covTFile
     * @throws Throwable
     */
    public static void dumpIterator(File cov1File, File cov1VcfFile, boolean printPos, File aliasFile, List<String> chroms, Integer n, PrintStream out)
	    throws Throwable {
	if (debug)
	    System.out.println("Load " + cov1File);

	// prepare alias file
	Map<String, String> chromAliases = null;
	if (aliasFile != null) {
	    chromAliases = new HashMap<String, String>();
	    TabIterator ti = new TabIterator(aliasFile, "#");
	    while (ti.hasNext()) {
		String[] t = ti.next();
		chromAliases.put(t[0], t[1]);
	    }
	}

	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);
	try {
	    cov1.setMaxCachedBlocks(1);
	    CompressedCoverageIterator it1 = cov1.getCoverageIterator();
	    it1.setChromAliases(chromAliases);
	    StringBuilder sb = new StringBuilder();
	    int c = 0;
	    while (it1.hasNext()) {
		Float hit1 = it1.next();
		GenomicPosition pos = it1.getGenomicPosition();
		if (printPos)
		    sb.append(pos.getChromosomeOriginal() + "\t" + pos.get1Position() + "\t");
		sb.append(hit1 + "\n");
		if (sb.length() > 50000000) {
		    if (debug)
			System.out.print("w");
		    out.println(sb.toString());
		    sb = new StringBuilder();
		    if (debug)
			System.out.print("!");
		}
		if (++c % 1000000 == 0)
		    System.out.print(".");
		if (n != null && c >= n)
		    break;
	    }
	    out.println(sb.toString());
	    if (debug)
		System.out.println(" Finished.");
	} finally {
	    cov1.close();
	}
    }

    /**
     * Extract variants that are found only in the tumor sample or that changed
     * from HET-> HOM. Variants found also in the normal are written to a second
     * VCF file. Variants with a coverage below a given treshold in the normal
     * are written to a third VCF file.
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

	out.close();
	if (normalAlso != null)
	    normalAlso.close();
	if (lowCoverage != null)
	    lowCoverage.close();
    }

    public static enum OPERATOR {
	ADD, SUBTRACT, DIFF, MIN, MAX, AVG
    };

    /**
     * Linear combination of coverage signals.
     * 
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
		float c1 = it.getCoverage1();
		float c2 = it.getCoverage2();
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
		case AVG:
		    co = (c1 + c2) / 2;
		    break;
		default:
		    throw new InvalidParameterException("Unknown operator " + op);
		}
		out.println(pos.getChromosomeOriginal() + "\t" + pos.get1Position() + "\t" + co);
		count++;
	    }
	    out.close();
	    out = null;

	    if (debug)
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
		case AVG:
		    System.out.println("Calculated average for " + count + " values");
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
     * Cross-correlation of coverage signals. FIXME: experimental method - use
     * with care.
     * 
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
		float c1 = it.getCoverage1();
		float c2 = it.getCoverage2();
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
		float c1 = it.getCoverage1();
		float c2 = it.getCoverage2();
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
     * Calculates the base-coverage per feature in a passed BED file. The output
     * will be sorted by genomic coordinates.
     * 
     * @param covFile
     * @param covVcfFile
     * @param bedFile
     * @param out
     * @throws Throwable
     * @return a map interval->(filename->DOC)
     * @throws CodocException
     */
    public static Map<GenomicInterval, Map<File, Double>> calculateCoveragePerBedFeature(List<File> covFiles, List<File> covVcfFiles, File bedFile,
	    PrintStream out) throws IOException, CodocException {
	CoverageDecompressor cov = null;

	try {

	    double scoreAvgAll = 0f, scoreCountAll = 0f;
	    SimpleBEDFile bf = new SimpleBEDFile(bedFile);
	    GenomicITree intervals = bf.getGenomicITree();
	    Map<GenomicInterval, Map<File, Double>> allScores = new HashMap<GenomicInterval, Map<File, Double>>();

	    for (int i = 0; i < covFiles.size(); i++) {
		File covFile = covFiles.get(i);
		File covVcfFile = null;
		if (covVcfFiles != null && i < covVcfFiles.size())
		    covVcfFile = covVcfFiles.get(i);

		Map<String, Double> absCov = new HashMap<String, Double>();
		if (debug)
		    System.out.println("Load " + covFile);
		try {
		    cov = CoverageDecompressor.loadFromFile(covFile, covVcfFile);

		    CompressedCoverageIterator it = cov.getCoverageIterator();
		    while (it.hasNext()) {
			Float coverage = it.next();
			// System.out.println(it.getGenomicPosition().toString1basedOrig()
			// + " -- " + coverage);
			List<? extends GenomicInterval> res = intervals.query1basedList(it.getGenomicPosition());

			if (res != null) {
			    if (res.size() > 0) {
				scoreAvgAll += coverage;
				scoreCountAll++;
			    }
			    for (GenomicInterval gi : res) {

				Double sum = absCov.get(gi.toString());
				if (sum == null)
				    sum = 0d;
				sum += coverage;
				absCov.put(gi.toString(), sum);

			    }
			}

		    }

		} finally {
		    if (cov != null)
			cov.close();
		}

		for (GenomicInterval gi : bf.getIntervalsList()) {
		    double width = gi.getWidth() + 1; // note: BED intervals are
		    // not including the end
		    // coordinate
		    Double sum = absCov.get(gi.toString());
		    if (sum == null)
			sum = 0d;
		    // System.out.println(gi + " " + sum + " " + width);
		    double avgScore = sum / width;
		    Map<File, Double> scores = allScores.get(gi);
		    if (scores == null)
			scores = new HashMap<File, Double>();
		    scores.put(covFile, avgScore);
		    allScores.put(gi, scores);
		}

	    }

	    if (out != null) {
		out.println("# BED file: \t" + bedFile);
		out.println("# Score files: \t" + Arrays.toString(covFiles.toArray()));
		out.print("#chr\tmin\tmax\tname\twidth");
		for (File covFile : covFiles)
		    out.print("\tavg.cov (" + covFile.getName() + ")");
		out.println();

		// sort intervals
		List<GenomicInterval> sort = new ArrayList<GenomicInterval>();
		sort.addAll(allScores.keySet());
		Collections.sort(sort);

		for (GenomicInterval gi : sort) {

		    out.format("%s\t%d\t%d\t%s\t%.0f", gi.getOriginalChrom(), gi.getMin() + 1, gi.getMax(), gi.getId(), gi.getWidth() + 1);

		    for (File covFile : covFiles) {
			Map<File, Double> scores = allScores.get(gi);
			Double avg = scores == null ? -1 : scores.get(covFile);
			out.format("\t%.1f", avg);
		    }

		    out.println();

		}
	    }

	    double averageScore = scoreAvgAll / scoreCountAll;
	    if (out != null)
		out.println("# Overall average score: " + averageScore + " in " + scoreCountAll + " positions");
	    if (debug)
		System.out.println("Finished.");

	    return allScores;
	} finally {
	    if (cov != null)
		cov.close();
	}

    }

    /**
     * Calculate a histogram of the avg. scores within the passed BED intervals.
     * 
     * @param covFile
     * @param bedFile
     * @param out
     * @return
     * @throws IOException
     * @throws CodocException
     */
    @SuppressWarnings("resource")
    public static BinnedHistogram calculateScoreHistogram(File covFile, File vcfFile, File bedFile, PrintStream out, int binSize, float scale,
	    float minScoreOut, float maxScoreOut, File bedOut) throws IOException, CodocException {

	PrintStream bout = bedOut != null ? new PrintStream(bedOut) : null;

	List<File> covFiles = new ArrayList<>();
	covFiles.add(covFile);
	List<File> covVcfFiles = new ArrayList<>();
	if (vcfFile != null)
	    covVcfFiles.add(vcfFile);

	Map<GenomicInterval, Map<File, Double>> allScores = calculateCoveragePerBedFeature(covFiles, covVcfFiles, bedFile, null);
	// System.out.println("got " + allScores.size());
	if (out != null && scale != 1.0f)
	    out.println("Coverage values were scaled by " + scale);

	BinnedHistogram bh = new BinnedHistogram(binSize);

	for (GenomicInterval k : allScores.keySet()) {
	    Map<File, Double> scores = allScores.get(k);
	    double score = scores.get(covFile);
	    k.setUri(k.getUri() + "-" + score);
	    if (score * scale > minScoreOut && score * scale < maxScoreOut)
		if (bout != null)
		    bout.println(k.toBED());

	    bh.push((int) Math.round(score * scale));
	}
	if (out != null)
	    out.println(bh);
	if (bout != null)
	    bout.close();
	return bh;
    }

    /**
     * Calculate a coverage histogram for all positions in the passed VCF file.
     * 
     * @param covFile
     * @param vcfFile
     * @param out
     * @param binSize
     * @return
     * @throws IOException
     * @throws CodocException
     */
    public static BinnedHistogram calculateScoreHistogramFromVCF(File covFile, File vcfFile, PrintStream outCSV, PrintStream out, int binSize, boolean onlypass)
	    throws IOException, CodocException {
	BinnedHistogram bh = new BinnedHistogram("ALL", binSize);
	Map<String, BinnedHistogram> chromBH = new HashMap<String, BinnedHistogram>();

	CoverageDecompressor cov = null;
	try {
	    cov = CoverageDecompressor.loadFromFile(covFile, null);

	    CompressedCoverageIterator it = cov.getCoverageIterator();
	    VCFIterator vi = null;
	    SimpleVCFVariant v = null;
	    String lastchrom = "";
	    while (it.hasNext()) {
		Float coverage = it.next();
		if (coverage == null)
		    coverage = -1f;

		GenomicPosition pos = it.getGenomicPosition();

		// load new chrom?
		if (!pos.getChromosomeOriginal().equals(lastchrom)) {
		    lastchrom = pos.getChromosomeOriginal();
		    vi = new VCFIterator(vcfFile);
		    while (vi.hasNext()) {
			v = vi.next();
			if (v.getChromosomeOrig().equals(lastchrom))
			    break;
		    }
		    if (!v.getChromosomeOrig().equals(lastchrom)) {
			vi = null;
			continue; // skip as there is no VCF entry for this
			// chrom
		    }
		}

		if (vi == null)
		    continue;

		// position vcf iterator
		while (v.getPosition() < pos.get1Position() && vi.hasNext())
		    v = vi.next();

		if (v.getGenomicPosition().equals(pos)) {
		    if (onlypass && !(v.getFilter().equals("PASS") || v.getFilter().equals(".")))
			continue;

		    if (debug)
			if ((int) Math.round(coverage) == 0)
			    System.err.println("Entry with zero coverage found at " + v);

		    bh.push((int) Math.round(coverage));
		    BinnedHistogram cbh = chromBH.get(v.getChromosomeOrig());
		    if (cbh == null)
			cbh = new BinnedHistogram(v.getChromosomeOrig(), binSize);
		    cbh.push((int) Math.round(coverage));
		    chromBH.put(v.getChromosomeOrig(), cbh);
		}

	    }
	} finally {
	    if (cov != null)
		cov.close();
	}
	if (out != null) {
	    out.println(bh);
	    SortedSet<String> schroms = new TreeSet<String>();
	    schroms.addAll(chromBH.keySet());
	    for (String chr : schroms) {
		out.println();
		out.println();
		out.println(chromBH.get(chr));
	    }
	}
	if (outCSV != null) {
	    outCSV.println(bh.toCSV());
	}
	return bh;
    }

    /**
     * Calculates the base-coverage per feature in a passed UCSC refseq data
     * file. The output will be sorted by genomic coordinates. FIXME:
     * experimental method - use with care.
     * 
     * @param covFile
     * @param covVcfFile
     * @param bedFile
     * @param covOut
     * @throws Throwable
     */
    public static void calculateCoveragePerUCSCFeature(File covFile, File covVcfFile, File ucscFlatFile, PrintStream covOut) throws Throwable {
	CoverageDecompressor cov = null;

	try {
	    if (debug)
		System.out.println("Load " + covFile);

	    RefSeqDb db = new RefSeqDb(ucscFlatFile);
	    Map<Gene, Float> geneCoverageMap = new HashMap<Gene, Float>();
	    Map<Gene, Float> genePosUnmappedMap = new HashMap<Gene, Float>();
	    for (Gene gene : db.getGenesSorted()) {
		for (ExonInterval ex : gene.getExonsSorted())
		    ex.setAnnotation("gene", gene);
	    }

	    cov = CoverageDecompressor.loadFromFile(covFile, covVcfFile);
	    CoverageTools.debug = false;
	    if (debug)
		System.out.println(Arrays.toString(cov.getChromosomes().toArray()));

	    covOut.println("min/max are gene positions; cov, width, avg.cov is calc only over exons.");
	    covOut.println("#chr\tmin\tmax\tname\tstrand\twidth\tcov\tavg.cov\tperc.uncov");

	    Map<String, ITree<Long>> trees = db.buildItrees();
	    CompressedCoverageIterator it = cov.getCoverageIterator();
	    while (it.hasNext()) {
		Float h = it.next();
		float coverage = (h == null) ? 0f : h;
		GenomicPosition pos = it.getGenomicPosition();
		if (pos == null) {
		    break;
		}
		ExonChromosomeTree tree = (ExonChromosomeTree) trees.get(pos.getChromosomeOriginal());
		if (tree == null) {
		    System.err.println("chromosome not found: " + pos.getChromosomeOriginal());
		    continue;
		}
		List<Interval<Long>> res = tree.query(pos.get0Position());
		for (Interval<Long> ex : res) {
		    Gene g = (Gene) ex.getAnnotation("gene");
		    Float geneCov = geneCoverageMap.get(g);
		    if (geneCov == null)
			geneCov = 0f;
		    geneCov += coverage;
		    geneCoverageMap.put(g, geneCov);

		    if (geneCov == 0f) {
			Float posUnmapped = genePosUnmappedMap.get(g);
			if (posUnmapped == null)
			    posUnmapped = 0f;
			posUnmapped++;
			geneCoverageMap.put(g, geneCov);
		    }
		}
	    }

	    for (Gene gene : db.getGenesSorted()) {
		Float geneCov = geneCoverageMap.get(gene);
		Float posUnmapped = genePosUnmappedMap.get(gene);
		covOut.print(toStr(gene.getChromosome()) + "\t");
		covOut.print(toStr(gene.getMin()) + "\t");
		covOut.print(toStr(gene.getMax()) + "\t");
		covOut.print(toStr(gene.getName()) + "\t");
		covOut.print(toStr(gene.getStrand()) + "\t");
		covOut.print(toFloat(gene.getExonWidth(), 0) + "\t");
		covOut.print(toFloat(geneCov, 0) + "\t");
		if (geneCov == null)
		    covOut.print("n/a\t");
		else
		    covOut.print(toFloat((gene.getExonWidth() == null ? 0f : (geneCov / gene.getExonWidth())), 1) + "\t");
		covOut.print(toFloat((posUnmapped == null ? 0f : (posUnmapped / gene.getWidth())), 1) + "\t");
		covOut.println();
	    }

	} finally {
	    if (cov != null)
		cov.close();
	}
	if (debug)
	    System.out.println("Finished.");

    }

    private static String toStr(Object o) {
	if (o == null)
	    return "n/a";
	return o + "";
    }

    private static String toFloat(Float f, int comma) {
	if (f == null)
	    return "n/a";
	return String.format("%." + comma + "f", f);
    }

    /**
     * Experimental
     * 
     * @param covFile
     * @param covVcfFile
     * @param wavFile
     * @throws CodocException
     * @throws IOException
     */
    @Deprecated
    private void coverageToWav(File covFile, File covVcfFile, File wavFile) throws CodocException, IOException {

	if (debug)
	    System.out.println("Load " + covFile);
	CoverageDecompressor cov = null;
	CompressedCoverageIterator cci = null;
	CoverageInputStream ci = null;

	try {
	    cov = CoverageDecompressor.loadFromFile(covFile, covVcfFile);
	    cci = cov.getCoverageIterator();
	    ci = new CoverageInputStream(cci);

	} finally {
	    if (cov != null)
		cov.close();
	    if (ci != null)
		ci.close();
	}
    }

    /**
     * Extract a sample from a codoc file.
     * 
     * @param covTFile
     * @throws Throwable
     */
    public static void subSample(File cov1File, File cov1VcfFile, File sampleRegionsBed, String startPos, int N, int step, String format, File outFile)
	    throws Throwable {
	if (debug)
	    System.out.println("Load " + cov1File);
	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);
	cov1.setMaxCachedBlocks(1);
	WigOutputStream wout = null;
	PrintWriter pout = null;
	boolean isWig = format == null ? false : format.equalsIgnoreCase("wig");
	boolean isCodoc = format == null ? false : format.equalsIgnoreCase("codoc");

	try {
	    CompressedCoverageIterator it1 = cov1.getCoverageIterator();

	    GenomicITree sampleRegions = (sampleRegionsBed != null) ? new SimpleBEDFile(sampleRegionsBed).getGenomicITree() : null;

	    GenomicPosition start = null;
	    if (startPos.equalsIgnoreCase("random")) {
		if (sampleRegions == null)
		    throw new IOException("If random sampling is used, a BED file must be set!");
		start = new SimpleBEDFile(sampleRegionsBed).getRandomPosition();
	    } else if (startPos.equalsIgnoreCase("first")) {
		if (sampleRegionsBed != null)
		    start = new BedIterator(sampleRegionsBed).next().getLeftPosition();
	    } else
		start = GenomicPosition.fromString(startPos, COORD_TYPE.ONEBASED);

	    if (sampleRegions != null && !sampleRegions.contains(start))
		throw new IOException("Start position " + start.toString1basedCanonicalHuman() + " not in ROI.");

	    StringBuilder sb = new StringBuilder();

	    if (isWig) {
		wout = new WigOutputStream(outFile);
	    } else if (isCodoc) {
		pout = new PrintWriter(new File(outFile.getAbsolutePath() + ".temp"));
	    } else {
		pout = new PrintWriter(outFile);
		sb.append("# Sampled " + N + " positions with step " + step + " starting at " + start + " in " + cov1File + "\n");
	    }

	    GenomicPosition pos = null;
	    Integer cov = null;
	    boolean inRegion = false;
	    long count = 0L, pcount = 0L, c = 0L;
	    while (it1.hasNext()) {
		cov = it1.nextCoverage();
		pos = it1.getGenomicPosition();
		if (start == null)
		    start = pos;

		// check ROI
		if (sampleRegions != null && !sampleRegions.contains(pos))
		    continue;

		// start to sample
		if (pos.equals(start))
		    inRegion = true;

		if (inRegion) {
		    pcount++;
		    if (pcount % step == 1) {
			count++;
			if (isWig)
			    wout.push(pos, cov, step);
			else
			    sb.append(pos.getChromosomeOriginal() + "\t" + pos.get1Position() + "\t" + cov + "\n");
		    }
		}
		if (sb.length() > 500000) {
		    pout.println(sb.toString());
		    sb = new StringBuilder();
		}
		if (debug) {
		    if (++c % 1000000 == 0)
			System.err.print(".");
		    if (c % 100000000 == 0)
			System.err.println(pos.toString1basedCanonicalHuman());
		}
	    }

	    if (!isWig) {
		pout.print(sb.toString());
		if (count < N)
		    pout.println("# Could no create complete sample as EOF was reached. Sample size: " + count);

	    }

	} finally {
	    System.out.println("Subsampling finished.");
	    cov1.close();
	    if (pout != null)
		pout.close();
	    if (wout != null)
		wout.close();
	}

	if (isCodoc) {
	    // garbage collect
	    System.gc();
	    // now compress the created coverage file
	    PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
	    String[] copyParams = new String[] { CoverageCompressor.OPT_BAM_FILTER, CoverageCompressor.OPT_BED_FILE, CoverageCompressor.OPT_BEST_COMPRESSION,
		    CoverageCompressor.OPT_BLOCKSIZE, CoverageCompressor.OPT_COMPRESSION_ALGORITHM, CoverageCompressor.OPT_MANUAL_GOLOMB_K,
		    CoverageCompressor.OPT_QUANT_METHOD, CoverageCompressor.OPT_QUANT_PARAM, CoverageCompressor.OPT_SCALE_COVERAGE,
		    CoverageCompressor.OPT_VALIDATION_STRINGENCY, CoverageCompressor.OPT_VCF_FILE };
	    for (String p : copyParams)
		if (cov1.getCompressionParameter(p) != null)
		    config.setProperty(p, cov1.getCompressionParameter(p));
	    config.setProperty(CoverageCompressor.OPT_COVERAGE_FILE, new File(outFile.getAbsolutePath() + ".temp").getAbsolutePath());
	    config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
	    System.out.println(config);
	    if (CoverageCompressor.compress(config, outFile, true))
		System.out.println("Created " + outFile + " / " + outFile.exists());
	    // delete temp file.
	    if ( ! new File(outFile.getAbsolutePath() + ".temp").delete())
		System.err.println("Could not delete temp file " + new File(outFile.getAbsolutePath() + ".temp"));;

	}
	if (debug)
	    System.out.println("Finished.");
    }

    /**
     * 
     * @param codocFiles
     * @param minCov
     * @param outputDir
     * @throws Throwable
     */
    public static void calculateMinCoveredRegions(List<File> codocFiles, List<Integer> minCov, List<File> scopeFiles, boolean dontCheckSorted,
	    boolean writeWig, File outputDir) throws Throwable {

	// create output Dir?
	org.apache.commons.io.FileUtils.forceMkdir(outputDir);

	WigOutputStream outWig = null;
	if (writeWig) {
	    File allCoveredWigFile = new File(outputDir, "Intersect.minCov-" + StringUtils.concat(minCov, "-") + ".wig");
	    outWig = new WigOutputStream(allCoveredWigFile);
	}
	File allCoveredBedFile = new File(outputDir, "Intersect.minCov-" + StringUtils.concat(minCov, "-") + ".bed");
	PrintStream outBed = new PrintStream(allCoveredBedFile);

	CoverageDecompressor cd = null;

	List<BedIterator> its = new ArrayList<>();
	// sorted list of current intervals
	List<GenomicInterval> pointers = new ArrayList<GenomicInterval>();

	for (int i = 0; i < codocFiles.size(); i++) {
	    File f = codocFiles.get(i);
	    Integer mc = minCov.get(i);
	    File bedOutFile = new File(outputDir, f.getName() + ".minCov" + mc + ".bed");
	    try {
		cd = CoverageDecompressor.loadFromFile(f, null);
		cd.toBED(bedOutFile, mc, null, null, null, null, true);
	    } finally {
		if (cd != null)
		    cd.close();
	    }
	    BedIterator bi = new BedIterator(bedOutFile);
	    if (bi.hasNext())
		pointers.add(bi.next());
	    its.add(bi);

	    if (debug)
		System.out.println("Created " + bedOutFile);
	}
	Collections.sort(pointers);

	if (pointers.size() == 0) {
	    outBed.close();
	    throw new IOException("All iterators were empty - do chrom names match?");
	}

	GenomicPosition p = pointers.get(0).getLeftPosition();
	GenomicPosition start = null;
	int count = 0;
	do {
	    // we know that the BEDs are sorted and non-overlapping
	    int matches = 0;
	    for (GenomicInterval gi : pointers) {
		if (gi.contains(p))
		    matches++;
	    }

	    if (matches == its.size()) {
		if (outWig != null)
		    outWig.push(p, 1d);
		if (start == null)
		    start = p;
	    } else {
		if (outWig != null)
		    outWig.push(p, 0d);
		if (start != null) {
		    outBed.println(start.getChromosomeOriginal() + "\t" + start.get0Position() + "\t" + p.get0Position() + "\tint" + (++count));
		    start = null;
		}
	    }

	    if (matches == 0) {
		// close interval?
		if (start != null) {
		    outBed.println(start.getChromosomeOriginal() + "\t" + start.get0Position() + "\t" + p.get0Position() + "\tint" + (++count));
		    start = null;
		}
		p = pointers.get(0).getLeftPosition();
	    } else {
		p = p.add(1);
	    }

	    // chrom switch?
	    if (start != null && !p.getChromosome().equals(start.getChromosome())) {
		outBed.println(start.getChromosomeOriginal() + "\t" + start.get0Position() + "\t" + p.get0Position() + "\tint" + (++count));
		start = null;
	    }

	    for (BedIterator bi : its) {
		GenomicInterval g = bi.getCurrentInterval();
		if (g.getRightPosition().compareTo(p) < 0) {
		    pointers.remove(g);
		    if (bi.hasNext()) {
			pointers.add(bi.next());
			Collections.sort(pointers);
		    }
		}
	    }

	} while (!pointers.isEmpty());
	if (start != null) {
	    outBed.println(start.getChromosomeOriginal() + "\t" + start.get0Position() + "\t" + p.get0Position() + "\tint" + (++count));
	    start = null;
	}
	if (outWig != null)
	    outWig.close();
	outBed.close();

	// calc overlaps
	for (File scope : scopeFiles) {
	    File covFile = new File(outputDir, "Coverage." + scope.getName() + ".tsv");
	    PrintStream out = new PrintStream(covFile);
	    BedTools.calcPerFeatureCoverageStats(scope, allCoveredBedFile, dontCheckSorted, 0f, 1f, out);
	    out.close();
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
	    System.out.println("Command:\tcalculateCoveragePerBedFeature\tCalculates the base-coverage per feature in a passed BED file.");
	    System.out.println("Command:\tcalculateCoveragePerUCSCFeature\tCalculates the base-coverage per feature in a UCSC database");
	    System.out.println("Command:\tcalculateScoreHistogram\tCalculate a histogram of the avg. DOC wrt. a given set of intervals");
	    System.out.println("Command:\tcalculateScoreHistogramFromVCF\tCalculate a histogram of the coverage per entry in a VCF file");
	    System.out.println("Command:\tsubSample\tExtract a sample from a codoc file.");
	    System.out.println("Command:\tannotateVCF\tAnnotate a VCF file with coverage information from a CODOC file.");
	    System.out.println("Command:\tcalculateMinCoveredRegions\tCalculates the regions in a set of CODOC files with a given minum coverage.");

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

	args = new String[] { "subsample",

	"-cov", "src/test/resources/covcompress/small.bam.codoc", "-start", "first", "-N", "100", "-step", "5", "-o",
		"src/test/resources/subsample/small.subsample.codoc", "-v" };

	// args = new String[] { "calculateMinCoveredRegions",
	//
	// "-cov", "src/test/resources/covintersec/A.codoc", "-cov",
	// "src/test/resources/covintersec/B.codoc", "-cov",
	// "src/test/resources/covintersec/C.codoc",
	//
	// "-o", "src/test/resources/covintersec/A+B+C", "-minCov", "2",
	//
	// // "-scope", "T:/Niko/ucsc_canonical_genes.canonical.bed.gz",
	// "-v" };

	//
	// args = new String[] { "dumpCoverage", "-cov",
	// "src/test/resources/covcompress/reverse.sam.codoc", "-o", "-",
	// "-printCoords", "-alias",
	// "src/test/resources/covcompress/chr.aliases", "-chroms", "21",
	// "-chroms", "20" };

	// args = new String[] { "subSample", "-cov",
	// "src/test/resources/covcompress/small.compressed",
	// "-bed", "src/test/resources/covcompress/small.roi", "-N", "100",
	// "-step", "5", "-start", "first", "-v", "-o",
	// "src/test/resources/subsample/small.subsample.codoc" };

	// args = new String[] { "dumpCoverage", "-cov",
	// "src/test/resources/covcompress/small.compressed", "-o", "-" };

	// args = new String[] { "calculateScoreHistogramFromVCF", "-cov",
	// "/temp/Niko/stats/4447.final.bam.codoc", "-vcf",
	// "/temp/Niko/stats/4447.final.platypus.vcf.gz", "-csv", "-",
	// "-o", "-", "-onlypass", "-v" };

	// create the command line parser
	CommandLineParser parser = new PosixParser();

	// create the Options
	Options options = new Options();
	options.addOption("h", "help", false, "Print this usage information.");
	String subcommand = null;

	try {
	    // parse the command line argumecalculateAttributeHistogramnts
	    CommandLine line = parser.parse(options, args, true);

	    // validate that block-size has been set
	    if (line.getArgs().length == 0 || line.hasOption("h")) {
		usage(options, subcommand, null);
	    }

	    subcommand = line.getArgs()[0];

	    if (subcommand.equalsIgnoreCase("calculateMinCoveredRegions")) {
		options = new Options();

		Option opt = new Option("cov", true, "CODOC coverage files");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("scope", true, "Scope BED files. Overlap statistics will be calculated for each scope and each coverage track.");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option(
			"minCov",
			true,
			"Minimum required coverage in each file (order should match the order of the input CODOC files). If only one value is provided it will be used for all input data sets.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("o", true, "Output directory.");
		opt.setRequired(true);
		options.addOption(opt);

		options.addOption("d", "dontCheckSorted", false, "Dont check sort status of beds.");

		options.addOption("w", "writeWig", false, "If set, a WIG file describing the covered regions will be written.");

		options.addOption("v", "verbose", false, "be verbose.");

		line = parser.parse(options, args);
		if (line.hasOption("v"))
		    debug = true;
		else
		    debug = false;

		List<File> covFiles = new ArrayList<>();
		for (String s : line.getOptionValues("cov"))
		    covFiles.add(new File(s));
		List<File> scopeFiles = new ArrayList<>();
		if (line.hasOption("scope"))
		    for (String s : line.getOptionValues("scope"))
			scopeFiles.add(new File(s));
		List<Integer> minCov = new ArrayList<>();
		if (line.getOptionValues("minCov").length == covFiles.size()) {
		    for (String m : line.getOptionValues("minCov"))
			minCov.add(Integer.parseInt(m));
		} else if (line.getOptionValues("minCov").length == 1) {
		    for (String s : line.getOptionValues("scope"))
			minCov.add(Integer.parseInt(line.getOptionValue("minCov")));
		} else {
		    throw new IOException("Invalid minCov configuration: either use one value for all datasets or one value per dataset");
		}

		File outDir = new File(line.getOptionValue("o"));

		calculateMinCoveredRegions(covFiles, minCov, scopeFiles, line.hasOption("dontCheckSorted"), line.hasOption("writeWig"), outDir);

		return;
	    } else

	    if (subcommand.equalsIgnoreCase("annotateVCF")) {
		options = new Options();

		Option opt = new Option("cov", true, "Coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "Input VCF.");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("field", true, "Name of the field to be added/replaced. Default: DP. Note that a header will be added to the VCF.");
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

		File covFile = new File(line.getOptionValue("cov"));
		File vcfFile = new File(line.getOptionValue("vcf"));
		File outFile = new File(line.getOptionValue("o"));
		String field = line.hasOption("field") ? line.getOptionValue("field") : "DP";

		annotateVCF(covFile, vcfFile, field, outFile);

		return;
	    } else if (subcommand.equalsIgnoreCase("subSample")) {
		options = new Options();

		Option opt = new Option("cov", true, "Coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "Coverage variants (VCF).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("start", true,
			"Start position of the sample. Use 'random' for a random position (In this case, -bed must be set) or 'first' for the first position in the dataset. ");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("bed", true,
			"BED file describing the regions a random position should be sampled from (only usable if start was set to 'random').");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("N", true, "Number of samples tio be extracted (default: 100).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("step", true, "Step size (default: 1).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("format", true, "Output format (raw | wig | codoc). Default: guessed from output file extension");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("o", true, "Output file.");
		opt.setRequired(true);
		options.addOption(opt);

		options.addOption("v", "verbose", false, "be verbose.");

		line = parser.parse(options, args);
		if (line.hasOption("v"))
		    debug = true;
		else
		    debug = false;

		File outFile = new File(line.getOptionValue("o"));
		File covFile = new File(line.getOptionValue("cov"));
		File vcfFile = (line.hasOption("vcf") ? new File(line.getOptionValue("vcf")) : null);
		int N = line.hasOption("N") ? Integer.parseInt(line.getOptionValue("N")) : 100;
		int step = line.hasOption("step") ? Integer.parseInt(line.getOptionValue("step")) : 1;
		String start = line.getOptionValue("start");
		File bedFile = line.hasOption("bed") ? new File(line.getOptionValue("bed")) : null;
		String format = line.hasOption("format") ? line.getOptionValue("format") : null;
		if (format == null) {
		    if (outFile.getName().endsWith(".wig"))
			format = "wig";
		    if (outFile.getName().endsWith(".codoc"))
			format = "codoc";
		}

		subSample(covFile, vcfFile, bedFile, start, N, step, format, outFile);

		return;

	    } else if (subcommand.equalsIgnoreCase("calculateScoreHistogram")) {
		options = new Options();

		Option opt = new Option("cov", true, "Coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "Coverage variants (VCF).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("bed", true, "BED file.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("binSize", true, "Histogram bin size (default: 1).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("scale", true, "Coverage signal scaling (default: 1.0).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("o", true, "Output.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("minScoreOut", true, "Minimum avg. score for writing an interval to bedOut (default: null).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("maxScoreOut", true, "Minimum avg. score for writing an interval to bedOut (default: null).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("bedOut", true,
			"BED file that will contain all intervals from the input BED that have an avg. score between minScoreOut and maxScoreOut (default: null).");
		opt.setRequired(false);
		options.addOption(opt);

		options.addOption("v", "verbose", false, "be verbose.");

		line = parser.parse(options, args);
		if (line.hasOption("v"))
		    debug = true;
		else
		    debug = false;

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		File covFile = new File(line.getOptionValue("cov"));
		File vcfFile = (line.hasOption("vcf") ? new File(line.getOptionValue("vcf")) : null);
		int binSize = (line.hasOption("binSize") ? Integer.parseInt(line.getOptionValue("binSize")) : 1);
		float scale = (line.hasOption("scale") ? Float.parseFloat(line.getOptionValue("scale")) : 1.0f);
		File bedFile = new File(line.getOptionValue("bed"));

		float minScoreOut = (line.hasOption("minScoreOut") ? Float.parseFloat(line.getOptionValue("minScoreOut")) : 1.0f);
		float maxScoreOut = (line.hasOption("maxScoreOut") ? Float.parseFloat(line.getOptionValue("maxScoreOut")) : 0.0f);
		File bedOutFile = (line.hasOption("bedOut") ? new File(line.getOptionValue("bedOut")) : null);

		BinnedHistogram bh = calculateScoreHistogram(covFile, vcfFile, bedFile, null, binSize, scale, minScoreOut, maxScoreOut, bedOutFile);
		System.out.println(bh);
		out.println(bh.toCSV());

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
		System.exit(0);

	    } else if (subcommand.equalsIgnoreCase("calculateCoveragePerUCSCFeature")) {
		options = new Options();

		Option opt = new Option("cov", true, "Coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "Coverage variants (VCF).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("ucscDb", true, "UCSC database file. Download from UCSC using the 'all fields from selected table' option.");
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

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		File covFile = new File(line.getOptionValue("cov"));
		File vcfFile = line.hasOption("vcf") ? new File(line.getOptionValue("vcf")) : null;
		File ucscDbFile = new File(line.getOptionValue("ucscDb"));

		calculateCoveragePerUCSCFeature(covFile, vcfFile, ucscDbFile, out);

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
		System.exit(0);
	    } else if (subcommand.equalsIgnoreCase("calculateCoveragePerBedFeature")) {
		options = new Options();

		Option opt = new Option("cov", true, "Coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "Coverage variants (VCF).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("bed", true, "BED file.");
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

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		List<File> covFiles = new ArrayList<File>();
		for (String f : line.getOptionValues("cov"))
		    covFiles.add(new File(f));

		List<File> vcfFiles = new ArrayList<File>();
		if (line.hasOption("vcf"))
		    for (String f : line.getOptionValues("vcf"))
			vcfFiles.add(new File(f));

		File bedFile = new File(line.getOptionValue("bed"));

		calculateCoveragePerBedFeature(covFiles, vcfFiles, bedFile, out);

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
		System.exit(0);
	    } else if (subcommand.equalsIgnoreCase("cancerNormalFilter")) {
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
		opt = new Option("o", true, "Output.");
		opt.setRequired(true);
		options.addOption(opt);
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
		File cov1VcfFile = line.hasOption("cov1Vcf") ? new File(line.getOptionValue("cov1Vcf")) : null;
		File cov2File = new File(line.getOptionValue("cov2"));
		File cov2VcfFile = line.hasOption("cov2Vcf") ? new File(line.getOptionValue("cov2Vcf")) : null;
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
		File cov1VcfFile = line.hasOption("cov1Vcf") ? new File(line.getOptionValue("cov1Vcf")) : null;
		File cov2File = new File(line.getOptionValue("cov2"));
		File cov2VcfFile = line.hasOption("cov2Vcf") ? new File(line.getOptionValue("cov2Vcf")) : null;
		PrintStream corrOut = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    corrOut = new PrintStream(line.getOptionValue("o"));

		correlateCoverageFiles(cov1File, cov1VcfFile, cov2File, cov2VcfFile, corrOut);

		if (!line.getOptionValue("o").equals("-"))
		    corrOut.close();
		System.exit(0);
	    } else if (subcommand.equalsIgnoreCase("dumpCoverage")) {
		options = new Options();

		Option opt = new Option("cov", true, "Compressed coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("covVcf", true, "Compressed coverage VCF file (optional).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("chroms", true, "Ordered list of chromosomes (optional).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("n", "max", true, "Maximum number of printed lines.");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("o", true, "Output.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("alias", true, "Optional chromosome alias file. ");
		opt.setRequired(false);
		options.addOption(opt);

		options.addOption("printCoords", false, "Print also coordinates");

		options.addOption("v", "verbose", false, "be verbose.");

		line = parser.parse(options, args);
		if (line.hasOption("v"))
		    debug = true;
		else
		    debug = false;

		File covFile = new File(line.getOptionValue("cov"));
		File covVcfFile = line.hasOption("covVcf") ? new File(line.getOptionValue("covVcf")) : null;
		boolean printCoords = line.hasOption("printCoords");

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		List<String> chroms = null;
		if (line.hasOption("chroms")) {
		    chroms = new ArrayList<String>();
		    for (String c : line.getOptionValues("chroms"))
			chroms.add(c);
		}

		Integer n = line.hasOption("n") ? Integer.parseInt(line.getOptionValue("n")) : null;

		dumpIterator(covFile, covVcfFile, printCoords, line.hasOption("alias") ? new File(line.getOptionValue("alias")) : null, chroms, n, out);

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
		System.exit(0);
	    } else if (subcommand.equalsIgnoreCase("calculateScoreHistogramFromVCF")) {
		options = new Options();

		Option opt = new Option("cov", true, "Compressed coverage.");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("vcf", true, "VCF file");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("csv", true, "Result CSV file that can be loaded into R");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("binSize", true, "Histogram bin size (default: 1).");
		opt.setRequired(false);
		options.addOption(opt);

		options.addOption("p", "onlypass", false, "Count only PASS VCF entries (default: false).");

		opt = new Option("o", true, "Output file (or '-' for stdout). All per-chrom histograms will be printed to this file/stream");
		opt.setRequired(true);
		options.addOption(opt);

		options.addOption("v", "verbose", false, "be verbose.");

		line = parser.parse(options, args);
		if (line.hasOption("v"))
		    debug = true;
		else
		    debug = false;

		File covFile = new File(line.getOptionValue("cov"));
		File vcf = new File(line.getOptionValue("vcf"));
		PrintStream outCSV = (line.hasOption("csv") ? (line.getOptionValue("csv").equals("-") ? System.err
			: new PrintStream(line.getOptionValue("csv"))) : null);
		int binSize = (line.hasOption("binSize") ? Integer.parseInt(line.getOptionValue("binSize")) : 1);
		boolean onlypass = line.hasOption("p");

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		calculateScoreHistogramFromVCF(covFile, vcf, outCSV, out, binSize, onlypass);

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
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
