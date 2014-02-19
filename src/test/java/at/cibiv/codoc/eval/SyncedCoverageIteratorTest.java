package at.cibiv.codoc.eval;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.CoverageHit;
import at.cibiv.codoc.SyncedCompressedCoverageIterator;
import at.cibiv.ngs.tools.bed.BedToolsCoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

public class SyncedCoverageIteratorTest {

	public static void addCov(String chr, int pos1, int cov, List<Integer> coverageValues, List<Integer> posValues, List<String> refValues) {
		coverageValues.add(cov);
		refValues.add(chr);
		posValues.add(pos1);
	}

	public static CoverageDecompressor getCoverageDecompressor(List<String> refValues, List<Integer> posValues, List<Integer> coverageValues, File temp)
			throws Throwable {
		// write to temp file
		PrintStream o = new PrintStream(temp);
		Iterator<Integer> it = coverageValues.iterator();
		Iterator<String> refit = refValues.iterator();
		Iterator<Integer> posit = posValues.iterator();
		while (it.hasNext()) {
			String ref = refit.next();
			int pos = posit.next();
			int cov = it.next();
			
			
			o.println(ref + "\t" + pos + "\t" + cov);
		}
		BedToolsCoverageIterator bit = new BedToolsCoverageIterator(temp, COORD_TYPE.ONEBASED, 1.0f);
		while ( bit.hasNext()) {
			double cov = bit.next();
			System.err.println(bit.getGenomicPosition().toString1basedOrig() + "\t" + cov);
		}
		bit = new BedToolsCoverageIterator(temp, COORD_TYPE.ONEBASED, 1.0f);

		CoverageCompressor comp = new CoverageCompressor(bit, null, temp);
		comp.dumpWarnings();
		CoverageDecompressor decomp = CoverageDecompressor.decompress(temp, null);
		System.err.println("---------------------");
		return decomp;
	}

	public static void main(String[] args) throws Throwable {

		List<Integer> cv1 = new ArrayList<Integer>();
		List<Integer> pos1 = new ArrayList<Integer>();
		List<String> ref1 = new ArrayList<String>();

		addCov("chr1", 1, 1, cv1, pos1, ref1);
		addCov("chr1", 2, 2, cv1, pos1, ref1);
		addCov("chr1", 3, 3, cv1, pos1, ref1);
		addCov("chr1", 4, 4, cv1, pos1, ref1);

		addCov("chr2", 1, 1, cv1, pos1, ref1);
		addCov("chr2", 2, 1, cv1, pos1, ref1);
		addCov("chr2", 3, 0, cv1, pos1, ref1);
		addCov("chr2", 4, 1, cv1, pos1, ref1);

		addCov("chr3", 4, 2, cv1, pos1, ref1);
		// addCov( "chr3", 5, 2, cv1, pos1, ref1 );

		List<Integer> cv2 = new ArrayList<Integer>();
		List<Integer> pos2 = new ArrayList<Integer>();
		List<String> ref2 = new ArrayList<String>();

		addCov("chr3", 4, 2, cv2, pos2, ref2);
		// addCov( "chr3", 5, 2, cv2, pos2, ref2 );
		addCov("chr1", 1, 1, cv2, pos2, ref2);
		addCov("chr1", 2, 2, cv2, pos2, ref2);
		addCov("chr1", 3, 3, cv2, pos2, ref2);
		addCov("chr1", 4, 4, cv2, pos2, ref2);
		addCov("chr2", 1, 1, cv2, pos2, ref2);
		addCov("chr2", 2, 1, cv2, pos2, ref2);
		addCov("chr2", 3, 0, cv2, pos2, ref2);
		addCov("chr2", 4, 1, cv2, pos2, ref2);

		File temp1 = new File("temp1.delme");
		CoverageDecompressor cov1 = getCoverageDecompressor(ref1, pos1, cv1, temp1);
		

		//CompressedCoverageIterator it1 = cov1.getCoverageIterator(new GenomicPosition("chr1", 1));
		CompressedCoverageIterator it1 = cov1.getCoverageIterator();
		System.out.println(it1 );
		while (it1.hasNext()) {
			Float h1 = it1.next();
			System.out.println(it1.getGenomicPosition().toString1based() + "\t" + h1);
		}
		System.out.println("--------------");

		cov1.interactiveQuerying();
		
		
		File temp2 = new File("temp2.delme");
		CoverageDecompressor cov2 = getCoverageDecompressor(ref2, pos2, cv2, temp2);
		
		CompressedCoverageIterator it2 = cov2.getCoverageIterator(new GenomicPosition("chr1", 1));
		//it2 = cov2.getCoverageIterator(new GenomicPosition("chr3", 1));
		while (it2.hasNext()) {
			Float h2 = it2.next();
			System.out.println(it2.getGenomicPosition().toString1based() + "\t" + h2);
		}
		System.out.println("--------------");

		cov2.interactiveQuerying();

		SyncedCompressedCoverageIterator sit = new SyncedCompressedCoverageIterator(cov1, cov2);
		while (sit.hasNext()) {
			GenomicPosition pos = sit.next();
			Float h1 = sit.getCoverage1();
			Float h2 = sit.getCoverage2();
			System.out.println(pos.toString1based() + "\t" + h1 + "\t" + h2);
		}
		temp1.delete();
		temp2.delete();
	}

}
