package at.cibiv.codoc;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * Iterates over the coverage. Note that this iterator does not support padding intervals.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CompressedCoverageIterator implements CoverageIterator<CoverageHit> {

	/**
	 * List of listeners.
	 */
	protected List<ChromosomeIteratorListener> listeners = new ArrayList<ChromosomeIteratorListener>();

	CodeWordInterval current = null;
	private CoverageDecompressor decomp;
	private Long pos1;
	private String currentchr;
	private Long currentpos;
	private int blockIdx = 0;
	CoverageHit currentCoverage = null;

	private boolean wasChangedChrom = false;

	public CompressedCoverageIterator(PropertyConfiguration conf) throws Throwable {
		this.decomp = new CoverageDecompressor(conf);
		blockIdx = 0;
		decomp.loadBlock(blockIdx);

		current = decomp.getFirstInterval();
		if (current != null) {
			pos1 = current.getMin();
		}
	}

	public CompressedCoverageIterator(CoverageDecompressor decomp) throws Throwable {
		this.decomp = decomp;
		blockIdx = 0;
		decomp.loadBlock(blockIdx);

		current = decomp.getFirstInterval();
		if (current != null) {
			pos1 = current.getMin();
		}
	}

	/**
	 * Creates a new coverage iterator that starts from the passed position.
	 * Starts at position 1 of chr if the passed pos was not found! Creates an
	 * empty iterator if the passed chr was not found.
	 * 
	 * @param decomp
	 * @param firstPos
	 * @throws Throwable
	 */
	public CompressedCoverageIterator(CoverageDecompressor decomp, GenomicPosition firstPos) throws Throwable {
		this.decomp = decomp;
		blockIdx = decomp.getCurrentBlockIdx();
		// NOTE: we have to load a block here to get the padding intervals loaded!
		decomp.loadBlock(blockIdx);
		CoverageHit h = decomp.query(firstPos);
		if (h == null) {
			// retry with 1st position in chr (should be padded)
			firstPos = new GenomicPosition(firstPos.getChromosomeOriginal(), 0);
			h = decomp.query(firstPos);
		}

		if (h != null && h.isPadding()) {
			// move over padding interval
			firstPos = new GenomicPosition(firstPos.getChromosomeOriginal(), h.getInterval().getMax());
			h = decomp.query(firstPos);
		}

		if (h == null) {
			current = null;
		} else {
			current = h.getInterval();
			pos1 = firstPos.get1Position();
		}
	}

	@Override
	public boolean hasNext() {
		return current != null;
	}

	@Override
	public boolean wasChangedChrom() {
		return wasChangedChrom;
	}

	/**
	 * sets
	 */
	public void exhaust() {
		current = null;
	}

	@Override
	public CoverageHit next() {
		if (!hasNext())
			return null;

		// FIXME: set padding of this interval if not in min/max
		this.currentCoverage = decomp.interpolate(current, pos1);

		// was the chrom changed?
		if (!current.getOriginalChrom().equals(currentchr)) {
			// System.out.println("NEW " + current.getChr() );
			for (ChromosomeIteratorListener l : listeners)
				try {
					l.notifyChangedChromosome(current.getOriginalChrom());
				} catch (IOException e) {
					e.printStackTrace();
				}
			wasChangedChrom = true;
		} else
			wasChangedChrom = false;

		currentchr = current.getOriginalChrom();
		currentpos = pos1;
		pos1++;
		if (pos1 > current.getMax()) {
			if ((current.getNext() != null) && (!current.getNext().getOriginalChrom().equals(current.getOriginalChrom())))
				pos1 = current.getNext().getMin();
			current = current.getNext();
			if (current == null) {
				// load next block?
				blockIdx++;
				if (decomp.getLeftBorders().get(blockIdx) != null) {
					try {
						decomp.loadBlock(blockIdx);
						current = decomp.getFirstInterval();
						// now current contains an interval from 1 to the block
						// start.
						if (current != null) {
							current = current.getNext();
							if (current != null) {
								pos1 = current.getMin();
							}
						}
					} catch (Throwable e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} else
					try {
						decomp.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
			} else {
			}
		}
		return this.currentCoverage;
	}

	@Override
	public void remove() {
	}

	@Override
	public long getOffset() {
		return currentpos;
	}

	@Override
	public String getCurrentReference() {
		return currentchr;
	}

	@Override
	public GenomicPosition getGenomicPosition() {
		if ( currentchr == null ) return null;
		if ( currentpos == null ) return null;
		return new GenomicPosition(currentchr, currentpos, COORD_TYPE.ONEBASED);
	}

	@Override
	public Integer nextCoverage() {
		return Math.round(next().getInterpolatedCoverage());
	}

	@Override
	public Double nextCoveragePrecise() {
		return (double) next().getInterpolatedCoverage();
	}

	@Override
	public CoverageHit getCurrentCoverage() {
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

	/**
	 * Note that subtracting of WIG files can e.g. be done with the
	 * java_genomics_toolkit
	 * 
	 * @param a
	 * @param b
	 * @param out
	 */
	@Deprecated
	public static void subtract(CompressedCoverageIterator a, CompressedCoverageIterator b, PrintStream out) {
		CoverageHit hita = null, hitb = null;
		GenomicPosition posa = null, posb = null;
		while (a.hasNext() || b.hasNext()) {
			if (hita == null) {
				hita = a.next();
				posa = a.getGenomicPosition();
			}
			if (hitb == null) {
				hitb = b.next();
				posb = b.getGenomicPosition();
			}
			int dir = posa.compareTo(posb);
			if (dir < 0) {
				int diff = Math.round(hita.getInterpolatedCoverage());
				out.println(posa + "\t" + diff);
				hita = null;
			} else if (dir > 0) {
				int diff = Math.round(hitb.getInterpolatedCoverage());
				out.println(posb + "\t" + diff);
				hitb = null;
			} else {
				int diff = Math.round(hita.getInterpolatedCoverage() - hitb.getInterpolatedCoverage());
				out.println(posa + "\t" + diff);
				hita = null;
				hitb = null;
			}
		}
	}

	public CoverageDecompressor getDecomp() {
		return decomp;
	}


}
