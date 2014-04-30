package at.cibiv.codoc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.ngs.tools.util.GenomicPosition;

/**
 * A memory-cached block of codewords.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CachedBlock {

	int blockIdx;
	Map<String, List<Integer>> positions;
	Map<String, List<Integer>> coverages;
	Map<String, List<Integer>> coverages2;

	/**
	 * @param blockIdx
	 * @param positions
	 *            the interval borders including the last coordinate
	 * @param coverages
	 *            the coverages at the interval borders
	 * @param coverages2
	 *            the coverages at the interval borders -1
	 * @param paddingDownstreamIntervals
	 * @param paddingUpstreamIntervals
	 */
	public CachedBlock(int blockIdx, Map<String, List<Integer>> positions, Map<String, List<Integer>> coverages, Map<String, List<Integer>> coverages2) {
		this.positions = positions;
		this.coverages = coverages;
		this.coverages2 = coverages2;
		this.blockIdx = blockIdx;
	}

	public int getBlockIdx() {
		return blockIdx;
	}

	public void setBlockIdx(int blockIdx) {
		this.blockIdx = blockIdx;
	}

	public CoverageHit query(GenomicPosition pos, float scaleFactor) {
		return query(pos.getChromosomeOriginal(), (int) pos.get1Position(), scaleFactor);
	}

	public Integer getFirstPos(String chr) {
		List<Integer> p = positions.get(chr);
		if (p == null || p.size() == 0)
			return null;
		return p.get(0);
	}

	/**
	 * Query block.
	 * 
	 * @param chr
	 * @param qpos
	 *            1-based position
	 * @param scaleFactor
	 * @return
	 */
	public CoverageHit query(String chr, int qpos, float scaleFactor) {
		CoverageHit hit = null;
		long startTime = System.nanoTime();
		List<Integer> pos = positions.get(chr);
		if (pos == null) {
			// System.out.println("chr " + chr + " not found [" +
			// positions.keySet() + "]");
			return null;
		}
		int h = Collections.binarySearch(positions.get(chr), qpos);

		if (h < 0) {
			h = (-h) - 2;
			if (h < 0) {
				return null;
			}
			if (h >= pos.size() - 1) {
				return null;
			}
			int left = pos.get(h);
			int right = pos.get(h + 1) - 1;
			int leftCoverage = coverages.get(chr).get(h);
			int rightCoverage = coverages2.get(chr).get(h + 1);
			hit = new CoverageHit(chr, left, right, leftCoverage, rightCoverage, qpos, scaleFactor);
		} else {
			int left = pos.get(h);
			int right = left;
			int leftCoverage = coverages.get(chr).get(h);
			int rightCoverage = leftCoverage;
			hit = new CoverageHit(chr, left, right, leftCoverage, rightCoverage, qpos, scaleFactor);
		}
		if (hit != null)
			hit.setExecTime(System.nanoTime() - startTime);

		return hit;
	}

	@Override
	public String toString() {
		return "[Block " + getBlockIdx() + "]";
	}

	public CachedBlockIterator iterator(List<String> chroms, float scaleFactor) throws CodocException {
		return new CachedBlockIterator(this, chroms, scaleFactor);
	}

	/**
	 * 
	 * @return the number of codewords stored in this block
	 */
	public int size() {
		int size = 0;
		for (String k : positions.keySet())
			size += positions.get(k).size();
		return size;
	}

}
