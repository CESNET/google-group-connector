package cz.metacentrum.perun.googlegroupconnector.exceptions;

/**
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 */
public class GoogleGroupsIOException extends Exception {

	public GoogleGroupsIOException() {
		super();
	}

	public GoogleGroupsIOException(String msg) {
		super(msg);
	}

	public GoogleGroupsIOException(Throwable cause) {
		super(cause);
	}

	public GoogleGroupsIOException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
