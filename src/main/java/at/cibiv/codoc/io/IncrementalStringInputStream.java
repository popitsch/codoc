package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An (endless!) input stream that delivers strings.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class IncrementalStringInputStream implements PopableStream<String> {

	private String prefix;
	long counter = 0L;

	public IncrementalStringInputStream(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public String pop() throws IOException {
		counter++;
		return prefix + counter;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public int available() throws IOException {
		return 1;
	}

	@Override
	public Class<String> getDatatype() {
		return String.class;
	}

	@Override
	public Long getDecodedEntities() {
		return counter;
	}

	@Override
	public InputStream getInputStream() {
		return null;
	}
}
