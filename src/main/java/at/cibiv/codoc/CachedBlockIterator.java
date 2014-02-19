package at.cibiv.codoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * Iterates over the coverage values in a cached block.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CachedBlockIterator implements CoverageIterator<Float> {

	/**
	 * List of listeners.
	 */
	protected List<ChromosomeIteratorListener> listeners = new ArrayList<ChromosomeIteratorListener>();
	private boolean wasChangedChrom = false;

	/**
	 * current BIT
	 */
	private CachedBlock cb;

	/**
	 * Iterates over the chromosomes of this block in the given order.
	 */
	private Iterator<String> chromOrderIterator;

	/**
	 * internal vars for caching
	 */
	private Float preloadedCoverage = null;
	private String preloadedChrom = null;
	private int preloadedPosition = 0;
	private int keyPosIdx = 0;
	boolean hasNext = true;

	/**
	 * Current state variables
	 */
	Float currentCoverage = null;
	String currentChrom = null;
	int currentPosition = 0;

	/**
	 * Scale factor.
	 */
	float scaleFactor;

	/**
	 * Constructor.
	 * 
	 * @param cb
	 * @param chroms
	 * @param scaleFactor
	 */
	public CachedBlockIterator(CachedBlock cb, List<String> chroms, float scaleFactor) {
		this.cb = cb;
		this.scaleFactor = scaleFactor;
		List<String> temp = new ArrayList<>();
		for (String c : chroms) {
			if (cb.positions.keySet().contains(c)) {
				temp.add(c);
			}
		}
		this.chromOrderIterator = temp.iterator();
		if (!chromOrderIterator.hasNext()) {
			hasNext = false;
		} else {
			preloadedChrom = chromOrderIterator.next();
			preloadedPosition = cb.positions.get(preloadedChrom).get(keyPosIdx) - 1;
		}

		preloadedCoverage = preloadCoverage();

	}

	@Override
	public boolean hasNext() {
		return hasNext;
	}

	@Override
	public Float next() {
		currentCoverage = preloadedCoverage;
		currentChrom = preloadedChrom;
		currentPosition = preloadedPosition;
		preloadedCoverage = preloadCoverage();
		return currentCoverage;
	}

	/**
	 * caching of next position.
	 * 
	 * @return
	 */
	private Float preloadCoverage() {
		Float ret = null;
		preloadedPosition++;
		if (preloadedPosition == cb.positions.get(preloadedChrom).get(keyPosIdx)) {
			keyPosIdx++;
			if (keyPosIdx == cb.positions.get(preloadedChrom).size()) {
				if (!chromOrderIterator.hasNext()) {
					hasNext = false;
					return null;
				} else {
					preloadedChrom = chromOrderIterator.next();
					keyPosIdx = 0;
					preloadedPosition = cb.positions.get(preloadedChrom).get(keyPosIdx) - 1;
					for (ChromosomeIteratorListener l : listeners)
						try {
							l.notifyChangedChromosome(preloadedChrom);
						} catch (IOException e) {
							e.printStackTrace();
						}
					wasChangedChrom = true;
					return preloadCoverage();
				}
			}

			int left = cb.positions.get(preloadedChrom).get(keyPosIdx - 1);
			int right = left;
			int leftCoverage = cb.coverages.get(preloadedChrom).get(keyPosIdx - 1);
			int rightCoverage = leftCoverage;
			ret = CoverageHit.getInterpolatedCoverage(left, right, leftCoverage, rightCoverage, preloadedPosition, this.scaleFactor);

		} else {

			int left = cb.positions.get(preloadedChrom).get(keyPosIdx - 1);
			int right = cb.positions.get(preloadedChrom).get(keyPosIdx) - 1;
			int leftCoverage = cb.coverages.get(preloadedChrom).get(keyPosIdx - 1);
			int rightCoverage = cb.coverages2.get(preloadedChrom).get(keyPosIdx);
			ret = CoverageHit.getInterpolatedCoverage(left, right, leftCoverage, rightCoverage, preloadedPosition, this.scaleFactor);
		}

		return ret;
	}

	
	@Override
	public boolean wasChangedChrom() {
		return wasChangedChrom;
	}

	@Override
	public void remove() {
	}

	@Override
	public long getOffset() {
		return currentPosition;
	}

	@Override
	public String getCurrentReference() {
		return currentChrom;
	}

	@Override
	public Float getCurrentCoverage() {
		return currentCoverage;
	}

	@Override
	public Double nextCoveragePrecise() {
		return (double) currentCoverage;
	}

	@Override
	public Integer nextCoverage() {
		return Math.round(next());
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
	public GenomicPosition getGenomicPosition() {
		if (currentChrom == null)
			return null;
		return new GenomicPosition(currentChrom, currentPosition, COORD_TYPE.ONEBASED);
	}

	public float getScaleFactor() {
		return scaleFactor;
	}

}
