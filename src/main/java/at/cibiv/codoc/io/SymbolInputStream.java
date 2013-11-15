package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;

import at.cibiv.ngs.tools.sam.iterator.CharEncoder.SYMBOLS;

/**
 * A stream that supports the pop() operation returning SYMBOLs
 * @author niko.popitsch@univie.ac.at
 *
 */
public class SymbolInputStream implements PopableStream<SYMBOLS> {

	SubByteInputStream sin = null;

	public SymbolInputStream(InputStream in) throws IOException {
		this.sin = new SubByteInputStream(in, SYMBOLS.values().length);
	}

	@Override
	public SYMBOLS pop() throws IOException {
		Integer x = sin.pop();
		if (x == null)
			return null;
		return SYMBOLS.values()[x];
	}

	@Override
	public void close() throws IOException {
		sin.close();
	}

	@Override
	public int available() throws IOException {
		return sin.available();
	}

	@Override
	public Class<SYMBOLS> getDatatype() {
		return SYMBOLS.class;
	}

	@Override
	public Long getDecodedEntities() {
		return sin.getDecodedEntities();
	}

	@Override
	public InputStream getInputStream() {
		return sin.getInputStream();
	}
	
}
