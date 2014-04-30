package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * Tests some cov. iterator methods.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageIteratorTest implements CoverageIterator<Integer>, ChromosomeIteratorListener {

	Iterator<Integer> it = null;
	Iterator<Integer> posit = null;
	Iterator<String> refit = null;
	String currentReference = null;
	int currentPos;
	Integer currentCoverage = null;

	/**
	 * List of listeners.
	 */
	protected List<ChromosomeIteratorListener> listeners = new ArrayList<ChromosomeIteratorListener>();

	public CoverageIteratorTest(List<Integer> coverageValues, List<Integer> posValues, List<String> refValues) {

		this.it = coverageValues.iterator();
		this.refit = refValues.iterator();
		this.posit = posValues.iterator();
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public Integer next() {
		currentPos = posit.next();
		String c = refit.next();
		if (currentReference == null)
			currentReference = c;
		else if (!c.equals(currentReference))
			for (ChromosomeIteratorListener l : listeners)
				try {
					l.notifyChangedChromosome(c);
				} catch (IOException e) {
					e.printStackTrace();
				}
		currentReference = c;
		currentCoverage = it.next();

		// System.err.println(currentReference+":"+currentPos+ " -> " +
		// currentCoverage );

		return currentCoverage;
	}

	@Override
	public void remove() {
	}

	@Override
	public long getOffset() {
		return currentPos;
	}

	@Override
	public String getCurrentReference() {
		return currentReference;
	}

	@Override
	public GenomicPosition getGenomicPosition() {
		return new GenomicPosition(getCurrentReference(), getOffset(), COORD_TYPE.ONEBASED);
	}

	@Override
	public Integer nextCoverage() {
		return next();
	}

	@Override
	public Double nextCoveragePrecise() {
		return (double) next();
	}

	@Override
	public Integer getCurrentCoverage() {
		return currentCoverage;
	}

	@Override
	public Integer skip() {
		return nextCoverage();
	}

	@Override
	public void addListener(ChromosomeIteratorListener l) {
		this.listeners.add(l);
	}

	@Override
	public void removeListener(ChromosomeIteratorListener l) {
		this.listeners.remove(l);
	}

	@Override
	public void notifyChangedChromosome(String chromosome) throws IOException {
		System.out.println("NEW CHROM " + chromosome);
	}

	@Override
	public boolean wasChangedChrom() {
		throw new RuntimeException("not implemented");
	}

	public static void addCov(String chr, int pos1, int cov, List<Integer> coverageValues, List<Integer> posValues, List<String> refValues) {
		coverageValues.add(cov);
		refValues.add(chr);
		posValues.add(pos1);
	}

	public static void main(String[] args) throws Throwable {

		List<Integer> coverageValues = new ArrayList<Integer>();
		List<Integer> posValues = new ArrayList<Integer>();
		List<String> refValues = new ArrayList<String>();

		addCov("chr1", 1, 0, coverageValues, posValues, refValues);
		addCov("chr1", 2, 2, coverageValues, posValues, refValues);
		addCov("chr1", 3, 3, coverageValues, posValues, refValues);
		addCov("chr1", 4, 4, coverageValues, posValues, refValues);
		// addCov( "chr2", 1, 1 );
		addCov("chr2", 10, 2, coverageValues, posValues, refValues);
		addCov("chr2", 11, 3, coverageValues, posValues, refValues);
		addCov("chr2", 12, 4, coverageValues, posValues, refValues);
		addCov("chr2", 13, 0, coverageValues, posValues, refValues);
		addCov("chr2", 14, 5, coverageValues, posValues, refValues);
		addCov("chr3", 4, 6, coverageValues, posValues, refValues);

		CoverageIteratorTest c = new CoverageIteratorTest(coverageValues, posValues, refValues);
		File covFile = new File("src/test/resources/CoverageIteratorTest.comp");
		System.out.println("-------------------------");
		CoverageCompressor comp = new CoverageCompressor(c, null, covFile);
		System.out.println(comp.getStatistics().toString());
		System.out.println("-------------------------");
		CoverageDecompressor decomp = CoverageDecompressor.decompress(covFile, null);
		CompressedCoverageIterator decomit = decomp.getCoverageIterator();

		Iterator<Integer> it = coverageValues.iterator();
		Iterator<String> refit = refValues.iterator();
		Iterator<Integer> posit = posValues.iterator();

		// c = new CoverageIteratorTest(coverageValues, posValues, refValues);
		// c.addListener(c);
		// while (c.hasNext())
		// System.out.println(c.next() + " " +
		// c.getGenomicPosition().toString1based());
		while (decomit.hasNext()) {
			Float hit = decomit.next();
			System.out.println("POS " + decomit.getGenomicPosition().toString1based() + "\t" + hit);
		}

		// int cov = 0, pos =0;String chr=null;
		// while (decomit.hasNext()) {
		// CoverageHit hit = decomit.next();
		// do {
		// if ( !refit.hasNext() ) {System.out.println("EOF!");break;}
		// chr = refit.next();
		// cov = it.next();
		// pos = posit.next();
		// } while ( cov == 0 );
		//
		// if (! decomit.getPosition().getChromosome().equalsIgnoreCase(chr))
		// System.err.println("chr: " + decomit.getPosition().getChromosome() +
		// " vs " + chr);
		// if ( decomit.getPosition().get1Position() != pos )
		// System.err.println("pos: " + decomit.getPosition().get1Position() +
		// " vs " + pos);
		// if ( Math.round(hit.getInterpolatedCoverage()) != cov)
		// System.err.println("pos: " +
		// Math.round(hit.getInterpolatedCoverage()) + " vs " + cov);
		// System.out.println("POS "+ decomit.getPosition().toString1based() +
		// "\t" + hit.getInterpolatedCoverage() + "\t" + hit + "\t" +
		// hit.getInterval().getUri());
		// }
		System.out.println(decomp.getWorkDir());
		decomp.close();
		System.out.println("-------------------------");
	}

}
