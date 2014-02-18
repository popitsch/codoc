package at.cibiv.codoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

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
			int left = positions.get(chr).get(h);
			int right = positions.get(chr).get(h + 1) - 1;
			int leftCoverage = coverages.get(chr).get(h);
			int rightCoverage = coverages2.get(chr).get(h + 1);
			hit = new CoverageHit(chr, left, right, leftCoverage, rightCoverage, qpos, scaleFactor);
		} else {
			int left = positions.get(chr).get(h);
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

	public static void main(String[] args) throws CodocException {
		String chr = "chr1";
		int blockIdx = 1;
		List<Integer> positions = new ArrayList<Integer>() {
			{
				add(10);
				add(20);
				add(30);
				add(40);
			}
		};
		List<Integer> coverages = new ArrayList<Integer>() {
			{
				add(1);
				add(10);
				add(5);
				add(0);
			}
		};
		List<Integer> coverages2 = new ArrayList<Integer>() {
			{
				add(0);
				add(5);
				add(10);
				add(1);
			}
		};
		Map<String, List<Integer>> p = new HashMap<String, List<Integer>>();
		Map<String, List<Integer>> c = new HashMap<String, List<Integer>>();
		Map<String, List<Integer>> c2 = new HashMap<String, List<Integer>>();
		p.put(chr, positions);
		c.put(chr, coverages);
		c2.put(chr, coverages2);

		CachedBlock cb = new CachedBlock(blockIdx, p, c, c2);

		// for (int q = 1; q < 50; q++) {
		// GenomicPosition x = new GenomicPosition(chr, q, COORD_TYPE.ONEBASED);
		// System.out.println(x.toString1basedOrig() + " -> " + cb.query(x,
		// 1.0f));
		// }

		List<String> chrs = new ArrayList<>();
		chrs.add("chr1");
		CachedBlockIterator it = cb.iterator(chrs, 1.0f);

		while (it.hasNext()) {
			CoverageHit hit = it.next();
			if (hit == null)
				System.out.println("EOF");
			else
				System.out.println(it.getGenomicPosition().toString1basedOrig() + " -> " + hit.getInterpolatedCoverage() + " " + hit);
		}

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
