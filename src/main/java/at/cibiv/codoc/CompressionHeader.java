package at.cibiv.codoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CompressionHeader {

	public static final String PREFIX = "#";

	public static final String SEP = "--------------------------------";

	Map<String, List<String>> map = new HashMap<String, List<String>>();

	public CompressionHeader() {
	}

	public void addValue(String k, String v) {
		List<String> l = map.get(k);
		if (l == null)
			l = new ArrayList<String>();
		l.add(v);
		map.put(k, l);
	}

	public String getFirstValue(String k) {
		List<String> l = map.get(k);
		if ((l == null) || (l.size() == 0))
			return null;
		return l.get(0);
	}

	public List<String> getValues(String k) {
		return map.get(k);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String k : map.keySet())
			sb.append(PREFIX + k + "\t" + map.get(k) + "\n");
		return sb.toString();
	}

	public static CompressionHeader fromString(String s) throws IOException {
		BufferedReader sr = new BufferedReader(new StringReader(s));
		String line;
		CompressionHeader h = new CompressionHeader();
		while ((line = sr.readLine()) != null) {
			if (line.startsWith(PREFIX)) {
				String cut = line.substring(PREFIX.length());
				String[] tmp = cut.split("\t");
				h.addValue(tmp[0], tmp[1]);
			}
		}
		return h;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
