package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.cibiv.codoc.CompressedCoverageIterator;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.bed.BedToolsCoverageIterator;
import at.cibiv.ngs.tools.sam.iterator.CoverageIterator;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.StringUtils;

@Deprecated
public class CompareCoverageIteratorsOLD {
	CoverageIterator<?> it1, it2;

	double countPos = 0;
	double maxDiff = 0;
	double diffPos = 0;
	double meanDiffSum = 0;
	public static final int[] BUCKETS = new int[] { 0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024 };
	Map<Integer, Double[]> buckets = new HashMap<Integer, Double[]>();
	int wrong = 0;

	List<String> chroms = null;
	List<String> chromsPrefixed = null;

	private GenomicPosition maxDiffPos;

	private Integer maxDiffReal = 0;

	private Integer maxDiffFound = 0;

	/**
	 * Constructor.
	 * 
	 * @param it1Real
	 * @param it2Found
	 */
	public CompareCoverageIteratorsOLD(CoverageIterator<?> it1Real, CoverageIterator<?> it2Found, List<String> chroms) {
		if (it1Real == null || it2Found == null)
			throw new RuntimeException("it was null");
		if (!it1Real.hasNext() || !it2Found.hasNext())
			throw new RuntimeException("it was empty");
		this.it1 = it1Real;
		this.it2 = it2Found;
		this.chroms = chroms;
		this.chromsPrefixed = new ArrayList<>();
		if (chroms != null)
			for (String c : chroms)
				chromsPrefixed.add(StringUtils.prefixedChr(c));
	}

	/**
	 * Measure.
	 * 
	 * @param cov1Real
	 * @param cov2Found
	 */
	private void measure(Integer cov1Real, Integer cov2Found, GenomicPosition pos) {
		if (cov1Real == null || cov2Found == null)
			return; // wont measure here.

		countPos++;
		if (countPos % 10000000 == 0)
			System.err.println("measured " + countPos + ", " + it1.getGenomicPosition().toString1based());

		// if (! cov1Real.equals(cov2Found)) {
		// wrong++;
		// System.out.println("err " + it1.getGenomicPosition().toString1based()
		// + "=" + cov1Real +
		// "/" + it2.getGenomicPosition().toString1based() + "=" + cov2Found);
		// if (wrong > 30 ) System.exit(0);
		// }
		// else
		// ;//System.out.println("OK " + it1.getGenomicPosition() + "=" +
		// cov1Real + "/" + it2.getGenomicPosition() + "=" + cov2Found);

		// if (cov1Real == 0)
		// if (cov2Found != 0)
		// System.err.println("Found " + cov1Real + " vs. " + cov2Found + " at "
		// + pos.toString1based());

		double diff = cov2Found - cov1Real;
		if (Math.abs(diff) > maxDiff) {
			maxDiff = Math.max(maxDiff, Math.abs(diff));
			maxDiffPos = pos;
			maxDiffReal = cov1Real;
			maxDiffFound = cov2Found;
		}
		if (diff != 0)
			diffPos++;
		meanDiffSum = meanDiffSum + Math.abs(diff);

		// if (diff > 1f) {
		// if (itree.query(new GenomicPosition(it1.getCurrentReference(),
		// it1.getOffset() - 1)).size() == 0) {
		// System.out.println("err " + it1.getGenomicPosition().toString1based()
		// + "=" + cov1Real + "/" + it2.getGenomicPosition().toString1based() +
		// "="
		// + cov2Found);
		// }
		//
		// if (cov1Real == 0f) {
		// System.err.println("err " + it1.getGenomicPosition().toString1based()
		// + "=" + cov1Real + "/" + it2.getGenomicPosition().toString1based() +
		// "="
		// + cov2Found);
		// }
		// }

		int bucketId = BUCKETS.length - 1;
		for (int i = 0; i < BUCKETS.length; i++) {
			if (cov1Real <= BUCKETS[i]) {
				bucketId = i;
				break;
			}
		}
		Double[] data = buckets.get(bucketId);
		if (data == null) {
			data = new Double[5];
			data[0] = 0d;
			data[1] = 0d;
			data[2] = 0d;
			data[3] = 0d;
			data[4] = 0d;
		}
		if (diff < 0) {
			// we are underestimating the coverage
			data[0] += 1f;
			data[1] += Math.abs(diff);
		} else if (diff > 0) {
			// we are overestimating the coverage
			data[2] += 1f;
			data[3] += Math.abs(diff);
			data[4]++;
		} else {
			data[4]++;
		}
		buckets.put(bucketId, data);
	}

	/**
	 * compare
	 */
	public void compareCoverageIterators() {
		while (true) {
			Integer cov1 = it1.nextCoverage();
			Integer cov2 = it2.nextCoverage();
			GenomicPosition pos1 = it1.getGenomicPosition();
			GenomicPosition pos2 = it2.getGenomicPosition();

			if (chroms != null) {
				while (!chromsPrefixed.contains(StringUtils.prefixedChr(it1.getCurrentReference()))) {
					if (!it1.hasNext())
						break;
					cov1 = it1.nextCoverage();
					pos1 = it1.getGenomicPosition();
				}
				while (!chromsPrefixed.contains(StringUtils.prefixedChr(it2.getCurrentReference()))) {
					if (!it2.hasNext())
						break;
					cov2 = it2.nextCoverage();
					pos2 = it2.getGenomicPosition();
				}
			}

			// System.out.println(pos1.toString1based() + "/" +
			// pos2.toString1based() + " " + it1.getCurrentCoverage() + "/" +
			// it2.getCurrentCoverage() );
			// if (cov1 != cov2)
			// System.err.println(pos1.toString1based() + "/" +
			// pos2.toString1based() + " " + it1.getCurrentCoverage() + "/" +
			// it2.getCurrentCoverage());

			while (pos1.compareTo(pos2) < 0) {
				measure(cov1, null, pos1);
				if (!it1.hasNext()) {
					break;
				}
				// System.out.println("skip 1 " + pos1 + " vs " + pos2);
				cov1 = it1.nextCoverage();
				pos1 = it1.getGenomicPosition();
			}

			while (pos1.compareTo(pos2) > 0) {
				measure(null, cov2, pos2);
				if (!it2.hasNext()) {
					break;
				}
				// System.out.println("skip 2 " + pos1 + " vs " + pos2);
				cov2 = it2.nextCoverage();
				pos2 = it2.getGenomicPosition();
			}

			if (!it2.hasNext() || !it1.hasNext())
				break;
			measure(cov1, cov2, pos1);
		}
	}

	public Double getCountsForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		return (data[0] + data[2] + data[4]);
	}

	public Double getMeanDiffForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		Double counts = getCountsForBucket(bucketId);
		if ((counts == null) || (counts == 0))
			return 0d;
		return (data[1] + data[3]) / getCountsForBucket(bucketId);
	}

	public Double getUnderDiffForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		if (data[0] == 0)
			return 0d;
		return (data[1]) / (data[0]);
	}

	public Double getOverDiffForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		if (data[2] == 0)
			return 0d;
		return (data[3]) / (data[2]);
	}

	public Double getUnderCountsForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		return (data[0]);
	}

	public Double getOverCountsForBucket(int bucketId) {
		Double[] data = buckets.get(bucketId);
		if (data == null)
			return null;
		return (data[2]);
	}

	/**
	 * Create a diff report
	 * 
	 * @param out
	 */
	private void createDiffReport(PrintStream out) {

		out.println("bucket\tbucket-border\tmeandiff\tcount\tmeandiff-overestimated\tcount-overestimated\tmeandiff-underestimated\tcount-underestimated");

		for (int i = 0; i < StatsEntry.BUCKETS.length; i++) {

			Double md = getMeanDiffForBucket(i);
			if (md == null)
				md = 0d;
			Double cc = getCountsForBucket(i);
			if (cc == null)
				cc = 0d;

			Double mdover = getOverDiffForBucket(i);
			if (mdover == null)
				mdover = 0d;
			Double ccover = getOverCountsForBucket(i);
			if (ccover == null)
				ccover = 0d;

			Double mdunder = getUnderDiffForBucket(i);
			if (mdunder == null)
				mdunder = 0d;
			Double ccunder = getUnderCountsForBucket(i);
			if (ccunder == null)
				ccunder = 0d;

			out.println(i + "\t" + BUCKETS[i] + "\t" + md + "\t" + cc + "\t" + mdover + "\t" + ccover + "\t" + mdunder + "\t" + ccunder);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("---------------------------\n");
		sb.append("count-pos\t" + String.format("%1$,.0f", countPos) + "\n");
		sb.append("maxi-diff\t" + maxDiff + "\n");
		if (maxDiffPos != null)
			sb.append("maxi-diff-pos\t" + maxDiffPos.toString1based() + ", real:" + maxDiffReal + ", found:" + maxDiffFound + "\n");
		sb.append("mean-diff\t" + (meanDiffSum / countPos) + "\n");
		sb.append("diff-posi\t" + String.format("%1$,.0f", diffPos) + "\n");

		return sb.toString();
	}

	public static CoverageIterator<?> instantiate(File f, List<String> chromosomes) throws Throwable {
		CoverageIterator<?> ret = null;
		String fn = f.getAbsolutePath();
		if (fn.endsWith(".tdf")) {
			System.out.println("Instantiating " + f + " as TDFIterator");
			ret = new TDFIterator(f, chromosomes);
		} else if (fn.endsWith(".comp") || fn.endsWith(".compressed")) {
			System.out.println("Instantiating " + f + " as CompressedCoverageIterator");
			PropertyConfiguration conf1 = CoverageDecompressor.getDefaultConfiguration();
			conf1.setProperty(CoverageDecompressor.OPT_COV_FILE, fn);
			conf1.setProperty(CoverageDecompressor.OPT_VCF_FILE, "AUTO");
			ret = new CompressedCoverageIterator(conf1);
		} else {
			System.out.println("Instantiating " + f + " as BedToolsCoverageIterator");
			ret = new BedToolsCoverageIterator(f, COORD_TYPE.ONEBASED, 1.0f);
		}
		return ret;
	}

	public static void compareIterators(File real, File found, List<String> chroms, PrintStream out) throws Throwable {
		CoverageIterator<?> itReal = instantiate(real, chroms);
		CoverageIterator<?> itFound = instantiate(found, chroms);
		if (itReal == null)
			throw new IOException("Could not instatiate " + real);
		if (itFound == null)
			throw new IOException("Could not instatiate " + found);
		CompareCoverageIteratorsOLD c = new CompareCoverageIteratorsOLD(itReal, itFound, chroms);
		c.compareCoverageIterators();
		out.println("Report for compareIterators " + real + " vs. " + found);
		out.println();
		out.println(c.toString());
		out.println();
		c.createDiffReport(out);
		out.close();
	}

	/**
	 * @param argsget
	 * @throws Throwable
	 */
	public static void main(String[] args) throws Throwable {

		// args = new String[] {
		// "/project/oesi/bgraph/compression-eval/data/geuvadis_UCSC_Genome/geuvadis_UCSC_Genome-sorted.bam.bedtoolscoverage",
		// "/project/oesi/bgraph/compression-eval/data/geuvadis_UCSC_Genome/geuvadis_UCSC_Genome-sorted.bam.w100.tdf",
		// "2",
		// "/project/oesi/bgraph/compression-eval/data/geuvadis_UCSC_Genome/geuvadis_UCSC_Genome-sorted.bam.w100.tdf.RESULTS2"
		// };
		// args = new String[] {
		// "/project/oesi/bgraph/compression-eval/data/NA12878/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped.bam.bedtoolscoverage",
		// "/project/oesi/bgraph/compression-eval/data/NA12878/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped.bam.p001.comp",
		// "/project/oesi/bgraph/compression-eval/data/NA12878/test"
		// };
		// args = new String[] {
		// "/scratch/testbams/small.bam.compressed.rawcoverage",
		// "/scratch/testbams/small.bam.g2.compressed",
		// "-"
		// };
		if (args.length < 4)
			throw new RuntimeException("usage: CompareCoverageIterators <real> <found> <chroms> <results>");

		String[] cc = args[2].split(",");
		ArrayList<String> chroms = null;
		if (!args[2].equals("-")) {
			chroms = new ArrayList<>();
			System.out.print("Comparing chromosomes: ");
			for (String c : cc) {
				chroms.add(c);
				System.out.print(c + ", ");
			}
			System.out.println();
		} else
			System.out.print("Comparing all chromosomes!");

		PrintStream out = System.out;
		if (!args[3].equals("-"))
			out = new PrintStream(args[3]);
		compareIterators(new File(args[0]), new File(args[1]), chroms, out);
	}

}
