package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.bed.BedToolsCoverageIterator;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.StringUtils;

/**
 * An iterator that synchronized the genomic positions of two coverage iterators
 * and returns only values for the positions they have in common. The two
 * iterator values can be asked at the current iterator position.
 * 
 * FIXME: under construction
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class SyncedCoverageIteratorComparator implements Iterator<GenomicPosition> {
	CoverageIterator<?> it1, it2;
	Double cachedCov1, cachedCov2, currentCov1, currentCov2;
	private GenomicPosition cachedPosition;
	private GenomicPosition currentPosition;

	public static final int[] BUCKETS = new int[] { 0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024 };
	Map<Integer, Double[]> buckets = new HashMap<Integer, Double[]>();
	double maxDiff = 0;
	private GenomicPosition maxDiffPos;
	double maxDiffReal = 0d;
	double maxDiffFound = 0d;
	double diffPos = 0d;
	double meanDiffSum = 0d;
	double countPos = 0;

	File fileReal = null;
	File fileFound = null;

	List<String> chroms = null;
	List<String> chromsPrefixed = null;

	/**
	 * Constructor.
	 * 
	 * @param it1
	 * @param it2
	 */
	public SyncedCoverageIteratorComparator(CoverageIterator<?> it1, CoverageIterator<?> it2, List<String> chroms) {
		this.it1 = it1;
		this.it2 = it2;
		this.chroms = chroms;
		this.chromsPrefixed = new ArrayList<>();
		if (chroms != null)
			for (String c : chroms)
				chromsPrefixed.add(StringUtils.prefixedChr(c));
	}

	/**
	 * Measure a value
	 * 
	 * @param real
	 * @param found
	 * @param pos
	 */
	private void measure(Double real, Double found, GenomicPosition pos) {

		// skip chrom?
		if (chroms != null) {
			if (!chromsPrefixed.contains(StringUtils.prefixedChr(pos.getChromosome())))
				return;
		}

		countPos++;
		if (real == null)
			real = 0d;
		if (found == null)
			found = 0d;
		double diff = found - real;

		// if ( Math.abs(diff) > 0) {
		// System.err.println("ERR at " + pos.toString1based() + ": " + real +
		// " vs " + found);
		// ((TDFIterator) it2 ).debug();
		// System.exit(1);
		// }

		if (Math.abs(diff) > maxDiff) {
			maxDiff = Math.max(maxDiff, Math.abs(diff));
			maxDiffPos = pos;
			maxDiffReal = real;
			maxDiffFound = found;
		}
		int bucketId = BUCKETS.length - 1;
		for (int i = 0; i < BUCKETS.length; i++) {
			if (real <= BUCKETS[i]) {
				bucketId = i;
				break;
			}
		}
		Double[] data = buckets.get(bucketId);
		if (data == null) {
			data = new Double[2];
			data[0] = 0d;
			data[1] = 0d;
		}
		data[0] += 1f;
		data[1] += Math.abs(diff);
		buckets.put(bucketId, data);
	}

	/**
	 * Position iterators above each other.
	 * 
	 * @return
	 */
	private GenomicPosition gotoNextCommonPosition() {
		if (!it1.hasNext())
			return null;
		if (!it2.hasNext())
			return null;
		cachedCov1 = it1.nextCoveragePrecise();
		cachedCov2 = it2.nextCoveragePrecise();
		GenomicPosition pos1 = it1.getGenomicPosition();
		GenomicPosition pos2 = it2.getGenomicPosition();

		while (!pos1.equals(pos2)) {
			while (pos1.compareTo(pos2) < 0) {
				if (!it1.hasNext()) {
					return null;
				}
				measure(cachedCov1, 0d, pos1);
				cachedCov1 = it1.nextCoveragePrecise();
				pos1 = it1.getGenomicPosition();
			}

			while (pos1.compareTo(pos2) > 0) {
				if (!it2.hasNext()) {
					return null;
				}
				cachedCov2 = it2.nextCoveragePrecise();
				pos2 = it2.getGenomicPosition();
			}
		}

		if (!pos1.equals(pos2)) {
			System.out.println("ERR " + pos1 + ", " + pos2);
			return null;
		}

		// now we are synced.
		measure(cachedCov1, cachedCov2, pos1);
		return pos1;
	}

	@Override
	public boolean hasNext() {
		if (cachedPosition == null)
			this.cachedPosition = gotoNextCommonPosition();
		return (cachedPosition != null);
	}

	@Override
	public GenomicPosition next() {
		if (cachedPosition == null)
			if (!hasNext())
				return null;
		currentPosition = cachedPosition;
		currentCov1 = cachedCov1;
		currentCov2 = cachedCov2;
		cachedPosition = null;
		return currentPosition;
	}

	@Override
	public void remove() {
	}

	public GenomicPosition getGenomicPosition() {
		return currentPosition;
	}

	public Double getCoverage1() {
		return cachedCov1;
	}

	public Double getCoverage2() {
		return cachedCov2;
	}

	public File getFileReal() {
		return fileReal;
	}

	public void setFileReal(File fileReal) {
		this.fileReal = fileReal;
	}

	public File getFileFound() {
		return fileFound;
	}

	public void setFileFound(File fileFound) {
		this.fileFound = fileFound;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("real\t" + getFileReal() + "\n");
		sb.append("found\t" + getFileFound() + "\n");
		sb.append("size-real\t" + getFileReal().length() + "\n");
		sb.append("size-found\t" + getFileFound().length() + "\n");
		sb.append("count-pos\t" + String.format("%1$,.0f", countPos) + "\n");
		sb.append("maxi-diff\t" + maxDiff + "\n");
		if (maxDiffPos != null)
			sb.append("maxi-diff-pos\t" + maxDiffPos.toString1based() + ", real:" + maxDiffReal + ", found:" + maxDiffFound + "\n");
		sb.append("mean-diff\t" + (meanDiffSum / countPos) + "\n");
		sb.append("diff-posi\t" + String.format("%1$,.0f", diffPos) + "\n");
		sb.append("---------------------------\n");
		sb.append("bucket\tbucket-border\tmeandiff\tcount\n");
		for (int i = 0; i < BUCKETS.length; i++) {
			Double[] data = buckets.get(i);
			if (data == null) {
				data = new Double[2];
				data[0] = 0d;
				data[1] = 0d;
			}
			double div = 0d;
			if (data[0] != 0)
				div = (data[1] / data[0]);
			sb.append(i + "\t" + BUCKETS[i] + "\t" + div + "\t" + data[0] + "\n");
		}
		return sb.toString();
	}

	/**
	 * Instantiate an iterator.
	 * 
	 * @param f
	 * @param chromosomes
	 * @return
	 * @throws Throwable
	 */
	public static CoverageIterator<?> instantiate(File f, List<String> chromosomes) throws Throwable {
		CoverageIterator<?> ret = null;
		String fn = f.getAbsolutePath();
		if (fn.endsWith(".tdf")) {
			System.out.println("Instantiating " + f + " as TDFIterator");
			ret = new TDFIterator(f, chromosomes);
		} else if (fn.endsWith(".comp") || fn.endsWith(".compressed")) {
			System.out.println("Instantiating " + f + " as CompressedCoverageIterator");
			PropertyConfiguration conf1 = CoverageDecompressor.getDefaultConfiguration();
			conf1.setProperty(CoverageDecompressor.OPT_COV_FILE, fn);
			conf1.setProperty(CoverageDecompressor.OPT_VCF_FILE, "AUTO");
			ret = new CompressedCoverageIterator(conf1, chromosomes, 1.0f);
		} else {
			System.out.println("Instantiating " + f + " as BedToolsCoverageIterator");
			ret = new BedToolsCoverageIterator(f, COORD_TYPE.ONEBASED, 1.0f);
		}
		return ret;
	}

	/**
	 * Compare two iterators and print stats.
	 * 
	 * @param real
	 * @param found
	 * @param chroms
	 * @param out
	 * @throws Throwable
	 */
	public static void compareIterators(File real, File found, List<String> chroms, PrintStream out) throws Throwable {
		CoverageIterator<?> itReal = instantiate(real, chroms);
		CoverageIterator<?> itFound = instantiate(found, chroms);
		if (itReal == null)
			throw new IOException("Could not instatiate " + real);
		if (itFound == null)
			throw new IOException("Could not instatiate " + found);

		SyncedCoverageIteratorComparator sync = new SyncedCoverageIteratorComparator(itReal, itFound, chroms);
		sync.setFileReal(real);
		sync.setFileFound(found);

		while (sync.hasNext()) {
			GenomicPosition pos = sync.next();
			if (pos.get0Position() % 10000000 == 0)
				System.out.println("Handled " + pos);
		}
		out.println(sync);
		out.close();
	}

	/**
	 * @param args
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {

		if (args.length < 4)
			throw new RuntimeException("usage: SyncedCoverageIteratorComparator <real> <found> <chroms> <results>");

		String[] cc = args[2].split(",");
		ArrayList<String> chroms = null;
		if (!args[2].equals("-")) {
			chroms = new ArrayList<>();
			System.out.print("SyncedCoverageIteratorComparator comparing chromosomes: ");
			for (String c : cc) {
				chroms.add(c);
				System.out.print(c + ", ");
			}
			System.out.println();
		} else
			System.out.print("Comparing all chromosomes!");

		PrintStream out = System.out;
		if (!args[3].equals("-"))
			out = new PrintStream(args[3]);
		compareIterators(new File(args[0]), new File(args[1]), chroms, out);
		System.out.println("Finished.");
	}
}
