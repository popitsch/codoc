package at.cibiv.codoc.utils;


/**
 * A general exception.
 * 
 * @author niko.popitsch@univie.ac.at
 *
 */
public class CodocException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public CodocException(String s) {
		super(s);
	}

	public CodocException(String s, Throwable e ) {
		super(s + ": " + e.toString() );
	}

}
