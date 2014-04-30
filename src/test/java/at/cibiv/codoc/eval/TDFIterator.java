package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.broad.igv.tdf.TDFDataset;
import org.broad.igv.tdf.TDFReader;
import org.broad.igv.tdf.TDFTile;

import at.cibiv.ngs.tools.sam.iterator.ChromosomeIteratorListener;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * A coverage iterator that is filled from a TDF file.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class TDFIterator implements CoverageIterator<Float> {

	/**
	 * List of listeners.
	 */
	protected List<ChromosomeIteratorListener> listeners = new ArrayList<ChromosomeIteratorListener>();

	TDFReader reader;
	Iterator<TDFTile> tilesIterator;
	TDFTile currentTile = null;
	Iterator<String> chromIt;
	int currentPosition = 0;
	String currentChrom = null;
	Float currentCoverage = null;

	int currentIdx = 0;

	private boolean hasNext;

	private int reportedPosition;

	private String reportedChrom;

	private Float reportedCoverage;

	boolean chromWasChanged = false;

	/**
	 * Iterates over the coveraged in the passed tdf file.
	 * 
	 * @param f
	 */
	public TDFIterator(File f) {
		this(f, null);
	}

	public TDFIterator(File f, List<String> chromosomes) {
		this.reader = TDFReader.getReader(f.getAbsolutePath());
		if (chromosomes == null) {
			chromosomes = new ArrayList<>();
			for (String chr : reader.getChromosomeNames()) {
				if (chr.equals("All"))
					continue;
				chromosomes.add(chr);
			}
		}
		init(chromosomes);
	}

	private void init(Collection<String> chromosomes) {
		if (chromosomes.size() < 1)
			throw new RuntimeException("At least one chromosome has to be provided");
		this.chromIt = chromosomes.iterator();

		loadTI(chromIt.next());
		this.hasNext = loadNext() != null;
	}

	public void loadTI(String chr) {
		String dsname = "/" + chr + "/raw";
		TDFDataset data = reader.getDataset(dsname);
		if (data == null) {
			throw new RuntimeException("No dataset " + dsname + " in " + reader.getDatasetNames());
		}
		tilesIterator = data.getTiles().iterator();
		currentTile = tilesIterator.next();
		currentPosition = currentTile.getStart()[0];
		currentChrom = chr;
		currentIdx = 0;
	}

	@Override
	public boolean hasNext() {
		return hasNext;
	}

	@Override
	public Float next() {
		if (!hasNext)
			return null;

		if (reportedChrom == null || !reportedChrom.equals(currentChrom))
			chromWasChanged = true;
		else
			chromWasChanged = false;

		reportedPosition = currentPosition;
		reportedChrom = currentChrom;
		reportedCoverage = currentCoverage;

		this.hasNext = loadNext() != null;

		return reportedCoverage;
	}

	public void debug() {
		System.out.println("Starts: " + Arrays.toString(currentTile.getStart()));
		System.out.println("Ends: " + Arrays.toString(currentTile.getEnd()));
		System.out.println("pos: " + currentPosition);
	}

	private boolean nextTile() {

		// get next tile if any.
		if (tilesIterator.hasNext()) {
			currentTile = tilesIterator.next();
			currentPosition = currentTile.getStart()[0];
			currentIdx = 0;
			// if (currentTile.getStart().length <= 1) {
			// currentPosition = currentTile.getStart()[0];
			// currentIdx = 0;
			// } else {
			// currentPosition = currentTile.getStart()[0];
			// currentIdx = 0;
			// }
		} else {
			// load next chrom if any
			if (!chromIt.hasNext())
				return false;
			String c = chromIt.next();
			for (ChromosomeIteratorListener l : listeners)
				try {
					l.notifyChangedChromosome(c);
				} catch (IOException e) {
					e.printStackTrace();
				}
			loadTI(c);
			this.hasNext = loadNext() != null;
		}
		return true;
	}

	private Float loadNext() {
		currentPosition++;

		if (currentPosition > currentTile.getEnd()[currentIdx]) {

			if (currentIdx == currentTile.getEnd().length - 1) {
				if (!nextTile()) {
					return null;
				}
			} else {
				currentIdx++;
			}
		}

		if (currentPosition <= currentTile.getStart()[currentIdx]) {
			// this is an uncovered area. We iterate over it but set cov to 0
			currentCoverage = 0f;
		} else
			currentCoverage = currentTile.getData(0)[currentIdx];

		return currentCoverage;
	}

	@Override
	public void remove() {
	}

	@Override
	public long getOffset() {
		return reportedPosition;
	}

	@Override
	public String getCurrentReference() {
		return reportedChrom;
	}

	@Override
	public Float getCurrentCoverage() {
		return reportedCoverage;
	}

	@Override
	public GenomicPosition getGenomicPosition() {
		return new GenomicPosition(reportedChrom, reportedPosition, COORD_TYPE.ONEBASED);
	}

	@Override
	public Integer nextCoverage() {
		Float n = next();
		if (n == null)
			return null;
		return n.intValue();
	}

	@Override
	public Double nextCoveragePrecise() {
		Float n = next();
		if (n == null)
			return null;
		return (double) n;
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
	public boolean wasChangedChrom() {
		return chromWasChanged;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		List<String> chromosomes = new ArrayList<String>();
		chromosomes.add("chr20");
		chromosomes.add("chr21");
		TDFIterator t = new TDFIterator(new File("xxx"));
		while (t.hasNext()) {
			Float cov = t.next();
			System.out.println(t.getGenomicPosition().toString1based() + " => " + cov);
			if (t.wasChangedChrom())
				System.out.println("chrom changed!");
		}
	}

}
