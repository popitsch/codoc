package at.cibiv.codoc.eval;

import java.util.Arrays;
import java.util.Random;

/**
 * Tests binary search methods.
 * @author niko.popitsch@univie.ac.at
 *
 */
public class BinSearchTest {

	private int[] startPos;

	public BinSearchTest(int[] startPos) {
		this.startPos = startPos;
	}

	public void query(int query) {
		int p = Arrays.binarySearch(startPos, query);
		if (p < 0) {
			p = -p - 1;
			if (p - 1 <= 0 || p >= startPos.length)
				System.out.println(query + "-> no result.");
			else
				System.out.println(query + "-> [" + startPos[p - 1] + "-"
						+ startPos[p] + "]");
		} else {

			if (p < 0 || p+1 > startPos.length)
				System.out.println(query + "-> no result.");
			else
				if ( p+1 == startPos.length ) {
					System.out.println(query + "-> [" + startPos[p-1] + "-"
							+ startPos[p] + "].");					
				}
			else
				System.out.println(query + "-> [" + startPos[p] + "-"
						+ startPos[p + 1] + "].");
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Random rand = new Random();
		int last = 0;
		int[] startPos = new int[100000];
		for (int i = 0; i < startPos.length; i++) {
			last += rand.nextInt(10);
			startPos[i] = last+1;
		}
		BinSearchTest data = new BinSearchTest(startPos);

		data.query(0);
		data.query(startPos[0]);
		data.query(startPos[startPos.length - 1]);

		long start = System.currentTimeMillis();
		int queries = 10;
		for (int i = 0; i < queries; i++) {
			int query = rand.nextInt(startPos.length);
			data.query(query);
		}
		long stop = System.currentTimeMillis();
		double tpq = ((double) stop - start) / (double) queries;
		System.out.println("time per query: " + tpq);
		
		
		
		// load 1 array with positions and 1 with coverage values
		// store arrays in hashtable with chrom as key (-> no linked-list required, no itree overhead, etc.)
		// change pos from long to int
		// required storage = 2 * [8] * 100000 = 1.6 MB per block
		
		
	}

}
