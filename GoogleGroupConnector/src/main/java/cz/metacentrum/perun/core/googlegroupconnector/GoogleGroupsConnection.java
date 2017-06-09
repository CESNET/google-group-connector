package cz.metacentrum.perun.core.googlegroupconnector;

import com.google.api.services.admin.directory.Directory;

/**
 * GoogleGroupsConnection handles connection to Google Groups account.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 */
public interface GoogleGroupsConnection {

	/**
	 * Build and returns an authorized Directory service object.
	 *
	 * @return Directory service object that is ready to make requests.
	 */
	public Directory getDirectoryService();
}
