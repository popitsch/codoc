package at.cibiv.codoc.quant;

import at.ac.univie.cs.mis.lds.index.itree.Interval;

/**
 * A quantization function.
 * @author niko.popitsch@univie.ac.at
 *
 */
public interface QuantizationFunction {

	/**
	 * This method returns the upper border of the coverage corridor for the
	 * given coverage value.
	 * 
	 * @param coverage
	 * @return upper bound
	 */
	public int getMaxBorder(int coverage);
	
	/**
	 * This method returns lower upper border of the coverage corridor for the
	 * given coverage value.
	 * 
	 * @param coverage
	 * @return lower bound
	 */
	public int getMinBorder(int coverage);
	
	/**
	 * @param coverage
	 * @return an interval representing the coverage corridor.
	 */
	public Interval<Integer> getBorderInterval( int coverage );
}
