package at.cibiv.codoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import at.cibiv.ngs.tools.util.GenomicPosition;

/**
 * An iterator that synchronized the genomic positions of two coverage iterators
 * and returns only values for the positions they have in common. The two
 * iterator values can be asked at the current iterator position.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class SyncedCompressedCoverageIterator implements Iterator<GenomicPosition> {
	CoverageDecompressor d1, d2;
	CompressedCoverageIterator it1, it2;
	CoverageHit cachedCov1, cachedCov2, currentCov1, currentCov2;
	private GenomicPosition cachedPosition;
	private GenomicPosition currentPosition;

	List<String> chroms;
	Iterator<String> chromIterator;
	String currentChrom = null;

	/**
	 * Constructor.
	 * 
	 * @param it1
	 * @param it2
	 * @throws Throwable 
	 */
	public SyncedCompressedCoverageIterator(CoverageDecompressor d1, CoverageDecompressor d2) throws Throwable {
		this(d1, d2, null);
	}

	/**
	 * Constructor.
	 * 
	 * @param it1
	 * @param it2
	 * @throws Throwable 
	 */
	public SyncedCompressedCoverageIterator(CoverageDecompressor d1, CoverageDecompressor d2, List<String> chromOrder) throws Throwable {
		this.d1 = d1;
		this.d2 = d2;
		if (chromOrder == null) {
			chromOrder = new ArrayList<>();
			chromOrder.addAll(d1.getChromosomes());
			chromOrder.addAll(d2.getChromosomes());
		}
		this.chroms = new ArrayList<>();
		for (String chr : chromOrder)
			if (!this.chroms.contains(chr)) {
				this.chroms.add(chr);
			}
		System.out.println("Chroms: " + Arrays.toString(chroms.toArray()));
		this.chromIterator = this.chroms.iterator();
		currentChrom = this.chromIterator.next();
		
		it1 = d1.getCoverageIterator(new GenomicPosition(currentChrom, 0));
		it2 = d2.getCoverageIterator(new GenomicPosition(currentChrom, 0));
	}

	/**
	 * Position iterators above each other.
	 * 
	 * @return
	 * @throws Throwable 
	 */
	private GenomicPosition gotoNextCommonPosition() throws Throwable {
	
		while (!it1.hasNext() || !it2.hasNext()) {
			if ( ! this.chromIterator.hasNext() )
				return null;
			// load next chrom 
			currentChrom = this.chromIterator.next();
			//System.out.println("load "+currentChrom);
			it1 = d1.getCoverageIterator(new GenomicPosition(currentChrom, 0));
			it2 = d2.getCoverageIterator(new GenomicPosition(currentChrom, 0));
		}


		cachedCov1 = it1.next();
		if (it1.wasChangedChrom() && ! it1.getCurrentReference().equals(currentChrom)) { it1.exhaust(); return gotoNextCommonPosition(); }
		cachedCov2 = it2.next();
		if (it2.wasChangedChrom()&& ! it2.getCurrentReference().equals(currentChrom)) { it2.exhaust(); return gotoNextCommonPosition(); }
		GenomicPosition pos1 = it1.getGenomicPosition();
		GenomicPosition pos2 = it2.getGenomicPosition();

		while (!pos1.equals(pos2)) {
			while (pos1.compareTo(pos2) < 0) {
				if (!it1.hasNext()) {
					return null;
				}
				// measure( cachedCov1, 0d, pos1 );
				cachedCov1 = it1.next();
				if (it1.wasChangedChrom()&& ! it1.getCurrentReference().equals(currentChrom)) { it1.exhaust(); return gotoNextCommonPosition(); }
				pos1 = it1.getGenomicPosition();
			}

			while (pos1.compareTo(pos2) > 0) {
				if (!it2.hasNext()) {
					return null;
				}
				cachedCov2 = it2.next();
				if (it2.wasChangedChrom()&& ! it2.getCurrentReference().equals(currentChrom)) { it2.exhaust(); return gotoNextCommonPosition(); }
				pos2 = it2.getGenomicPosition();
			}
		}

		if (!pos1.equals(pos2)) {
			System.out.println("ERR " + pos1 + ", " + pos2);
			return null;
		}

		//System.out.println(pos1.toString1based() + "/" +cachedCov1.getInterpolatedCoverage()+"/"+ cachedCov2.getInterpolatedCoverage()+"/"+  it1.hasNext() + "/" + it2.hasNext());
		
		// now we are synced.
		// measure(cachedCov1, cachedCov2, pos1);
		return pos1;
	}

	@Override
	public boolean hasNext() {
		if (cachedPosition == null)
			try {
				this.cachedPosition = gotoNextCommonPosition();
			} catch (Throwable e) {
				e.printStackTrace();
				return false;
			}
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

	public CoverageHit getCoverage1() {
		return cachedCov1;
	}

	public CoverageHit getCoverage2() {
		return cachedCov2;
	}

	/**
	 * @param args
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {
	}

}
