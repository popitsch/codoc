package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A stream implementation that supports the push() operation.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 * @param <T>
 */
public interface PushableStream<T> {
	
	/**
	 * Push data to the stream.
	 * @param data
	 */
	public void push( T data ) throws IOException;
	
	/**
	 * Close the stream.
	 * @throws IOException
	 */
	public void close() throws IOException;
	
	/**
	 * @return the stream data type
	 */
	public Class<T> getDatatype();

	/**
	 * @return the number of encoded entities if available
	 */
	public Long getEncodedEntities();
	
	
	/**
	 * @return the underlying OutputStream if any
	 */
	public OutputStream getOutputStream();

	/**
	 * Flush the underlyin stream
	 */
	public void flush() throws IOException;
	
}
