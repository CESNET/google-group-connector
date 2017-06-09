package cz.metacentrum.perun.core.googlegroupconnector;

import cz.metacentrum.perun.core.googlegroupconnector.exceptions.GoogleGroupsIOException;
import java.io.File;

/**
 * GoogleGroupsService represents google_groups service for Perun.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 */
public interface GoogleGroupsService {

	/**
	 * Propagates changes from Perun to Google Groups.
	 *
	 * @param file file containing data from Perun
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 * inserting/getting/deleting objects into/from Google Group.
	 */
	public void compareAndPropagateData(File file) throws GoogleGroupsIOException;
}
