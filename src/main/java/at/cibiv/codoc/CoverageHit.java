package at.cibiv.codoc;

import at.cibiv.codoc.quant.QuantizationFunction;

/**
 * A query hit.
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

	public CoverageHit(String chr, int left, int right, int leftCoverage, int rightCoverage, int qpos, float scaleFactor) {
		this.scaleFactor = scaleFactor;
		this.chr = chr;
		this.left = left;
		this.right = right;
		this.leftCoverage = leftCoverage;
		this.rightCoverage = rightCoverage;
		this.qpos = qpos;
	}

	/**
	 * Rounded, scaled coverage
	 * 
	 * @return
	 */
	public int getRoundedCoverage() {
		if (interpolatedCoverage == null) {
			getInterpolatedCoverageRaw();
		}
		return Math.round(interpolatedCoverage * scaleFactor);
	}

	/**
	 * Rounded, unscaled coverage
	 * 
	 * @return
	 */
	public int getRoundedCoverageRaw() {
		if (interpolatedCoverage == null) {
			getInterpolatedCoverageRaw();
		}
		return Math.round(interpolatedCoverage);
	}

	/**
	 * Interpolated, scaled coverage
	 * 
	 * @return
	 */
	public float getInterpolatedCoverage() {
		if (interpolatedCoverage == null) {
			getInterpolatedCoverageRaw();
		}
		return interpolatedCoverage * scaleFactor;
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
		if (interpolatedCoverage == null) {
			if (getWidth() == 0)
				interpolatedCoverage = (float) rightCoverage;
			else {
				float prec = (float) (qpos - left) / (float) getWidth();
				interpolatedCoverage = leftCoverage + ((rightCoverage - leftCoverage) * prec);
			}
		}
		return interpolatedCoverage;
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

	public void decorateWithBoundaries(QuantizationFunction quant) {
		setUpperBoundary((float) quant.getMaxBorder(leftCoverage));
		setLowerBoundary((float) quant.getMinBorder(leftCoverage));
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getInterpolatedCoverage() + " [" + getChr() + ":" + getLeft() + "-" + getRight() + "]" + ", low:[" + getLowerBoundary() + "/"
				+ getLeftCoverage() + "]" + ", up:[" + getUpperBoundary() + "/" + getRightCoverage() + "]" + ", isFuzzy:" + isFuzzy() + ", isPadding:"
				+ isPadding());

		// System.out.println("isFuzzy: " + hit.isFuzzy());
		// System.out.println("isPadding: " + hit.isPadding());
		// // System.out.println("prev:" +
		// // hit.getInterval().getAnnotation("prev"));
		// // System.out.println("next:" +
		// // hit.getInterval().getAnnotation("next"));
		// System.out.println("prev:" + hit.getInterval().getPrev());
		// System.out.println("next:" + hit.getInterval().getNext());
		// System.out.println("lc: " + hit.getInterval().getLeftCoverage());
		// System.out.println("rc: " + hit.getInterval().getRightCoverage());

		return sb.toString();
	}


}
