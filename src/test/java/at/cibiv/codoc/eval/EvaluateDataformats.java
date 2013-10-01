package at.cibiv.codoc.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.time.StopWatch;

import at.cibiv.codoc.CoverageCompressor;
import at.cibiv.codoc.CoverageCompressor.QUANT_METHOD;
import at.cibiv.codoc.CoverageDecompressor;
import at.cibiv.codoc.CoverageHit;
import at.cibiv.codoc.eval.StatsEntry.ENTRY_TYPE;
import at.cibiv.codoc.io.AbstractDataBlock.BLOCK_COMPRESSION_METHOD;
import at.cibiv.ngs.tools.util.DebugUtil;
import at.cibiv.ngs.tools.util.GenomicPosition;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;
import at.cibiv.ngs.tools.util.TabIterator;

import bgraph.util.FileUtils;
import bgraph.util.PropertyConfiguration;

public class EvaluateDataformats {

	static boolean dontredo = true;

	private File BAM;
	private File VCF;
	private File IGV_GENOME;
	private File resultDir;
	private PrintStream report;
	List<StatsEntry> entries = new ArrayList<StatsEntry>();
	private File temporaryFile;
	private File rawcoverageFile;
	private PrintStream diffReport;
	private PrintStream sizeReport;

	public EvaluateDataformats(File BAM, File VCF, File IGV_GENOME, File resultDir) throws FileNotFoundException {
		this.BAM = BAM;
		this.VCF = VCF;
		this.IGV_GENOME = IGV_GENOME;
		this.resultDir = resultDir;

		this.report = new PrintStream(new File(resultDir, BAM.getName() + ".FINALREPORT.txt"));
		this.diffReport = new PrintStream(new File(resultDir, BAM.getName() + ".DIFFREPORT.csv"));
		this.sizeReport = new PrintStream(new File(resultDir, BAM.getName() + ".SIZEREPORT.csv"));
		this.temporaryFile = new File(resultDir, BAM.getName() + ".helper");
		this.rawcoverageFile = new File(temporaryFile.getAbsolutePath() + ".rawcoverage");
	}

	private void log(String s) {
		System.out.println(s);
	}

	private void headline(String s) {
		log("================================== " + s + " ==================================");
	}

	/**
	 * Helper.
	 * 
	 * @param proc
	 * @throws IOException
	 */
	private void checkProcess(Process proc) throws IOException {

		BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
		// read the output from the command
		log("----------------stdout----------------------");
		String s;
		while ((s = stdInput.readLine()) != null) {
			log(s);
		}
		log("----------------stderr----------------------");
		// read any errors from the attempted command
		while ((s = stdError.readLine()) != null) {
			log(s);
		}
		log("--------------------------------------");
		if (proc.exitValue() != 0) {
			log("Error in TDF conversion (status " + proc.exitValue() + ")");
			throw new IOException("Error in TDF conversion");
		}
	}

	private void close() {
		report.println("Finished successfully!");
		report.close();
		diffReport.close();
		sizeReport.close();
		log("Finished.");
	}

	/**
	 * Do the compression
	 * 
	 * @throws Throwable
	 */
	private void compress() throws Throwable {

		StopWatch w = new StopWatch();
		Runtime rt = Runtime.getRuntime();
		// INIT
		DebugUtil.debug(BAM, VCF, IGV_GENOME, report);

		// BGRAPH, perc, 0.2
		StatsEntry bgraphEntry1 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, perc 0.2");
		headline("Compressing " + bgraphEntry1.getName());
		bgraphEntry1.setFile(new File(resultDir, BAM.getName() + ".bgraph.p02.comp"));
		if (bgraphEntry1.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry1.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry1.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry1.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.2");

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry1.setCompressionTime(w.getTime());
		}
		bgraphEntry1.setByteSize(bgraphEntry1.getFile().length());
		entries.add(bgraphEntry1);

		// BGRAPH, perc, 1.0
		StatsEntry bgraphEntry2 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, perc 0.1");
		headline("Compressing " + bgraphEntry2.getName());
		bgraphEntry2.setFile(new File(resultDir, BAM.getName() + ".bgraph.p01.comp"));
		if (bgraphEntry2.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry2.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry2.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry2.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.1");

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry2.setCompressionTime(w.getTime());
		}
		bgraphEntry2.setByteSize(bgraphEntry2.getFile().length());
		entries.add(bgraphEntry2);

		// BGRAPH, perc, 0.05
		StatsEntry bgraphEntry3 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, perc 0.05");
		headline("Compressing " + bgraphEntry3.getName());
		bgraphEntry3.setFile(new File(resultDir, BAM.getName() + ".bgraph.p005.comp"));
		if (bgraphEntry3.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry3.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry3.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry3.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.PERC.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "0.05");

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry3.setCompressionTime(w.getTime());
		}
		bgraphEntry3.setByteSize(bgraphEntry3.getFile().length());
		entries.add(bgraphEntry3);

		// BGRAPH, grid, 2
		StatsEntry bgraphEntry4 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, grid 2");
		headline("Compressing " + bgraphEntry4.getName());
		bgraphEntry4.setFile(new File(resultDir, BAM.getName() + ".bgraph.g2.comp"));
		if (bgraphEntry4.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry4.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry4.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry4.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.GRID.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "2");

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry4.setCompressionTime(w.getTime());
		}
		bgraphEntry4.setByteSize(bgraphEntry4.getFile().length());
		entries.add(bgraphEntry4);

		// BGRAPH2grid
		StatsEntry bgraphEntry5 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, log2grid");
		headline("Compressing " + bgraphEntry5.getName());
		bgraphEntry5.setFile(new File(resultDir, BAM.getName() + ".bgraph.log2.comp"));
		if (bgraphEntry5.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry5.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry5.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry5.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.LOG2.name());

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry5.setCompressionTime(w.getTime());
		}
		bgraphEntry5.setByteSize(bgraphEntry5.getFile().length());
		entries.add(bgraphEntry5);

		// BGRAPH, grid, 1
		StatsEntry bgraphEntry6 = new StatsEntry(ENTRY_TYPE.BGRAPH, "BGRAPH, grid 1");
		headline("Compressing " + bgraphEntry6.getName());
		bgraphEntry6.setFile(new File(resultDir, BAM.getName() + ".bgraph.g1.comp"));
		if (bgraphEntry6.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + bgraphEntry6.getFile() + ", no recalculation done!");
		} else {
			PropertyConfiguration config = CoverageCompressor.getDefaultConfiguration(BLOCK_COMPRESSION_METHOD.GZIP);
			config.setProperty(CoverageCompressor.OPT_BAM_FILE, BAM.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_OUT_FILE, bgraphEntry6.getFile().getAbsolutePath());
			config.setProperty(CoverageCompressor.OPT_BAM_FILTER, "FLAGS^^1024;FLAGS^^512");
			config.setProperty(CoverageCompressor.OPT_CREATE_STATS, "true");
			config.setProperty(CoverageCompressor.OPT_VERBOSE, "true");
			config.setProperty(CoverageCompressor.OPT_STATS_FILE, bgraphEntry6.getFile().getAbsolutePath() + ".stats");
			config.setProperty(CoverageCompressor.OPT_QUANT_METHOD, QUANT_METHOD.GRID.name());
			config.setProperty(CoverageCompressor.OPT_QUANT_PARAM, "1");

			w.reset();
			w.start();
			CoverageCompressor compressor = new CoverageCompressor(config);
			w.stop();
			bgraphEntry6.setCompressionTime(w.getTime());
		}
		bgraphEntry6.setByteSize(bgraphEntry6.getFile().length());
		entries.add(bgraphEntry6);

		
		// TDF, win 1
		StatsEntry tdfEntry1 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 1");
		headline("Compressing " + tdfEntry1.getName());
		tdfEntry1.setFile(new File(resultDir, BAM.getName() + ".1.tdf"));
		if (tdfEntry1.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry1.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 1 " + BAM + " " + tdfEntry1.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry1.setCompressionTime(w.getTime());
		}
		tdfEntry1.setByteSize(tdfEntry1.getFile().length());
		entries.add(tdfEntry1);

		// TDF, win 2
		StatsEntry tdfEntry2 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 2");
		headline("Compressing " + tdfEntry2.getName());
		tdfEntry2.setFile(new File(resultDir, BAM.getName() + ".2.tdf"));
		if (tdfEntry2.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry2.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 2 " + BAM + " " + tdfEntry2.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry2.setCompressionTime(w.getTime());
		}
		tdfEntry2.setByteSize(tdfEntry2.getFile().length());
		entries.add(tdfEntry2);

		// TDF, win 5
		StatsEntry tdfEntry5 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 5");
		headline("Compressing " + tdfEntry5.getName());
		tdfEntry5.setFile(new File(resultDir, BAM.getName() + ".5.tdf"));
		if (tdfEntry5.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry5.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 5 " + BAM + " " + tdfEntry5.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry5.setCompressionTime(w.getTime());
		}
		tdfEntry5.setByteSize(tdfEntry5.getFile().length());
		entries.add(tdfEntry5);

		// TDF, win 10
		StatsEntry tdfEntry10 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 10");
		headline("Compressing " + tdfEntry10.getName());
		tdfEntry10.setFile(new File(resultDir, BAM.getName() + ".10.tdf"));
		if (tdfEntry10.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry10.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 10 " + BAM + " " + tdfEntry10.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry10.setCompressionTime(w.getTime());
		}
		tdfEntry10.setByteSize(tdfEntry10.getFile().length());
		entries.add(tdfEntry10);

		// TDF, win 50
		StatsEntry tdfEntry50 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 50");
		headline("Compressing " + tdfEntry50.getName());
		tdfEntry50.setFile(new File(resultDir, BAM.getName() + ".50.tdf"));
		if (tdfEntry50.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry50.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 50 " + BAM + " " + tdfEntry50.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry50.setCompressionTime(w.getTime());
		}
		tdfEntry50.setByteSize(tdfEntry50.getFile().length());
		entries.add(tdfEntry50);

		// TDF, win 100
		StatsEntry tdfEntry100 = new StatsEntry(ENTRY_TYPE.TDF, "TDF, Windowsize 100");
		headline("Compressing " + tdfEntry100.getName());
		tdfEntry100.setFile(new File(resultDir, BAM.getName() + ".100.tdf"));
		if (tdfEntry100.getFile().exists() && dontredo) {
			log("WARNING: used existing file " + tdfEntry1.getFile() + ", no recalculation done!");
		} else {
			w.reset();
			w.start();
			Process proc = rt.exec("igvtools count -w 100 " + BAM + " " + tdfEntry100.getFile() + " " + IGV_GENOME);
			proc.waitFor();
			w.stop();
			checkProcess(proc);
			tdfEntry100.setCompressionTime(w.getTime());
		}
		tdfEntry100.setByteSize(tdfEntry100.getFile().length());
		entries.add(tdfEntry100);

		for (StatsEntry e : entries) {
			// GZIP the entries
			File gzipFile = new File(e.getFile().getAbsolutePath() + ".gz");
			if (gzipFile.exists() && dontredo) {
				log("WARNING: used existing file " + gzipFile + ", no recalculation done!");
			} else {
				FileUtils.gzipFile(e.getFile(), gzipFile, FileUtils.DEF_GZIP_BUF_SIZE);
			}
			e.setByteSizeGzip(gzipFile.length());
		}

	}

	private void extractCoverage() {

		// STEP 2 - extract raw coverage
		headline("Calculating raw coverage");
		if (rawcoverageFile.exists() && dontredo)
			log("WARNING: used existing rawcoveragefile " + rawcoverageFile + ", no recalculation done!");
		else {
			log("Calculating raw coverage");
			CoverageCompressor.compress(BAM, VCF, temporaryFile, false, CoverageCompressor.OPT_DUMP_RAW);
			// delete delme
			temporaryFile.delete();
		}
	}

	private void evaluate() throws Throwable {
		// STEP3 Evaluate
		headline("Evaluating...");
		for (StatsEntry e : entries) {

			// iterate all positionsin raw coverage file
			TabIterator it = new TabIterator(rawcoverageFile, "#");
			int c = 0;
			while (it.hasNext()) {
				c++;
				if (c % 100000 == 0)
					log("Evaluated positions: " + c);
				String[] t = it.next();
				String chr = t[0];
				int pos1 = Integer.parseInt(t[1]);
				GenomicPosition pos = new GenomicPosition(chr, pos1, COORD_TYPE.ONEBASED);
				float realCoverage = Float.parseFloat(t[2]);

				switch (e.getType()) {
				case BGRAPH:
					// query bgraph
					CoverageDecompressor decomp = (CoverageDecompressor) e.getDecompressor();
					if (decomp == null) {
						File wigFile = new File(e.getFile().getAbsolutePath() + ".wig");
						PropertyConfiguration conf = CoverageDecompressor.getDefaultConfiguration();
						conf.setProperty(CoverageDecompressor.OPT_COV_FILE, e.getFile().getAbsolutePath());
						conf.setProperty(CoverageDecompressor.OPT_VCF_FILE, VCF.getAbsolutePath());
						conf.setProperty(CoverageDecompressor.OPT_WIG_FILE, wigFile.getAbsolutePath());
						conf.setProperty(CoverageDecompressor.OPT_VERBOSE, "true");
						decomp = new CoverageDecompressor(conf);
						e.setDecompressor(decomp);
						e.setWigFile(wigFile);
					}
					float bgraphCoverage = 0;
					CoverageHit hit = decomp.query(pos);
					if (hit == null)
						bgraphCoverage = 0;
					else
						bgraphCoverage = hit.getInterpolatedCoverage();
					e.addData(bgraphCoverage, realCoverage);
					break;
				case TDF:
					// query TDF
					TDFFile tdfFile = (TDFFile) e.getDecompressor();
					if (tdfFile == null) {
						tdfFile = new TDFFile(e.getFile());
						e.setDecompressor(tdfFile);
					}
					Float tdfCoverage = tdfFile.query(pos);
					if (tdfCoverage == null)
						tdfCoverage = 0f;
					e.addData(tdfCoverage, realCoverage);
					// System.out.println(pos1+ "/" + realCoverage + "/" +
					// tdfCoverage
					// );
					if (e.getName().equals("TDF, Windowsize 1"))
						if (tdfCoverage != realCoverage)
							log("Diff at " + pos.toString1based() + "(" + tdfCoverage + "/" + realCoverage + ")");
					break;
				}

			}
			log("Finished " + e);
			// flush memory!
			e.setDecompressor(null);
			System.gc();
		}

		// ------------------------------------------------- create summary data
		// report --------------------

		report.println(printEntry(null));
		for (StatsEntry e : entries)
			report.println(printEntry(e));

		// ------------------------------------------------- create size data
		// report --------------------
		sizeReport.println("name\tcounts\tmeandiff\tsize-byte\tsize-gziped-byte");
		for (StatsEntry e : entries) {
			Long size = e.getByteSize();
			Long sizeGzip = e.getByteSizeGzip();
			Float mdtotal = (float) e.getMeanDiff();
			if (mdtotal == null)
				mdtotal = 0f;
			Float cctotal = (float) e.getCountPos();
			if (cctotal == null)
				cctotal = 0f;
			sizeReport.println(e.getName() + "\t" + cctotal + "\t" + mdtotal + "\t" + size + "\t" + sizeGzip);
		}

		// ------------------------------------------------- create diff data
		// report --------------------

		diffReport.println("bucket\tname\tmeandiff\tcount\tmeandiff-overestimated\tcount-overestimated\tmeandiff-underestimated\tcount-underestimated");

		for (StatsEntry e : entries) {
			for (int i = 0; i < StatsEntry.BUCKETS.length; i++) {

				String name = e.getName();

				Float md = e.getMeanDiffForBucket(i);
				if (md == null)
					md = 0f;
				Float cc = e.getCountsForBucket(i);
				if (cc == null)
					cc = 0f;

				Float mdover = e.getOverDiffForBucket(i);
				if (mdover == null)
					mdover = 0f;
				Float ccover = e.getOverCountsForBucket(i);
				if (ccover == null)
					ccover = 0f;

				Float mdunder = e.getUnderDiffForBucket(i);
				if (mdunder == null)
					mdunder = 0f;
				Float ccunder = e.getUnderCountsForBucket(i);
				if (ccunder == null)
					ccunder = 0f;

				diffReport.println(i + "\t" + name + "\t" + md + "\t" + cc + "\t" + mdover + "\t" + ccover + "\t" + mdunder + "\t" + ccunder);
			}
		}
	}

	private String printEntry(StatsEntry e) {
		if (e == null)
			return "Tool/Settings\tTime[ms]\tSize[byte]\tMean.Diff\tMax.Diff\tPath";
		return e.getName() + "\t" + e.getCompressionTime() + "\t" + e.getByteSize() + "\t" + e.getMeanDiff() + "\t" + e.getMaxDiff() + "\t" + e.getFile();
	}

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws Throwable {
//		 args = new String[] { "/scratch/testbams/small.bam",
//		 "/scratch/testbams/small.vcf",
//		 "/home/CIBIV/niko/igv/genomes/hg19.genome",
//		 "/scratch/testbams/evaluation1/" };
		//
//		 args = new String[] {
//		 "/scratch/testbams/13524_ATCACG_D223KACXX_3_20130430B_20130430_1.trimmed.bam-WRG.bam",
//		 "/scratch/testbams/13524_ATCACG_D223KACXX_3_20130430B_20130430_1.trimmed.samt.vcf",
//		 "/project/ngs/niko/borrelia/ref/B31_Schutzer_reference.genome",
//		 "/scratch/testbams/evaluation2/" };
		//
		// args = new String[] {
		// "/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam",
		// "/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped.bam-GATK-final.vcf",
		// "/home/CIBIV/niko/igv/genomes/hg19.genome",
		// "/scratch/testbams/evaluation3/" };

		if (args.length < 4)
			throw new RuntimeException("usage: " + EvaluateDataformats.class + " BAM VCF IGV_GENOME resultDir ");

		File BAM = new File(args[0]);
		File VCF = new File(args[1]);
		File IGV_GENOME = new File(args[2]);
		File resultDir = new File(args[3]);

		EvaluateDataformats evaluator = new EvaluateDataformats(BAM, VCF, IGV_GENOME, resultDir);
		evaluator.compress();
		evaluator.extractCoverage();
		evaluator.evaluate();
		evaluator.close();

	}

}
