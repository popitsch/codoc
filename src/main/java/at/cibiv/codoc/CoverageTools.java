package at.cibiv.codoc;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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
import at.cibiv.codoc.io.CoverageInputStream;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.bed.SimpleBEDFile;
import at.cibiv.ngs.tools.exon.ExonChromosomeTree;
import at.cibiv.ngs.tools.exon.ExonInterval;
import at.cibiv.ngs.tools.exon.Gene;
import at.cibiv.ngs.tools.exon.RefSeqDb;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.BinnedHistogram;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.vcf.SimpleVCFFile;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant;
import at.cibiv.ngs.tools.vcf.SimpleVCFVariant.ZYGOSITY;
import at.cibiv.ngs.tools.vcf.VCFIterator;

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
     * Dump the iterator to stdout
     * 
     * @param covTFile
     * @throws Throwable
     */
    public static void dumpIterator(File cov1File, File cov1VcfFile, boolean printPos, PrintStream out) throws Throwable {
	if (debug)
	    System.out.println("Load " + cov1File);
	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);
	try {
	    cov1.setMaxCachedBlocks(1);
	    CompressedCoverageIterator it1 = cov1.getCoverageIterator();
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
     * Extract a random sample from a codoc file.
     * 
     * @param covTFile
     * @throws Throwable
     */
    public static void extractRandomSample(File cov1File, File cov1VcfFile, File sampleRegionsBed, int N, int step, PrintStream out) throws Throwable {
	if (debug)
	    System.out.println("Load " + cov1File);
	CoverageDecompressor cov1 = CoverageDecompressor.loadFromFile(cov1File, cov1VcfFile);
	try {
	    cov1.setMaxCachedBlocks(1);
	    CompressedCoverageIterator it1 = cov1.getCoverageIterator();

	    SimpleBEDFile sampleRegions = new SimpleBEDFile(sampleRegionsBed);
	    GenomicPosition start = sampleRegions.getRandomPosition();

	    StringBuilder sb = new StringBuilder();
	    sb.append("# Sampled " + N + " with step " + step + " from " + start.toString1basedCanonicalHuman() + " in " + cov1File + "\n");
	    int c = 0, counter = 0;
	    boolean inRegion = false;
	    String lastchr = "";
	    while (it1.hasNext()) {
		Float hit1 = it1.next();
		GenomicPosition pos = it1.getGenomicPosition();
		if (pos.equals(start))
		    inRegion = true;

		if (inRegion) {
		    if (pos.get1Position() % step == 0) {
			if (!lastchr.equals(pos.getChromosome())) {
			    sb.append("# " + pos.toString1basedCanonicalHuman() + "\n");
			    lastchr = pos.getChromosome();
			}
			sb.append(hit1.floatValue() + "\n");
			counter++;
			if (counter == N)
			    break;
		    }
		}
		if (sb.length() > 500000) {
		    out.println(sb.toString());
		    sb = new StringBuilder();
		}
		if (++c % 100000 == 0)
		    System.err.print(".");
	    }
	    out.print(sb.toString());
	    if (counter < N)
		out.println("# Could no create complete sample as EOF was reached. Sample size: " + counter);
	    if (debug)
		System.out.println("Finished.");
	} finally {
	    cov1.close();
	}
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
	    System.out.println("Command:\textractRandomSample\tExtract a random sample from a codoc file.");
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

	// args = new String[] { "extractRandomSample",
	// "-cov", "src/test/resources/covcompress/small.compressed",
	// "-vcf", "src/test/resources/covcompress/small.vcf",
	// "-bed", "src/test/resources/covcompress/small.roi",
	// "-N", "100",
	// "-step", "2",
	// "-v", "-o", "-" };

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

	    if (subcommand.equalsIgnoreCase("extractRandomSample")) {
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

		opt = new Option("N", true, "Number of samples tio be extracted (default: 100).");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("step", true, "Step size (default: 1).");
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

		PrintStream out = System.out;
		if (!line.getOptionValue("o").equals("-"))
		    out = new PrintStream(line.getOptionValue("o"));

		File covFile = new File(line.getOptionValue("cov"));
		File vcfFile = (line.hasOption("vcf") ? new File(line.getOptionValue("vcf")) : null);
		File bedFile = new File(line.getOptionValue("bed"));
		int N = line.hasOption("N") ? Integer.parseInt(line.getOptionValue("N")) : 100;
		int step = line.hasOption("step") ? Integer.parseInt(line.getOptionValue("step")) : 1;

		extractRandomSample(covFile, vcfFile, bedFile, N, step, out);

		if (!line.getOptionValue("o").equals("-"))
		    out.close();
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

		opt = new Option("o", true, "Output.");
		opt.setRequired(true);
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

		dumpIterator(covFile, covVcfFile, printCoords, out);

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
