package at.cibiv.codoc.eval;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.broad.igv.tdf.TDFDataset;
import org.broad.igv.tdf.TDFReader;
import org.broad.igv.tdf.TDFTile;

import at.cibiv.ngs.tools.lds.GenomicITree;
import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.StringUtils;
import at.cibiv.ngs.tools.util.TabIterator;

public class TDFFile {

	TDFReader reader;

	GenomicITree cache = new GenomicITree(null);

	public TDFFile(File f) {
		this.reader = TDFReader.getReader(f.getAbsolutePath());

		for (String chr : reader.getChromosomeNames()) {
			if (chr.equals("All"))
				continue;
			String dsname = "/" + chr + "/raw";
			TDFDataset data = reader.getDataset("/" + chr + "/raw");
			if (data == null) {
				System.out.println("No dataset " + dsname + " in " + reader.getDatasetNames());
				continue;
			}
			// System.out.println("Dataset: " + dsname );
			for (TDFTile t : data.getTiles()) {
				for (int i = 0; i < t.getStart().length; i++) {
					long start1 = t.getStart()[i] + 1;
					// if ( start1==100001)
					// System.out.println(t.getStartPosition(i) + "," +
					// t.getEndPosition(i) + "," + t.getNames());
					long end1 = t.getEnd()[i];
					float coverage = t.getData(0)[i];
					GenomicInterval gi = new GenomicInterval(StringUtils.prefixedChr(chr), start1, end1, coverage + "");
					// System.out.println(gi);
					cache.insert(gi);
				}
			}
		}
		System.out.println("Loaded intervals: " + cache.getIntervals().size());
		cache.buildTree();
		System.out.println("Loaded " + cache);
	}

	public Float query(GenomicPosition pos) throws IOException {
		Set<? extends GenomicInterval> ret = cache.query1based(pos);
		if ((ret == null) || (ret.isEmpty()))
			return null;
		GenomicInterval gi = ret.iterator().next();
		if (ret.size() > 1) {
			// System.out.println("AMB " + ret);
			for (GenomicInterval mayberight : ret)
				if (mayberight.getMin().equals(mayberight.getMax()))
					gi = mayberight;
		}
		return Float.parseFloat(gi.getUri());
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		TDFFile test = new TDFFile(new File("xxx"));

		TabIterator it = new TabIterator(new File("helper.rawcoverage"), "#");
		while (it.hasNext()) {
			String[] t = it.next();
			String chr = t[0];
			int pos1 = Integer.parseInt(t[1]);
			GenomicPosition pos = new GenomicPosition(chr, pos1, COORD_TYPE.ONEBASED);
			float realCoverage = Float.parseFloat(t[2]);
			Float queried = test.query(pos);
			if (queried == null)
				queried = 0f;

			if (realCoverage != queried) {
				System.out.println(pos.toString1based() + ": " + realCoverage + "!=" + queried);
			}
		}

	}

}
