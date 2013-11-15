package at.cibiv.codoc.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import at.cibiv.ngs.tools.util.MathUtil;
import at.cibiv.ngs.tools.util.StringUtils;

/**
 * Encodes strings using their prefixes. Max string len is 255.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class PrefixOutputStream<S> implements PushableStream<String> {

	/**
	 * Debug flag.
	 */
	private boolean debug = false;

	/**
	 * The current prefix
	 */
	protected String prefix = null;

	/**
	 * The last encoded string
	 */
	protected String lastString = null;

	/**
	 * Candidate prefix.
	 */
	private String newPrefixCandidate = "";

	/**
	 * Candidate prefix count
	 */
	private int newPrefixCandidateCount = 0;

	/**
	 * The stream the data is written to by the prefix stream implementation.
	 */
	protected OutputStream outData;

	/**
	 * The stream the lengths is written to by the prefix stream implementation.
	 */
	protected OutputStream outLen;

	/**
	 * The stream for writing the prefix map.
	 */
	protected StandardOutputStream<String> outMap;

	/**
	 * A map for prefixes.
	 */
	protected Map<String, Integer> prefixMap = new HashMap<String, Integer>();

	/**
	 * The data type that is used for storing the string length values.
	 */
	protected Class<S> lengthType = null;

	/**
	 * For storing length differences.
	 */
	protected int lastLen = 0;

	/**
	 * When true, the stream will store string=length differences instead of
	 * rela lengths.
	 */
	private boolean storeLengthDiff = true;

	/**
	 * Counts the pushed entities.
	 */
	long entityCounter = 0L;
	
	/**
	 * Flag whether stream is already closed.
	 */
	private boolean closed = false;

	/**
	 * Constructor.
	 * 
	 * @param out
	 */
	public PrefixOutputStream(OutputStream outData, OutputStream outLen, OutputStream outMap, Class<S> lengthType,
			boolean storeLengthDiff, boolean debug) {
		this.outData = outData;
		this.outLen = outLen;
		this.outMap = new StandardOutputStream<String>(outMap, String.class, debug);
		this.lengthType = lengthType;
		this.storeLengthDiff = storeLengthDiff;
		this.debug = debug;
	}

	@Override
	public OutputStream getOutputStream() {
		return outData;
	}

	/**
	 * emit a string
	 * 
	 * @param l
	 * @param s
	 * @throws IOException
	 */
	private void emit(String s) throws IOException {
		if (debug)
			System.out.println("Write " + s);

		byte[] b = s.getBytes("UTF-8");
		int len = b.length;
		if (storeLengthDiff) {
			len -= lastLen;
			lastLen = b.length;
			outLen.write(MathUtil.int2byte(len));
		} else {
			// byte- encoded string length
			if (lengthType == Byte.class) {
				outLen.write(MathUtil.char2byteBisection((char) len));
			} else if (lengthType == Character.class) {
				outLen.write(MathUtil.char2byte((char) len));
			} else if (lengthType == Integer.class) {
				outLen.write(MathUtil.int2byte(len));
			} else
				throw new IOException("Unsupported length type " + lengthType.getName());
		}

		// byte encode string
		outData.write(b);
	}

	@Override
	public Class<String> getDatatype() {
		return String.class;
	}

	@Override
	public void push(String data) throws IOException {
		entityCounter++;

		if (lastString == null) {
			// only first time.
			lastString = data;
			return;
		}

		if (prefix == null) {
			// calculate prefix
			prefix = StringUtils.findCommonPrefix(data, lastString);
			Integer pid = prefixMap.get(prefix);
			if (pid == null) {
				pid = prefixMap.size();
				prefixMap.put(prefix, pid);
			}
			emit("@" + pid);
			// encode suffix of old read.
			String suffix = lastString.substring(prefix.length());
			emit(suffix);
			suffix = data.substring(prefix.length());
			emit(suffix);
			lastString = data;
			return;
		}

		if (data.startsWith(prefix)) {

			String longerPrefix = StringUtils.findCommonPrefix(data, lastString);
			if (longerPrefix.length() > prefix.length()) {
				if (newPrefixCandidate.equals(longerPrefix))
					newPrefixCandidateCount++;
				else {
					newPrefixCandidate = longerPrefix;
					newPrefixCandidateCount = 0;
				}
				if (newPrefixCandidateCount > 3) {
					// longer prefix is stable - use it.
					// emit the new prefix
					// emit("&" + longerPrefix.substring(prefix.length()));
					prefix = longerPrefix;
					Integer pid = prefixMap.get(prefix);
					if (pid == null) {
						pid = prefixMap.size();
						prefixMap.put(prefix, pid);
					}
					emit("@" + pid);
					// encode suffix of old read.
					String suffix = data.substring(prefix.length());
					emit(suffix);
					lastString = data;
					return;
				}
			}
			// ok, we have to encode the suffix only.
			String suffix = data.substring(prefix.length());
			// ecode the suffix.
			emit(suffix);
			lastString = data;
			return;
		} else {

			String shorterPrefix = StringUtils.findCommonPrefix(data, lastString);
			if (prefix.startsWith(shorterPrefix)) {
				prefix = shorterPrefix;
				Integer pid = prefixMap.get(prefix);
				if (pid == null) {
					pid = prefixMap.size();
					prefixMap.put(prefix, pid);
				}
				emit("@" + pid);
				// encode suffix of old read.
				String suffix = data.substring(prefix.length());
				emit(suffix);
				lastString = data;
				return;
			}
			// reset the new prefix
			prefix = null;
			lastString = data;
			return;
		}
	}

	/**
	 * Close the stream.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		if ( closed )
			return;
		try {
			if ((lastString != null) && (prefix == null)) {
				// calculate prefix
				prefix = StringUtils.findCommonPrefix(lastString, lastString);
				Integer pid = prefixMap.get(prefix);
				if (pid == null) {
					pid = prefixMap.size();
					prefixMap.put(prefix, pid);
				}
				emit("@" + pid);
				// encode suffix of old read.
				String suffix = lastString.substring(prefix.length());
				emit(suffix);
			}

			// emit map, ordered by value!
			ArrayList<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(prefixMap.entrySet());
			Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() {
				public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
					return o1.getValue().compareTo(o2.getValue());
				}
			});
			for (Map.Entry<String, Integer> m : l) {
				outMap.push(m.getKey());
			}
			if (debug)
				System.out.println("Wrote " + l.size() + " prefixes.");

		} finally {
			outLen.close();
			outData.close();
			outMap.close();
			this.closed = true;
		}
	}

	@Override
	public Long getEncodedEntities() {
		return entityCounter;
	}

	@Override
	public void flush() throws IOException {
		outLen.flush();
		outData.flush();
	}
}
