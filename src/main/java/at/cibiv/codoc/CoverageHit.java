package at.cibiv.codoc;

import at.cibiv.codoc.quant.QuantizationFunction;

/**
 * A query hit. Use only if you need access to the individual data fields
 * (otherwise, the static getInterpolatedCoverage() method is much faster!).
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CoverageHit {

	private Float interpolatedCoverage;
	private Float lowerBoundary;
	private Float upperBoundary;
	private String chr;
	private int left;
	private int right;
	private int leftCoverage;
	private int rightCoverage;
	private long execTime;
	private boolean isPadding = false;
	private float scaleFactor;
	private int qpos;

	/**
	 * Constructor.
	 * 
	 * @param chr
	 * @param left
	 * @param right
	 * @param leftCoverage
	 * @param rightCoverage
	 * @param qpos
	 * @param scaleFactor
	 */
	public CoverageHit(String chr, int left, int right, int leftCoverage, int rightCoverage, int qpos, float scaleFactor) {
		this.scaleFactor = scaleFactor;
		this.chr = chr;
		this.left = left;
		this.right = right;
		this.leftCoverage = leftCoverage;
		this.rightCoverage = rightCoverage;
		this.qpos = qpos;
		this.interpolatedCoverage = getInterpolatedCoverage(left, right, leftCoverage, rightCoverage, qpos, scaleFactor);
	}

	/**
	 * Linear interpolation of the coverage.
	 * 
	 * @param chr
	 * @param l
	 * @param r
	 * @param lc
	 * @param rc
	 * @param qp
	 * @param scaleFactor
	 * @return
	 */
	public static float getInterpolatedCoverage(int l, int r, int lc, int rc, int qp, float scaleFactor) {
		int w = (r - l);
		if (w == 0) 
			return (float) rc* scaleFactor;
		float prec = (float) (qp - l) / (float) w;
		float ret = lc + ((rc - lc) * prec);
		return ret * scaleFactor;
	}

	/**
	 * Rounded, scaled coverage
	 * 
	 * @return
	 */
	public int getRoundedCoverage() {
		return Math.round(interpolatedCoverage);
	}

	/**
	 * Rounded, unscaled coverage
	 * 
	 * @return
	 */
	public int getRoundedCoverageRaw() {
		return Math.round(getInterpolatedCoverage(left, right, leftCoverage, rightCoverage, qpos, 1.0f));
	}

	/**
	 * Interpolated, scaled coverage
	 * 
	 * @return
	 */
	public float getInterpolatedCoverage() {
		return interpolatedCoverage;
	}

	public int getWidth() {
		return right - left;
	}

	/**
	 * Interpolated, unscaled coverage
	 * 
	 * @return
	 */
	public float getInterpolatedCoverageRaw() {
		return getInterpolatedCoverage(left, right, leftCoverage, rightCoverage, qpos, 1.0f);
	}

	/**
	 * Lower error boundary, scaled
	 * 
	 * @return
	 */
	public Float getLowerBoundary() {
		if (lowerBoundary == null)
			return null;
		return lowerBoundary * scaleFactor;
	}

	/**
	 * Lower error boundary, unscaled
	 * 
	 * @return
	 */
	public Float getLowerBoundaryRaw() {
		return lowerBoundary;
	}

	/**
	 * Set lower error boundary, unscaled
	 */
	public void setLowerBoundary(Float lowerBoundary) {
		this.lowerBoundary = lowerBoundary;
	}

	/**
	 * Upper error boundary, scaled
	 * 
	 * @return
	 */
	public Float getUpperBoundary() {
		if (upperBoundary == null)
			return null;
		return upperBoundary * scaleFactor;
	}

	/**
	 * Upper error boundary, unscaled
	 * 
	 * @return
	 */
	public Float getUpperBoundaryRaw() {
		if (upperBoundary == null)
			return null;
		return upperBoundary;
	}

	/**
	 * Set upper error boundary, unscaled
	 */
	public void setUpperBoundary(Float upperBoundary) {
		this.upperBoundary = upperBoundary;
	}

	/**
	 * @return true, if the hit was defined as fuzzy or false otherwise.
	 */
	public boolean isFuzzy() {
		if (upperBoundary == null)
			return false;
		if (lowerBoundary == null)
			return false;

		return (!upperBoundary.equals(lowerBoundary));
	}

	/**
	 * @return true if this hit was set as stemming from a padding region.
	 */
	public boolean isPadding() {
		return isPadding;
	}

	public void setPadding(boolean isPadding) {
		this.isPadding = isPadding;
	}

	/**
	 * Query execution time in nano seconds
	 * 
	 * @return
	 */
	public long getExecTime() {
		return execTime;
	}

	/**
	 * Query execution time in nano seconds
	 * 
	 * @param execTime
	 */
	public void setExecTime(long execTime) {
		this.execTime = execTime;
	}

	/**
	 * The scaling factor.
	 * 
	 * @return
	 */
	public float getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(float scaleFactor) {
		this.scaleFactor = scaleFactor;
	}

	public String getChr() {
		return chr;
	}

	public void setChr(String chr) {
		this.chr = chr;
	}

	public int getLeft() {
		return left;
	}

	public void setLeft(int left) {
		this.left = left;
	}

	public int getRight() {
		return right;
	}

	public void setRight(int right) {
		this.right = right;
	}

	public int getLeftCoverage() {
		return leftCoverage;
	}

	public void setLeftCoverage(int leftCoverage) {
		this.leftCoverage = leftCoverage;
	}

	public int getRightCoverage() {
		return rightCoverage;
	}

	public void setRightCoverage(int rightCoverage) {
		this.rightCoverage = rightCoverage;
	}

	public int getQpos() {
		return qpos;
	}

	public void setQpos(int qpos) {
		this.qpos = qpos;
	}

	/**
	 * set the upper and lower boundary for this hit based on the passed
	 * quantization function.
	 * 
	 * @param quant
	 */
	public void decorateWithBoundaries(QuantizationFunction quant) {
		setUpperBoundary((float) quant.getMaxBorder(leftCoverage));
		setLowerBoundary((float) quant.getMinBorder(leftCoverage));
	}

	/**
	 * Prints the interpolated coverage
	 */
	@Override
	public String toString() {
		return (isFuzzy() ? "~" : "") + String.format("%.2f", getInterpolatedCoverage());
	}

	/**
	 * Verbose toString() method.
	 * @return
	 */
	public String toStringVerbose() {
		StringBuilder sb = new StringBuilder();
		sb.append(getInterpolatedCoverage()
				+ " ["
				+ getChr()
				+ ":"
				+ getLeft()
				+ "-"
				+ getRight()
				+ "]"
				+ (isFuzzy() ? ", low:[" + getLowerBoundary() + "/" + getLeftCoverage() + "]" + ", up:[" + getUpperBoundary() + "/" + getRightCoverage() + "]"
						: "") + (isPadding() ? ", from padding!" : ""));
		return sb.toString();
	}

}
