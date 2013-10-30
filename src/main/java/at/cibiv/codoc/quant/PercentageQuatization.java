package at.cibiv.codoc.quant;

import at.ac.univie.cs.mis.lds.index.itree.Interval;
import at.ac.univie.cs.mis.lds.index.itree.IntervalImpl;
import at.cibiv.codoc.utils.CodocException;

/**
 * Quantization based on coverage-percentage.
 * 
 * The corridor bounds are [cov * (1-perc); cov * (1+perc)]
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class PercentageQuatization implements QuantizationFunction {

	private double percentage;

	public PercentageQuatization(double percentage) throws CodocException {
		if ((percentage < 0) || (percentage > 1f))
			throw new CodocException("percentage has to be in [0; 1]");
		this.percentage = percentage;
	}

	@Override
	public int getMaxBorder(int coverage) {
		return (int) Math.round((double) coverage * (1d + percentage));
	}

	@Override
	public int getMinBorder(int coverage) {
		return (int) Math.round((double) coverage * (1d - percentage));
	}

	@Override
	public Interval<Integer> getBorderInterval(int coverage) {
		return new IntervalImpl<Integer>(getMinBorder(coverage), getMaxBorder(coverage));
	}

	public double getPercentage() {
		return percentage;
	}

	public void setPercentage(double percentage) {
		this.percentage = percentage;
	}

}
