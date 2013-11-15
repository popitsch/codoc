package at.cibiv.codoc.io;

/**
 * A generic run-length
 * 
 * @author niko.popitsch@univie.ac.at
 * 
 * @param <T>
 */
public class RunLength<T> {

	/**
	 * the object that "runs", e.g., Long numbers, Strings or objects of any
	 * other class.
	 */
	T c;

	/**
	 * The length of this run-length.
	 */
	int len;

	/**
	 * Constructor
	 * 
	 * @param c
	 */
	public RunLength(T c) {
		this.c = c;
		this.len = 1;
	}

	/**
	 * Constructor
	 * 
	 * @param c
	 */
	public RunLength(T c, int len) {
		this.c = c;
		this.len = len;
	}

	public void inc() {
		len++;
	}

	public void inc(int x) {
		len += x;
	}

	/**
	 * Decreases the RLE and returns true if there is a length left or false
	 * otherwise.
	 * 
	 * @return
	 */
	public boolean dec() {
		len--;
		if (len >= 0)
			return true;
		return false;
	}

	/**
	 * @return the object that "runs"
	 */
	public T getObject() {
		return c;
	}

	/**
	 * @return the length
	 */
	public int getLen() {
		return len;
	}

	/**
	 * Set the length.
	 * 
	 * @param len
	 */
	public void setLen(int len) {
		this.len = len;
	}

	/**
	 * Debugging.
	 */
	public String toString() {
		return ("[" + c + ":" + len + "]");
	}
}