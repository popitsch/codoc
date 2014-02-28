package at.cibiv.codoc.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.FileUtils;
import at.cibiv.codoc.utils.PropertyConfiguration;


/**
 * A file header that manages multiple data blocks.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class FileHeader extends FileDataOutputBlock<Object> {

	/**
	 * The data stream configuration
	 */
	PropertyConfiguration configuration = null;

	/**
	 * List of data blocks.
	 */
	List<FileDataOutputBlock<?>> blocks = new ArrayList<FileDataOutputBlock<?>>();

	/**
	 * 
	 * @param f
	 * @param conf
	 * @throws Throwable
	 * @throws FileNotFoundException
	 */
	public FileHeader(File f) throws FileNotFoundException {
		super("header", f);
	}

	public PropertyConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(PropertyConfiguration configuration) {
		this.configuration = configuration;
	}

	public List<FileDataOutputBlock<?>> getBlocks() {
		return blocks;
	}

	public void addBlocks(Collection<FileDataOutputBlock<?>> blocks) {
		this.blocks.addAll(blocks);
	}

	public void addBlock(FileDataOutputBlock<?> block) {
		this.blocks.add(block);
	}

	/**
	 * Store to file.
	 * 
	 * 
	 * @param out
	 * @throws Throwable
	 */
	public void toFile() throws Throwable {
		// write two times to get the proper byte length
		toFile(getByteLengthF1());
		toFile(getByteLengthF1());
	}

	/**
	 * Store to file.
	 * 
	 * @param out
	 * @throws Throwable
	 */
	public void toFile(long length) throws Throwable {
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(getF1());
			PropertyConfiguration headerconf = PropertyConfiguration
					.fromString("header-type=mixed\nheader-debug=false");
			@SuppressWarnings("unchecked")
			PushableStream<Object> sout = (PushableStream<Object>) StreamFactory.createOutputStream(fout, headerconf,
					"header");

			// write length
			sout.push(length);

			// write config
			sout.push(getConfiguration().toString());

//			// write consensus
//			sout.push(getQualityConsensus().length);
//			for (Byte b : getQualityConsensus())
//				sout.push(b);

			// write blocks
			sout.push(blocks.size());
			for (FileDataOutputBlock<?> p : blocks) {
				if (p instanceof TripleFileDataOutputBlock<?>) {
					sout.push(p.getId());
					sout.push("triple");
					sout.push(p.getByteLengthF1());
					sout.push(((TripleFileDataOutputBlock<?>) p).getByteLengthF2());
					sout.push(((TripleFileDataOutputBlock<?>) p).getByteLengthF3());
				} else 
				if (p instanceof HeterogenousFileDataOutputBlock<?>) {
					sout.push(p.getId());
					sout.push("heterogenous");
					sout.push(p.getByteLengthF1());
					sout.push(((HeterogenousFileDataOutputBlock<?>) p).getByteLengthF2());
				} else {
					sout.push(p.getId());
					sout.push("normal");
					sout.push(p.getByteLengthF1());
				}
			}


			// close stream
			sout.close();

		} finally {
			if (fout != null)
				fout.close();
			close();
			setByteLengthF1(getF1().length());

		}
	}

	/**
	 * Read from file.
	 * 
	 * @param in
	 * @return
	 * @throws CodocException 
	 * @throws Throwable
	 */
	public static FileHeader fromFile(File in, File workDir) throws IOException, CodocException {
		FileInputStream fin = null;
		try {
			fin = new FileInputStream(in);
			PropertyConfiguration headerconf = PropertyConfiguration
					.fromString("header-type=mixed\nheader-debug=false");
			MixedInputStream sin = (MixedInputStream) StreamFactory.createInputStream(fin, headerconf, "header");

			// create header
			FileHeader ret = new FileHeader(null);

			// read length
			ret.setByteLengthF1(sin.popLong());

			// read config
			ret.setConfiguration(PropertyConfiguration.fromString(sin.popString()));

//			// read consensus
//			int l = sin.popInteger();
//			Byte[] cons = new Byte[l];
//			for (int i = 0; i < l; i++)
//				cons[i] = sin.popByte();
//			ret.setQualityConsensus(cons);

			// read positions
			int l = sin.popInteger();
			for (int i = 0; i < l; i++) {
				String id = sin.popString();
				String type = sin.popString();
				if (type.equals("triple")) {
					Long f1l = sin.popLong();
					Long f2l = sin.popLong();
					Long f3l = sin.popLong();
					TripleFileDataOutputBlock<?> block = new TripleFileDataOutputBlock<>(id, null, null, null);
					block.setByteLengthF1(f1l);
					block.setByteLengthF2(f2l);
					block.setByteLengthF3(f3l);
					ret.addBlock(block);
				} else
				if (type.equals("heterogenous")) {
					Long f1l = sin.popLong();
					Long f2l = sin.popLong();
					HeterogenousFileDataOutputBlock<?> block = new HeterogenousFileDataOutputBlock<>(id, null, null);
					block.setByteLengthF1(f1l);
					block.setByteLengthF2(f2l);
					ret.addBlock(block);
				} else {
					Long f1l = sin.popLong();
					FileDataOutputBlock<?> block = new FileDataOutputBlock<>(id, null);
					block.setByteLengthF1(f1l);
					ret.addBlock(block);
				}
			}


			// close stream
			sin.close();

			List<Long> splitPositions = new ArrayList<>();
			long pos = ret.getByteLengthF1();
			splitPositions.add(pos);
			for (FileDataOutputBlock<?> block : ret.getBlocks()) {
				if (block instanceof TripleFileDataOutputBlock<?>) {
					pos += block.getByteLengthF1();
					splitPositions.add(pos);
					pos += ((TripleFileDataOutputBlock<?>) block).getByteLengthF2();
					splitPositions.add(pos);
					pos += ((TripleFileDataOutputBlock<?>) block).getByteLengthF3();
					splitPositions.add(pos);
				}  else
				if (block instanceof HeterogenousFileDataOutputBlock<?>) {
					pos += block.getByteLengthF1();
					splitPositions.add(pos);
					pos += ((HeterogenousFileDataOutputBlock<?>) block).getByteLengthF2();
					splitPositions.add(pos);
				} else {
					pos += block.getByteLengthF1();
					splitPositions.add(pos);
				}
			}
			// delete the last split pos as it is not relevant
			splitPositions.remove(splitPositions.size() - 1);

			
			// split the files
			List<File> files = FileUtils.splitFile(in, workDir, false, splitPositions);
//			System.out.println("created files: " + Arrays.toString(files.toArray())+ " with splitpos " + Arrays.toString(splitPositions.toArray()));
			Iterator<File> fit = files.iterator();
			

			// skip header
			ret.setF1(fit.next());
			for (FileDataOutputBlock<?> block : ret.getBlocks()) {
				if (block instanceof TripleFileDataOutputBlock<?>) {
					block.setF1(fit.next());
					((TripleFileDataOutputBlock<?>) block).setF2(fit.next());
					((TripleFileDataOutputBlock<?>) block).setF3(fit.next());
				} else 
				if (block instanceof HeterogenousFileDataOutputBlock<?>) {
					block.setF1(fit.next());
					((HeterogenousFileDataOutputBlock<?>) block).setF2(fit.next());
				} else {
					block.setF1(fit.next());
				}
			}



			return ret;

		} finally {
			if (fin != null)
				fin.close();
		}
	}
}
