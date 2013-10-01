package at.cibiv.codoc;

import at.cibiv.ngs.tools.lds.GenomicInterval;
import at.cibiv.ngs.tools.util.GenomicPosition.COORD_TYPE;

/**
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 */
public class CodeWordInterval extends GenomicInterval {

	int leftCoverage, rightCoverage;
	CodeWordInterval prev, next;

	/**
	 * Constructor.
	 */
	public CodeWordInterval(String chr, Long min, Long max, String uri, COORD_TYPE type) {
		super(chr, min, max, uri, type);
	}

	/**
	 * Constructor.
	 */
	public CodeWordInterval(String chr, Long min, Long max, String uri) {
		super(chr, min, max, uri);
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

	public CodeWordInterval getPrev() {
		return prev;
	}

	public void setPrev(CodeWordInterval prev) {
		this.prev = prev;
	}

	public CodeWordInterval getNext() {
		return next;
	}

	public void setNext(CodeWordInterval next) {
		this.next = next;
	}
	
	@Override
	public String toString() {
		String ret = super.toString();
		ret+=", lc:"+getLeftCoverage() + ",rc:"+getRightCoverage();
		return ret;
	}
	

}
