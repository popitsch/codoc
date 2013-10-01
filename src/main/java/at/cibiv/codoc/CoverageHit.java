package at.cibiv.codoc;


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
		sb.append("hit: " + getInterpolatedCoverage() + " [" + interval.getChr()+ ":" + interval.getMin() + "-" + interval.getMax() + "], low:" + getLowerBoundary() + ", up: " + getUpperBoundary());
		return sb.toString();
	}

}
