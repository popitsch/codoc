package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;

import at.cibiv.ngs.tools.sam.iterator.CharEncoder.SYMBOLS;

/**
 * @see SymbolInputStream
 * @author niko.popitsch@univie.ac.at
 *
 */
public class SymbolOutputStream implements PushableStream<SYMBOLS> {

	SubByteOutputStream sout = null;

	public SymbolOutputStream(OutputStream out) {
		sout = new SubByteOutputStream(out, SYMBOLS.values().length);
	}

	@Override
	public void push(SYMBOLS data) throws IOException {
		if (data == null)
			sout.push(null);
		else
			sout.push(data.ordinal());
	}

	@Override
	public void close() throws IOException {
		sout.close();
	}

	@Override
	public Class<SYMBOLS> getDatatype() {
		return SYMBOLS.class;
	}

	@Override
	public Long getEncodedEntities() {
		return sout.getEncodedEntities();
	}

	@Override
	public OutputStream getOutputStream() {
		return sout.getOutputStream();
	}

	@Override
	public void flush() throws IOException {
		sout.flush();
	}
}
