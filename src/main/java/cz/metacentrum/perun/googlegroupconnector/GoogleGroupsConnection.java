package cz.metacentrum.perun.googlegroupconnector;

import com.google.api.services.directory.Directory;
import com.google.api.services.drive.Drive;

import java.util.Properties;

/**
 * Handles connection to G suite account and provides configuration of your domain.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 * @author Pavel Zlamal <zlamal@cesnet.cz>
 */
public interface GoogleGroupsConnection {

	/**
	 * Build and returns an authorized Directory service object.
	 *
	 * @return Directory service object that is ready to make requests.
	 */
	Directory getDirectoryService();

	/**
	 * Return configuration properties for G suite domain.
	 *
	 * @return Configuration properties for G suite domain
	 */
	Properties getProperties();

	/**
	 * Build and return an authorized Drive client service.
	 *
	 * @return an authorized Drive client service
	 */
	Drive getDriveService();


}
