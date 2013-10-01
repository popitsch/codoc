package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import at.cibiv.ngs.tools.sam.iterator.CharEncoder.SYMBOLS;
import at.cibiv.ngs.tools.util.MathUtil;

/**
 * An abstract stream of run lengths.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 * @param <T>
 */
public class RunLengthOutputStream<T, S> implements PushableStream<T> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * Max number of buffered RLEs.
	 */
	final static int MAX_BUF = 1000000;

	/**
	 * The stream the data is written to by the run-length implementation.
	 */
	protected OutputStream outData;

	/**
	 * The stream the lengths is written to by the run-length implementation.
	 */
	protected OutputStream outLen;

	/**
	 * Output buffer for faster I/O.
	 */
	protected List<RunLength<T>> buffer;

	/**
	 * The current run-length.
	 */
	protected RunLength<T> currentRLE = null;

	/**
	 * The number of emitted code words (=run-lengths).
	 */
	protected long writtenCodewordCounter = 0;

	/**
	 * Max number of buffered RLEs.
	 */
	static int MAXLEN = Integer.MAX_VALUE;

	/**
	 * The data type that is used for storing the rle length values.
	 */
	protected Class<S> lengthType = null;

	/**
	 * The datatype.
	 */
	protected Class<T> datatype = null;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;

	/**
	 * Counts the pushed raw values.
	 */
	long rawValueCounter = 0L;

	/**
	 * pushable stream
	 */
	private PushableStream<T> pushableStream;

	/**
	 * Constructor.
	 * 
	 * @param out
	 * @throws IOException
	 */
	public RunLengthOutputStream(PushableStream<T> outData, OutputStream outLen, Class<T> datatype, Class<S> lengthType, boolean debug) throws IOException {
		this(outData.getOutputStream(), outLen, datatype, lengthType, debug);
		this.pushableStream = outData;
	}

	/**
	 * Constructor.
	 * 
	 * @param out
	 * @throws IOException
	 */
	public RunLengthOutputStream(OutputStream outData, OutputStream outLen, Class<T> datatype, Class<S> lengthType, boolean debug) throws IOException {
		this.outData = outData;
		this.outLen = outLen;
		this.datatype = datatype;
		this.lengthType = lengthType;
		this.buffer = new ArrayList<RunLength<T>>(MAX_BUF);
		this.debug = debug;

		if (datatype == Byte.class) {
		} else if (datatype == Character.class) {
		} else if (datatype == Integer.class) {
		} else if (datatype == String.class) {
		} else if (datatype == SYMBOLS.class) {
		} else
			throw new IOException("Unsupported data type " + datatype.getName());

		if (lengthType == Byte.class) {
			MAXLEN = Byte.MAX_VALUE - Byte.MIN_VALUE;
		} else if (lengthType == Character.class) {
			MAXLEN = Character.MAX_VALUE;
		} else if (lengthType == Integer.class) {
			MAXLEN = Integer.MAX_VALUE;
		} else
			throw new IOException("Unsupported length type " + lengthType.getName());
	}

	@Override
	public OutputStream getOutputStream() {
		return outData;
	}

	/**
	 * Flush the output buffer. FIXME replace by advanced i/o!
	 * 
	 * @throws IOException
	 */
	protected void flushBuffer() throws IOException {
		if (buffer == null)
			return;
		Iterator<RunLength<T>> it = buffer.iterator();
		if (datatype == Byte.class) {
			while (it.hasNext()) {
				RunLength<T> raw = it.next();
				if (pushableStream != null)
					pushableStream.push(raw.getObject());
				else {
					@SuppressWarnings("unchecked")
					RunLength<Byte> rle = (RunLength<Byte>) raw;
					outData.write(rle.getObject());
				}
				if (debug)
					System.out.println("Wrote " + raw);
				if (lengthType == Byte.class) {
					outLen.write(MathUtil.char2byteBisection((char) raw.getLen()));
				} else if (lengthType == Character.class) {
					outLen.write(MathUtil.char2byte((char) raw.getLen()));
				} else if (lengthType == Integer.class) {
					outLen.write(MathUtil.int2byte(raw.getLen()));
				} else
					throw new IOException("Unsupported length type " + lengthType.getName());
			}
		} else if (datatype == Character.class) {
			while (it.hasNext()) {
				RunLength<T> raw = it.next();
				if (pushableStream != null)
					pushableStream.push(raw.getObject());
				else {
					@SuppressWarnings("unchecked")
					RunLength<Character> rle = (RunLength<Character>) raw;
					outData.write(MathUtil.char2byte(rle.getObject()));
				}
				if (debug)
					System.out.println("Wrote " + raw);
				if (lengthType == Byte.class) {
					outLen.write(MathUtil.char2byteBisection((char) raw.getLen()));
				} else if (lengthType == Character.class) {
					outLen.write(MathUtil.char2byte((char) raw.getLen()));
				} else if (lengthType == Integer.class) {
					outLen.write(MathUtil.int2byte(raw.getLen()));
				} else
					throw new IOException("Unsupported length type " + lengthType.getName());
			}
		} else if (datatype == Integer.class) {
			while (it.hasNext()) {
				RunLength<T> raw = it.next();
				if (pushableStream != null)
					pushableStream.push(raw.getObject());
				else {
					@SuppressWarnings("unchecked")
					RunLength<Integer> rle = (RunLength<Integer>) raw;
					outData.write(MathUtil.int2byte(rle.getObject()));
				}
				if (debug)
					System.out.println("Wrote " + raw);
				if (lengthType == Byte.class) {
					outLen.write(MathUtil.char2byteBisection((char) raw.getLen()));
				} else if (lengthType == Character.class) {
					outLen.write(MathUtil.char2byte((char) raw.getLen()));
				} else if (lengthType == Integer.class) {
					outLen.write(MathUtil.int2byte(raw.getLen()));
				} else
					throw new IOException("Unsupported length type " + lengthType.getName());
			}
		} else if (datatype == String.class) {
			while (it.hasNext()) {
				RunLength<T> raw = it.next();
				if (pushableStream != null)
					pushableStream.push(raw.getObject());
				else {
					@SuppressWarnings("unchecked")
					RunLength<String> rle = (RunLength<String>) raw;
					if (debug)
						System.out.println("Wrote " + rle);
					if (rle.getObject() == null)
						outData.write(MathUtil.int2byte(-1));
					else {
						byte[] s = rle.getObject().getBytes("UTF-8");
						outData.write(MathUtil.int2byte(s.length));
						if (s.length > 0)
							outData.write(s);
					}
				}
				if (lengthType == Byte.class) {
					outLen.write(MathUtil.char2byteBisection((char) raw.getLen()));
				} else if (lengthType == Character.class) {
					outLen.write(MathUtil.char2byte((char) raw.getLen()));
				} else if (lengthType == Integer.class) {
					outLen.write(MathUtil.int2byte(raw.getLen()));
				} else
					throw new IOException("Unsupported length type " + lengthType.getName());
			}
		} else if (datatype == SYMBOLS.class) {
			while (it.hasNext()) {
				RunLength<T> raw = it.next();
				pushableStream.push(raw.getObject());
				if (debug)
					System.out.println("Pushed " + raw);
				if (lengthType == Byte.class) {
					outLen.write(MathUtil.char2byteBisection((char) raw.getLen()));
				} else if (lengthType == Character.class) {
					outLen.write(MathUtil.char2byte((char) raw.getLen()));
				} else if (lengthType == Integer.class) {
					outLen.write(MathUtil.int2byte(raw.getLen()));
				} else
					throw new IOException("Unsupported length type " + lengthType.getName());
			}

		} else
			throw new IOException("Unsupported datatype " + datatype.getName());
		this.buffer.clear();
	}

	/**
	 * Emit the current run length (if not null).
	 * 
	 * @throws IOException
	 */
	public void emit() throws IOException {
		if (currentRLE != null) {
			this.buffer.add(currentRLE);
			this.writtenCodewordCounter++;
			if (this.writtenCodewordCounter % MAX_BUF == 0)
				flushBuffer();
		}
		currentRLE = null;
	}

	/**
	 * Push data to this rle.
	 * 
	 * @param data
	 * @return
	 * @throws IOException
	 */
	public void push(T data) throws IOException {
		rawValueCounter++;
		if (currentRLE == null) {
			currentRLE = new RunLength<T>(data);
			entityCounter++;
		} else {
			if (currentRLE.getLen() >= MAXLEN) {
				emit();
				currentRLE = new RunLength<T>(datatype.cast(data));
				entityCounter++;
			} else {
				T cmp = currentRLE.getObject();
				boolean compare = false;
				if (cmp == null)
					compare = (data == null);
				else
					compare = cmp.equals(data);

				if (compare)
					currentRLE.inc();
				else {
					emit();
					currentRLE = new RunLength<T>(datatype.cast(data));
					entityCounter++;
				}
			}
		}
	}

	/**
	 * Close this stream.
	 * 
	 * @throws IOException
	 */
	@Override
	public void close() throws IOException {
		if (buffer != null)
			try {
				emit();
				flushBuffer();
			} finally {
				if (pushableStream != null)
					pushableStream.close();
				else
					outData.close();
				outLen.close();
				buffer = null;
			}
	}

	/**
	 * Flushed this stream.
	 * 
	 * @throws IOException
	 */
	@Override
	public void flush() throws IOException {
		flushBuffer();
	}

	/**
	 * @return The number of emitted code words (=run-lengths).
	 */
	public long getWrittenCodewordCounter() {
		return writtenCodewordCounter;
	}

	public Class<S> getLengthType() {
		return lengthType;
	}

	@Override
	public Class<T> getDatatype() {
		return datatype;
	}

	@Override
	public Long getEncodedEntities() {
		return entityCounter;
	}

	public Long getEncodedRawValues() {
		return rawValueCounter;
	}

	@Override
	public String toString() {
		return "[RunLengthOutputStream<" + datatype.getName() + "><" + lengthType.getName() + ">]";
	}

}
