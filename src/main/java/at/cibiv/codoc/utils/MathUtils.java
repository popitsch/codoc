package at.cibiv.codoc.utils;

import java.text.DecimalFormat;
import java.util.Random;

/**
 * Mathematical utilities
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class MathUtils {

	public final static DecimalFormat decimalFormat1 = new DecimalFormat("#,###,###,##0");

	/**
	 * NOTE: make sure that 0 <= exp <= 62
	 * 
	 * @return Returns 2^exp
	 */
	public static long pow2(long exp) throws CodocException {
		if (exp < 0L)
			throw new CodocException("pow2 argument " + exp + " out of range (0 <= exp <= 62)");
		if (exp > 62L)
			throw new CodocException("pow2 argument " + exp + " out of range (0 <= exp <= 62)");
		if (exp == 0L)
			return 1L;
		return 2L << (exp - 1);
	}

	/**
	 * The rounded logarithmus dualis of the passed value. If the value is
	 * negative, 0 is returned.
	 * 
	 * @param value
	 * @return ld(value)
	 */
	public static long ld(long value) {
		if (value <= 0)
			return 0;

		int ld = (int) Math.round(Math.log(value) / Math.log(2));
		return ld;
	}

	/**
	 * The floored logarithmus dualis of the passed value. If the value is
	 * negative, 0 is returned.
	 * 
	 * @param value
	 * @return ld(value)
	 */
	public static long ldfloor(long value) {
		if (value <= 0)
			return 0;

		int ld = (int) Math.floor(Math.log(value) / Math.log(2));
		return ld;
	}
	/**
	 * Calculates a lognorm distributed random number.
	 * 
	 * @param rand
	 * @param mean
	 * @param variance
	 * @return
	 */
	public static int randomLognorm(Random rand, double mean, double variance) {
		double x = rand.nextGaussian();
		double meansquared = mean * mean;
		double m = Math.log(meansquared / Math.sqrt(meansquared + (variance * variance)));
		double varbymean = variance / mean;
		double s = Math.sqrt(Math.log(varbymean * varbymean + 1));
		return (int) Math.exp(x * s + m);
	}

	/**
	 * Format a long/integer number
	 * 
	 * @param num
	 * @return
	 */
	public static String formatIntegerNumber(Long num) {
		if (num == null)
			return "null";
		return decimalFormat1.format(num);
	}

//	/**
//	 * Debugging.
//	 * @param args
//	 * @throws CodocException 
//	 */
//	public static void main(String[] args) throws CodocException {
//		
//		for ( int i = 0; i < 10; i++) {
//			long ld = MathUtils.ldfloor(i);
//			
//			long lower = i==0?0:MathUtils.pow2(ld);
//			long upper = i==0?0:MathUtils.pow2(ld+1);
//			
//			
//			System.out.println(i + " -> ld " + ld + " [" + lower + "," + upper + "]");
//			if ( ! ( ( i >= lower) &&  ( i <= upper) ) )
//				System.err.println("Err");
//		}
//
//	}

}
