package at.cibiv.codoc;

/**
 * A query hit.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class CoverageHit {

	private float interpolatedCoverage;
	private Float lowerBoundary;
	private Float upperBoundary;
	private CodeWordInterval interval;
	private long execTime;
	private boolean isPadding = false;
	private float scaleFactor;

	public CoverageHit(float scaleFactor) {
		this.scaleFactor = scaleFactor;
	}

	
	/**
	 * Rounded, scaled coverage 
	 * @return
	 */
	public int getRoundedCoverage() {
		return Math.round(interpolatedCoverage * scaleFactor);
	}
	
	/**
	 * Rounded, unscaled coverage
	 * @return
	 */
	public int getRoundedCoverageRaw() {
		return Math.round(interpolatedCoverage);
	}
	
	/**
	 * Interpolated, scaled coverage 
	 * @return
	 */
	public float getInterpolatedCoverage() {
		return interpolatedCoverage * scaleFactor;
	}
	
	/**
	 * Interpolated, unscaled coverage 
	 * @return
	 */
	public float getInterpolatedCoverageRaw() {
		return interpolatedCoverage;
	}

	/**
	 * Set the coverag evalue (unscaled!)
	 * @param interpolatedCoverage
	 */
	public void setInterpolatedCoverage(float interpolatedCoverage) {
		this.interpolatedCoverage = interpolatedCoverage;
	}

	/**
	 * Lower error boundary, scaled
	 * @return
	 */
	public Float getLowerBoundary() {
		if ( lowerBoundary == null )
			return null;
		return lowerBoundary * scaleFactor;
	}
	
	/**
	 * Lower error boundary, unscaled
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
	 * @return
	 */
	public Float getUpperBoundary() {
		if ( upperBoundary == null )
			return null;
		return upperBoundary * scaleFactor;
	}

	/**
	 * Upper error boundary, unscaled
	 * @return
	 */
	public Float getUpperBoundaryRaw() {
		if ( upperBoundary == null )
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
	 * @return Codeword interval
	 */
	public CodeWordInterval getInterval() {
		return interval;
	}

	/**
	 * Set the codeword interval
	 */
	public void setInterval(CodeWordInterval interval) {
		this.interval = interval;
	}

	/**
	 * @return true, if the hit was defined as fuzzy or false otherwise.
	 */
	public boolean isFuzzy() {
		if ( upperBoundary == null )
			return false;
		if ( lowerBoundary == null )
			return false;
		
		return (! upperBoundary.equals(lowerBoundary));
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
	 * @return
	 */
	public float getScaleFactor() {
		return scaleFactor;
	}


	public void setScaleFactor(float scaleFactor) {
		this.scaleFactor = scaleFactor;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getInterpolatedCoverage() + " [" + interval.getChr()+ ":" + interval.getMin() + "-" + interval.getMax() + "]" +
				", low:[" + getLowerBoundary() + "/" + getInterval().getLeftCoverage() + "]" +
				", up:[" + getUpperBoundary() + "/" + getInterval().getRightCoverage() + "]" +
				", isFuzzy:" + isFuzzy() + 
				", isPadding:" + isPadding() + 
				", prev:"+getInterval().getPrev() +
				", next:"+getInterval().getNext()
				);
		
//		System.out.println("isFuzzy: " + hit.isFuzzy());
//		System.out.println("isPadding: " + hit.isPadding());
//		// System.out.println("prev:" +
//		// hit.getInterval().getAnnotation("prev"));
//		// System.out.println("next:" +
//		// hit.getInterval().getAnnotation("next"));
//		System.out.println("prev:" + hit.getInterval().getPrev());
//		System.out.println("next:" + hit.getInterval().getNext());
//		System.out.println("lc: " + hit.getInterval().getLeftCoverage());
//		System.out.println("rc: " + hit.getInterval().getRightCoverage());
		
		return sb.toString();
	}

}
