package at.cibiv.codoc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import at.ac.univie.cs.mis.lds.index.itree.ITree;
import at.ac.univie.cs.mis.lds.index.itree.Interval;
import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.util.GenomicPosition;

/**
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CodeWordIntervalTree extends GenomicITree {

	public CodeWordIntervalTree(String uri) {
		super(uri);
	}

	/** 
	 * @return a list of all contained intervals.
	 */
	@Override
	public List<CodeWordInterval> getIntervals() {
		List<CodeWordInterval> ret = new ArrayList<CodeWordInterval>();
		for (String c : itrees.keySet()) {
			ITree<Long> t = itrees.get(c);
			Iterator<Interval<Long>> it = t.iterator();
			while (it.hasNext())
				ret.add((CodeWordInterval) it.next());
		}
		return ret;
	}


	/**
	 * Queries the tree for the passed position (1-based). Returns a sorted
	 * resultset.
	 * 
	 * @return a list of intervals overlapping with the query point or null if
	 *         no information about the given chromosome is available.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public SortedSet<CodeWordInterval> query1based(GenomicPosition q) {
		if (q == null)
			return null;
		return (SortedSet<CodeWordInterval>) query(new CodeWordInterval(q.getChromosome(), q.get1Position(), q.get1Position(), null));
	}
	
}
