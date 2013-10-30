package at.cibiv.codoc;

/**
 * A query hit.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class CoverageHit {

	private float interpolatedCoverage;
	private Integer lowerBoundary;
	private Integer upperBoundary;
	private CodeWordInterval interval;
	private long execTime;
	private boolean isPadding = false;

	public CoverageHit() {
	}

	
	public int getRoundedCoverage() {
		return Math.round(interpolatedCoverage);
	}
	
	public float getInterpolatedCoverage() {
		return interpolatedCoverage;
	}

	public void setInterpolatedCoverage(float interpolatedCoverage) {
		this.interpolatedCoverage = interpolatedCoverage;
	}

	public int getLowerBoundary() {
		return lowerBoundary;
	}

	public void setLowerBoundary(int lowerBoundary) {
		this.lowerBoundary = lowerBoundary;
	}

	public int getUpperBoundary() {
		return upperBoundary;
	}

	public void setUpperBoundary(int upperBoundary) {
		this.upperBoundary = upperBoundary;
	}

	public CodeWordInterval getInterval() {
		return interval;
	}

	public void setInterval(CodeWordInterval interval) {
		this.interval = interval;
	}

	public boolean isFuzzy() {
		if ( upperBoundary == null )
			return false;
		if ( lowerBoundary == null )
			return false;
		
		return (! upperBoundary.equals(lowerBoundary));
	}

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
