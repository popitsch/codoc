package at.cibiv.codoc.quant;


import at.ac.univie.cs.mis.lds.index.itree.Interval;
import at.ac.univie.cs.mis.lds.index.itree.IntervalImpl;
import at.cibiv.codoc.utils.CodocException;
import at.cibiv.codoc.utils.MathUtils;

/**
 * Quantization based on a log2 grid.
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class LogarithmicGridQuatization implements QuantizationFunction {

	public LogarithmicGridQuatization() throws CodocException {
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
			long ld = MathUtils.ldfloor(coverage);
			try {
				upper = (int) MathUtils.pow2(ld + 1);
				lower = upper >> 1;
			} catch (CodocException e) {
				e.printStackTrace();
			}

		}
		Interval<Integer> borders = new IntervalImpl<Integer>(lower, upper);
		return borders;
	}

//	/**
//	 * Debugging.
//	 * 
//	 * @param args
//	 * @throws CodocException
//	 */
//	public static void main(String[] args) throws CodocException {
//		LogarithmicGridQuatization q = new LogarithmicGridQuatization();
//		for (int i = 50; i > 0; i--) {
//			Interval<Integer> borders = q.getBorderInterval(i);
//			System.out.println(i + "=>" + borders);
//			if (!borders.contains(i))
//				System.err.println("ERR");
//		}
//	}

}
