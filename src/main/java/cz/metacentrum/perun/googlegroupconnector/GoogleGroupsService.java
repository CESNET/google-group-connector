package cz.metacentrum.perun.googlegroupconnector;

import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.User;
import cz.metacentrum.perun.googlegroupconnector.exceptions.GoogleGroupsIOException;
import java.io.File;
import java.util.List;

/**
 * GoogleGroupsService represents google_groups service for Perun.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 */
public interface GoogleGroupsService {

	/**
	 * Parse input file to list of Users of G Suite domain.
	 * Format is:
	 *
	 * primaryMail;givenName;familyName;fullName
	 *
	 * @param usersFile CSV input file
	 * @return List of users from file
	 */
	List<User> parseUserFile(File usersFile);

	/**
	 * Parse input file to list of Group of G Suite domain.
	 * Format is:
	 *
	 * identifier(mail);displayName;list,of,member,identifiers,split,by,commas
	 *
	 * @param groupsFile CSV input file
	 * @return List of groups from file
	 */
	List<Group> parseGroupsFile(File groupsFile);

	/**
	 * Propagates changes in users from Perun to G Suite domain.
	 *
	 * @param users List of Users from Perun
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 * inserting/getting/deleting objects into/from G Suite.
	 */
	void processUsers(List<User> users) throws GoogleGroupsIOException;

	/**
	 * Propagates changes in groups from Perun to G Suite domain.
	 *
	 * @param groups List of Groups from Perun
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 * inserting/getting/deleting objects into/from G Suite.
	 */
	void processGroups(List<Group> groups) throws GoogleGroupsIOException;

	/**
	 * Propagates changes in groups membership from Perun to G Suite domain.
	 *
	 * @param group Group to update members for
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 * inserting/getting/deleting objects into/from G Suite.
	 */
	void processGroupMembers(Group group) throws GoogleGroupsIOException;

}
