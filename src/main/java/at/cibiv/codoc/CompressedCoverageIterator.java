package at.cibiv.codoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;

/**
 * Iterates over the coverage in a compressed CODOC file.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CompressedCoverageIterator implements CoverageIterator<Float>, ChromosomeIteratorListener {

	/**
	 * List of listeners.
	 */
	protected List<ChromosomeIteratorListener> listeners = new ArrayList<ChromosomeIteratorListener>();

	private CoverageDecompressor decomp;
	private int blockIdx = 0;
	CachedBlockIterator it = null;
	private List<String> chroms;
	private float scaleFactor;
	boolean lastBlock = false;

	private boolean exhausted = false;

	/**
	 * Constructor.
	 * 
	 * @param decomp
	 * @param chroms
	 * @param scaleFactor
	 * @throws CodocException
	 * @throws IOException
	 */
	public CompressedCoverageIterator(CoverageDecompressor decomp, List<String> chroms, float scaleFactor) throws CodocException, IOException {
		this.decomp = decomp;
		this.chroms = chroms;
		this.scaleFactor = scaleFactor;
		this.blockIdx = 0;
		this.it = decomp.loadBlock(blockIdx).iterator(chroms, scaleFactor);
		lastBlock = (blockIdx == decomp.chrData.size() - 1);
	}

	/**
	 * Constructor.
	 * 
	 * @param conf
	 * @param chroms
	 * @param scaleFactor
	 * @throws CodocException
	 * @throws IOException
	 */
	public CompressedCoverageIterator(PropertyConfiguration conf, List<String> chroms, float scaleFactor) throws CodocException, IOException {
		this.decomp = new CoverageDecompressor(conf);
		this.chroms = chroms;
		this.scaleFactor = scaleFactor;
		this.blockIdx = 0;
		this.it = decomp.loadBlock(blockIdx).iterator(chroms, scaleFactor);
		lastBlock = (blockIdx == decomp.chrData.size() - 1);
	}

	/**
	 * Deplete the iterator.
	 */
	public void exhaust() {
		this.exhausted = true;
	}

	@Override
	public boolean hasNext() {
		if (exhausted)
			return false;
		if (!it.hasNext() && lastBlock)
			return false;
		return true;
	}

	@Override
	public boolean wasChangedChrom() {
		return it.wasChangedChrom();
	}

	@Override
	public Float next() {

		if (!it.hasNext()) {
			blockIdx++;
			try {
				this.it = decomp.loadBlock(blockIdx).iterator(chroms, scaleFactor);
			} catch (CodocException | IOException e) {
				e.printStackTrace();
				return null;
			}
			if (blockIdx == decomp.chrData.size() - 1) {
				lastBlock = true;
			}
		}
		if (!it.hasNext())
			return null;

		Float hit = it.next();
		return hit;
	}

	@Override
	public void remove() {
	}

	@Override
	public long getOffset() {
		return it.getOffset();
	}

	@Override
	public String getCurrentReference() {
		return it.getCurrentReference();
	}

	@Override
	public GenomicPosition getGenomicPosition() {
		return it.getGenomicPosition();
	}

	@Override
	public Integer nextCoverage() {
		return Math.round(next());
	}

	@Override
	public Double nextCoveragePrecise() {
		return (double) next();
	}

	@Override
	public Float getCurrentCoverage() {
		return it.getCurrentCoverage();
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
		for (ChromosomeIteratorListener l : listeners)
			try {
				l.notifyChangedChromosome(it.getCurrentReference());
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	public CoverageDecompressor getDecomp() {
		return decomp;
	}

	public float getScaleFactor() {
		return scaleFactor;
	}


}
