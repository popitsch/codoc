package at.cibiv.codoc.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
import at.cibiv.ngs.tools.bed.BedToolsCoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.wig.WigIterator;

/**
 * Some helper methods.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class SamplingTools {

	private static float getMuFromMid50perc(List<Integer> samples) {
		if (samples.size() == 0)
			return 0f;
		Collections.sort(samples);
		float sum = 0f, c = 0f;
		int start = (int) ((float) samples.size() * 0.25f);
		int end = (int) ((float) samples.size() * 0.75f);
		for (int i = start; i < end; i++) {
			sum += samples.get(i);
			c++;
		}
		if (c == 0)
			return 0f;
		float mu = sum / c;
		return mu;
	}

	/**
	 * Calculate the average difference between the first <maxreads> mapped
	 * reads
	 * 
	 * @param sortedSamFile
	 * @param maxreads
	 * @return
	 */
	public static float calcStartPosDiffMuFromSam(File sortedSamFile,
			int maxreads) {
		final SAMFileReader inputSam = new SAMFileReader(sortedSamFile);
		inputSam.setValidationStringency(ValidationStringency.SILENT);
		SAMRecordIterator it = inputSam.iterator();

		Integer lastOff = null;
		List<Integer> samples = new ArrayList<Integer>();

		while (it.hasNext()) {
			SAMRecord rec = it.next();
			if (rec.getReadUnmappedFlag())
				continue;
			if (lastOff == null)
				lastOff = rec.getAlignmentStart();
			int diff = rec.getAlignmentStart() - lastOff;
			samples.add(diff);
			lastOff = rec.getAlignmentStart();
			maxreads--;
			if (maxreads < 0)
				break;
		}
		return getMuFromMid50perc(samples);
	}

	public static float calcStartPosDiffMuFromWig(File wigFile,
			float scaleFactor, int maxpositions) throws IOException {
		WigIterator wi = new WigIterator(wigFile, scaleFactor);

		Float lastVal = 0f;
		Integer lastOff = 0;
		List<Integer> samples = new ArrayList<Integer>();

		while (wi.hasNext()) {
			Float val = wi.next();
			if (!val.equals(lastVal)) {
				if (lastOff == null)
					lastOff = (int) wi.getOffset();
				int diff = (int) wi.getOffset() - lastOff;
				lastVal = val;
				samples.add(diff);
				lastOff = (int) wi.getOffset();
			}
			maxpositions--;
			if (maxpositions < 0)
				break;
		}
		return getMuFromMid50perc(samples);
	}

	public static float calcStartPosDiffMuFromBedToolsCov(File coverageFile,
			float scaleFactor, int maxpositions) throws IOException {
		BedToolsCoverageIterator it = new BedToolsCoverageIterator(
				coverageFile, COORD_TYPE.ONEBASED, 1.0f);
		Double lastVal = 0d;
		Integer lastOff = 0;
		List<Integer> samples = new ArrayList<Integer>();

		while (it.hasNext()) {
			Double val = it.next();
			if (!val.equals(lastVal)) {
				if (lastOff == null)
					lastOff = (int) it.getOffset();
				int diff = (int) it.getOffset() - lastOff;
				lastVal = val;
				samples.add(diff);
				lastOff = (int) it.getOffset();
			}
			maxpositions--;
			if (maxpositions < 0)
				break;
		}
		return getMuFromMid50perc(samples);
	}

	// public static void main(String[] args) throws IOException {
	// System.out.println(calcStartPosDiffMuFromWig(new File(
	// "c:/data/testbams/test-sorted.bam.wig"), 3000));
	//
	// for (int i = 0; i < 100; i++) {
	// int max = i * 100;
	// long start = System.currentTimeMillis();
	// float mu = calcStartPosDiffMuFromWig(new File(
	// "c:/data/testbams/test-sorted.bam.wig"), max);
	// // float mu = calcStartPosDiffMuFromSam(new File(
	// // "c:/data/testbams/bigbam.bam"), max);
	// int k = GolombOutputStream.calcK(mu);
	// System.out.println(max + " -> " + mu + "/" + k);
	//
	// System.out.println(System.currentTimeMillis() - start);
	// }
	//
	// }

}
