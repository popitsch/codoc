package at.cibiv.codoc.quant;

import at.ac.univie.cs.mis.lds.index.itree.Interval;
import at.ac.univie.cs.mis.lds.index.itree.IntervalImpl;
import at.cibiv.codoc.utils.CodocException;

/**
 * Quantization based on a fixed-height grid.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class RegularGridQuatization implements QuantizationFunction {

	private double height;

	public RegularGridQuatization(double height) throws CodocException {
		if (height < 0)
			throw new CodocException("height has to be a positive value");
		this.height = height;
	}

	@Override
	public int getMaxBorder(int coverage) {
		return getBorderInterval(coverage).getMax();
	}

	@Override
	public int getMinBorder(int coverage) {
		return getBorderInterval(coverage).getMin();
	}

	@Override
	public Interval<Integer> getBorderInterval(int coverage) {
		int lower = 0;
		int upper = 0;
		if (coverage != 0) {
			lower = (int) Math.round(Math.floor((double) coverage / height) * height);
			upper = lower + (int) height;
			if (lower == 0)
				lower = 1;
		}
		Interval<Integer> borders = new IntervalImpl<Integer>(lower, upper);
		return borders;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}

//	/**
//	 * Debugging.
//	 * 
//	 * @param args
//	 * @throws CodocException
//	 */
//	public static void main(String[] args) throws CodocException {
//		RegularGridQuatization q = new RegularGridQuatization(5);
//		for (int i = 0; i < 100; i++) {
//			Interval<Integer> borders = q.getBorderInterval(i);
//			System.out.println(i + " " + borders);
//			if (!borders.contains(i))
//				System.err.println("ERR");
//		}
//	}

}
