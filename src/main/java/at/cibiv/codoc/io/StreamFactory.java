package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.PropertyConfiguration;
import at.cibiv.ngs.tools.sam.iterator.CharEncoder.SYMBOLS;

/**
 * Create streams.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class StreamFactory {

	/**
	 * List of supported stream types.
	 */
	public static enum STREAM_TYPE {
		RLE, PREFIX, STANDARD, NULL, MIXED, GOLOMB, SYMBOLS, SUBBYTE, ARITHMETIC
	}

	/**
	 * List of supported stream data types.
	 */
	public static enum STREAM_DATA_TYPE {
		BYTE, INTEGER, CHARACTER, STRING, SYMBOLS
	}

	/**
	 * List of supported stream data types.
	 */
	public static enum STREAM_LENGTH_TYPE {
		BYTE, INTEGER, CHARACTER
	}

	private static STREAM_TYPE stFromString(String s) throws CodocException {
		if (s == null)
			throw new CodocException("Invalid stream type " + s + "  [" + Arrays.toString(STREAM_TYPE.values())
					+ "]");
		for (STREAM_TYPE t : STREAM_TYPE.values()) {
			if (t.name().equalsIgnoreCase(s))
				return t;
		}
		throw new CodocException("Invalid stream type " + s + "  [" + Arrays.toString(STREAM_TYPE.values())
				+ "]");
	}

	private static STREAM_DATA_TYPE sdtFromString(String s) throws CodocException {
		if (s == null)
			throw new CodocException("Invalid stream datatype " + s + "  ["
					+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]");
		for (STREAM_DATA_TYPE t : STREAM_DATA_TYPE.values()) {
			if (t.name().equalsIgnoreCase(s))
				return t;
		}
		throw new CodocException("Invalid stream datatype " + s + "  ["
				+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]");
	}

	private static STREAM_LENGTH_TYPE sltFromString(String s) throws CodocException {
		if (s == null)
			throw new CodocException("Invalid stream lengthtype " + s + "  ["
					+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]");
		for (STREAM_LENGTH_TYPE t : STREAM_LENGTH_TYPE.values()) {
			if (t.name().equalsIgnoreCase(s))
				return t;
		}
		throw new CodocException("Invalid stream lengthtype " + s + "  ["
				+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]");
	}

	/**
	 * createOutputStream(System.out,
	 * PropertyConfiguration.fromString("test-type=golomb\ntest-k=12"), "test")
	 * 
	 * @param conf
	 * @param prefix
	 * @return
	 * @throws Throwable
	 */
	public static PushableStream<? extends Object> createTripleOutputStream(OutputStream o1, OutputStream o2,
			OutputStream o3, PropertyConfiguration conf, String prefix) throws Throwable {

		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");

		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));

		// get stream type
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));

		switch (type) {
		case PREFIX:
			STREAM_DATA_TYPE datatype3 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype3 = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			Boolean storeDiff = Boolean.parseBoolean(conf.getProperty(prefix + "-storediff", "false"));

			switch (datatype3) {
			case STRING:
				switch (lengthtype3) {
				case BYTE:
					return new PrefixOutputStream<Byte>(o1, o2, o3, Byte.class, storeDiff, debug);
				case CHARACTER:
					return new PrefixOutputStream<Character>(o1, o2, o3, Character.class, storeDiff, debug);
				case INTEGER:
					return new PrefixOutputStream<Integer>(o1, o2, o3, Integer.class, storeDiff, debug);
				}
			default:
				throw new CodocException("Prefix encoding supported only for strings");
			}
		default:
			throw new CodocException("Stream type not supported!");

		}
	}

	/**
	 * createOutputStream(System.out,
	 * PropertyConfiguration.fromString("test-type=golomb\ntest-k=12"), "test")
	 * 
	 * @param conf
	 * @param prefix
	 * @return
	 * @throws Throwable
	 */
	public static PushableStream<? extends Object> createOutputStream(OutputStream out, PropertyConfiguration conf,
			String prefix) throws Throwable {

		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");

		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));

		// get stream type
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));

		switch (type) {
		case SUBBYTE:
			Integer maxSymbols = null;
			try {
				String tmp = conf.getProperty(prefix + "-maxSymbols", new CodocException(
						"No maxSymbols parameter configured for " + prefix));
				maxSymbols = Integer.parseInt(tmp);
			} catch (NumberFormatException e) {
				throw new CodocException("Invalid maxSymbols " + e);
			}
			return new SubByteOutputStream(out, maxSymbols);
		case SYMBOLS:
			return new SymbolOutputStream(out);
		case GOLOMB:
			STREAM_DATA_TYPE datatype4 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			Integer k = null;
			try {
				String kstr = conf.getProperty(prefix + "-k", new CodocException(
						"No golomb k parameter configured for " + prefix));
				k = Integer.parseInt(kstr);
			} catch (NumberFormatException e) {
				throw new CodocException("Invalid k " + e);
			}

			switch (datatype4) {
			case BYTE:
				return new GolombOutputStream<Byte>(out, k, Byte.class);
			case CHARACTER:
				return new GolombOutputStream<Character>(out, k, Character.class);
			case INTEGER:
				return new GolombOutputStream<Integer>(out, k, Integer.class);
			default:
				throw new CodocException("Golomb encoding supported only for positive integers");
			}
		case RLE:
			STREAM_DATA_TYPE datatype = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			switch (datatype) {
			case BYTE:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Byte, Byte>(out, out, Byte.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<Byte, Character>(out, out, Byte.class, Character.class, debug);
				case INTEGER:
					return new RunLengthOutputStream<Byte, Integer>(out, out, Byte.class, Integer.class, debug);
				}
			case CHARACTER:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Character, Byte>(out, out, Character.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<Character, Character>(out, out, Character.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthOutputStream<Character, Integer>(out, out, Character.class, Integer.class,
							debug);
				}
			case INTEGER:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Integer, Byte>(out, out, Integer.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<Integer, Character>(out, out, Integer.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthOutputStream<Integer, Integer>(out, out, Integer.class, Integer.class, debug);
				}
			case STRING:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<String, Byte>(out, out, String.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<String, Character>(out, out, String.class, Character.class, debug);
				case INTEGER:
					return new RunLengthOutputStream<String, Integer>(out, out, String.class, Integer.class, debug);
				}
			case SYMBOLS:
				throw new CodocException("Heterogenous stream required");
			}
		case STANDARD:
			STREAM_DATA_TYPE datatype2 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			switch (datatype2) {
			case BYTE:
				return new StandardOutputStream<Byte>(out, Byte.class, debug);
			case CHARACTER:
				return new StandardOutputStream<Character>(out, Character.class, debug);
			case INTEGER:
				return new StandardOutputStream<Integer>(out, Integer.class, debug);
			case STRING:
				return new StandardOutputStream<String>(out, String.class, debug);
			}
		case NULL:
			return new StandardOutputStream<Object>(null, Object.class, debug);
		case MIXED:
			return new MixedOutputStream(out, debug);
		default:
			throw new CodocException("Stream type not supported!");
		}

	}

	/**
	 * Create a heterogenous stream.
	 * 
	 * @param outData
	 * @param outLen
	 * @param conf
	 * @param prefix
	 * @return
	 * @throws Throwable
	 */
	public static PushableStream<? extends Object> createHeterogenousOutputStream(OutputStream outData,
			OutputStream outLen, PropertyConfiguration conf, String prefix) throws Throwable {
		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");

		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));

		// get stream type
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));

		switch (type) {
		case RLE:
			STREAM_DATA_TYPE datatype = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			switch (datatype) {
			case BYTE:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Byte, Byte>(outData, outLen, Byte.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<Byte, Character>(outData, outLen, Byte.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthOutputStream<Byte, Integer>(outData, outLen, Byte.class, Integer.class, debug);
				}
			case CHARACTER:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Character, Byte>(outData, outLen, Character.class, Byte.class,
							debug);
				case CHARACTER:
					return new RunLengthOutputStream<Character, Character>(outData, outLen, Character.class,
							Character.class, debug);
				case INTEGER:
					return new RunLengthOutputStream<Character, Integer>(outData, outLen, Character.class,
							Integer.class, debug);
				}
			case INTEGER:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<Integer, Byte>(outData, outLen, Integer.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<Integer, Character>(outData, outLen, Integer.class,
							Character.class, debug);
				case INTEGER:
					return new RunLengthOutputStream<Integer, Integer>(outData, outLen, Integer.class, Integer.class,
							debug);
				}
			case STRING:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<String, Byte>(outData, outLen, String.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<String, Character>(outData, outLen, String.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthOutputStream<String, Integer>(outData, outLen, String.class, Integer.class,
							debug);
				}
			case SYMBOLS:
				switch (lengthtype) {
				case BYTE:
					return new RunLengthOutputStream<SYMBOLS, Byte>(new SymbolOutputStream(outData), outLen,
							SYMBOLS.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthOutputStream<SYMBOLS, Character>(new SymbolOutputStream(outData), outLen,
							SYMBOLS.class, Character.class, debug);
				case INTEGER:
					return new RunLengthOutputStream<SYMBOLS, Integer>(new SymbolOutputStream(outData), outLen,
							SYMBOLS.class, Integer.class, debug);
				}
			}
		case NULL:
			return new StandardOutputStream<Object>(null, Object.class, debug);
		default:
			throw new CodocException("Stream type cannot be heterogenous! " + type);

		}
	}

	public static PopableStream<?> createTripleInputStream(InputStream in1, InputStream in2, InputStream in3,
			PropertyConfiguration conf, String prefix) throws Throwable {
		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));
		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));

		switch (type) {
		case PREFIX:
			STREAM_DATA_TYPE datatype3 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype3 = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			Boolean storeDiff = Boolean.parseBoolean(conf.getProperty(prefix + "-storediff", "false"));

			switch (datatype3) {
			case STRING:
				switch (lengthtype3) {
				case BYTE:
					return new PrefixInputStream<Byte>(in1, in2, in3, Byte.class, storeDiff, debug);
				case CHARACTER:
					return new PrefixInputStream<Character>(in1, in2, in3, Character.class, storeDiff, debug);
				case INTEGER:
					return new PrefixInputStream<Integer>(in1, in2, in3, Integer.class, storeDiff, debug);
				}
			default:
				throw new CodocException("Prefix encoding supported only for strings");
			}

		}
		throw new CodocException("Stream type not supported!");
	}

	/**
	 * 
	 * @param conf
	 * @param prefix
	 * @return
	 * @throws Throwable
	 */
	public static PopableStream<?> createInputStream(InputStream in, PropertyConfiguration conf, String prefix)
			throws Throwable {
		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));

		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));
		switch (type) {
		case SUBBYTE:
			Integer maxSymbols = null;
			try {
				String tmp = conf.getProperty(prefix + "-maxSymbols", new CodocException(
						"No maxSymbols parameter configured for " + prefix));
				maxSymbols = Integer.parseInt(tmp);
			} catch (NumberFormatException e) {
				throw new CodocException("Invalid maxSymbols "+ e);
			}
			return new SubByteInputStream(in, maxSymbols);
		case SYMBOLS:
			return new SymbolInputStream(in);
		case GOLOMB:
			STREAM_DATA_TYPE datatype4 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			Integer k = null;
			try {
				String kstr = conf.getProperty(prefix + "-k", new CodocException(
						"No golomb k parameter configured for " + prefix));
				k = Integer.parseInt(kstr);
			} catch (NumberFormatException e) {
				throw new CodocException("Invalid k "+ e);
			}

			switch (datatype4) {
			case BYTE:
				return new GolombInputStream<Byte>(in, k, Byte.class);
			case CHARACTER:
				return new GolombInputStream<Character>(in, k, Character.class);
			case INTEGER:
				return new GolombInputStream<Integer>(in, k, Integer.class);
			default:
				throw new CodocException("Golomb encoding supported only for positive integers");
			}
		case RLE:
			STREAM_DATA_TYPE datatype = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			switch (datatype) {
			case BYTE:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Byte, Byte>(in, in, Byte.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Byte, Character>(in, in, Byte.class, Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<Byte, Integer>(in, in, Byte.class, Integer.class, debug);
				}
			case CHARACTER:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Character, Byte>(in, in, Character.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Character, Character>(in, in, Character.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthInputStream<Character, Integer>(in, in, Character.class, Integer.class, debug);
				}
			case INTEGER:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Integer, Byte>(in, in, Integer.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Integer, Character>(in, in, Integer.class, Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<Integer, Integer>(in, in, Integer.class, Integer.class, debug);
				}
			case STRING:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<String, Byte>(in, in, String.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<String, Character>(in, in, String.class, Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<String, Integer>(in, in, String.class, Integer.class, debug);
				}
			case SYMBOLS:
				throw new CodocException("Heterogenous stream required");
			}
		case STANDARD:
			STREAM_DATA_TYPE datatype2 = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ STREAM_DATA_TYPE.values() + "]")));
			switch (datatype2) {
			case BYTE:
				return new StandardInputStream<Byte>(in, Byte.class, debug);
			case CHARACTER:
				return new StandardInputStream<Character>(in, Character.class, debug);
			case INTEGER:
				return new StandardInputStream<Integer>(in, Integer.class, debug);
			case STRING:
				return new StandardInputStream<String>(in, String.class, debug);
			}
		case NULL:
			return new StandardInputStream<Object>(null, Object.class, debug);
		case MIXED:
			return new MixedInputStream(in, debug);
		}
		throw new CodocException("Stream type not supported!");
	}

	/**
	 * 
	 * @param conf
	 * @param prefix
	 * @return
	 * @throws Throwable
	 */
	public static PopableStream<?> createHeterogenousInputStream(InputStream inData, InputStream inLen,
			PropertyConfiguration conf, String prefix) throws Throwable {
		if ((conf == null) || (prefix == null))
			throw new CodocException("No proper stream configuration!");
		STREAM_TYPE type = stFromString(conf.getProperty(prefix + "-type", new CodocException(
				"No stream type configured for " + prefix + "  [" + Arrays.toString(STREAM_TYPE.values()) + "]")));

		// debug property
		boolean debug = Boolean.parseBoolean(conf.getProperty(prefix + "-debug", "false"));

		switch (type) {
		case RLE:
			STREAM_DATA_TYPE datatype = sdtFromString(conf.getProperty(
					prefix + "-datatype",
					new CodocException("No stream datatype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_DATA_TYPE.values()) + "]")));
			STREAM_LENGTH_TYPE lengthtype = sltFromString(conf.getProperty(
					prefix + "-lengthtype",
					new CodocException("No stream lengthtype configured for " + prefix + "  ["
							+ Arrays.toString(STREAM_LENGTH_TYPE.values()) + "]")));
			switch (datatype) {
			case BYTE:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Byte, Byte>(inData, inLen, Byte.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Byte, Character>(inData, inLen, Byte.class, Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<Byte, Integer>(inData, inLen, Byte.class, Integer.class, debug);
				}
			case CHARACTER:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Character, Byte>(inData, inLen, Character.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Character, Character>(inData, inLen, Character.class,
							Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<Character, Integer>(inData, inLen, Character.class, Integer.class,
							debug);
				}
			case INTEGER:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<Integer, Byte>(inData, inLen, Integer.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<Integer, Character>(inData, inLen, Integer.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthInputStream<Integer, Integer>(inData, inLen, Integer.class, Integer.class,
							debug);
				}
			case STRING:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<String, Byte>(inData, inLen, String.class, Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<String, Character>(inData, inLen, String.class, Character.class,
							debug);
				case INTEGER:
					return new RunLengthInputStream<String, Integer>(inData, inLen, String.class, Integer.class, debug);
				}
			case SYMBOLS:

				switch (lengthtype) {
				case BYTE:
					return new RunLengthInputStream<SYMBOLS, Byte>(new SymbolInputStream(inData), inLen, SYMBOLS.class,
							Byte.class, debug);
				case CHARACTER:
					return new RunLengthInputStream<SYMBOLS, Character>(new SymbolInputStream(inData), inLen,
							SYMBOLS.class, Character.class, debug);
				case INTEGER:
					return new RunLengthInputStream<SYMBOLS, Integer>(new SymbolInputStream(inData), inLen,
							SYMBOLS.class, Integer.class, debug);
				}
			}
		case NULL:
			return new StandardInputStream<Object>(null, Object.class, debug);
		default:
			throw new CodocException("Stream type cannot be heterogenous: " + type);
		}
	}

	public static void main(String[] args) throws IOException, Throwable {
		System.out.println(StreamFactory.createOutputStream(System.out,
				PropertyConfiguration.fromString("test-type=golomb\ntest-k=12"), "test"));
		System.out.println(StreamFactory.createOutputStream(System.out,
				PropertyConfiguration.fromString("test-type=rle\ntest-datatype=byte\ntest-lengthtype=byte"), "test"));
		System.out.println(StreamFactory.createOutputStream(System.out,
				PropertyConfiguration.fromString("test-type=standard\ntest-datatype=byte"), "test"));
		System.out.println(StreamFactory.createOutputStream(System.out,
				PropertyConfiguration.fromString("test-type=null\ntest-datatype=byte"), "test"));

		System.out.println(StreamFactory.createInputStream(null,
				PropertyConfiguration.fromString("test-type=golomb\ntest-k=12"), "test"));
		System.out.println(StreamFactory.createInputStream(null,
				PropertyConfiguration.fromString("test-type=rle\ntest-datatype=byte\ntest-lengthtype=byte"), "test"));
		System.out.println(StreamFactory.createInputStream(null,
				PropertyConfiguration.fromString("test-type=standard\ntest-datatype=byte"), "test"));
		System.out.println(StreamFactory.createInputStream(null,
				PropertyConfiguration.fromString("test-type=null\ntest-datatype=byte"), "test"));

	}

}
