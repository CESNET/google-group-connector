package cz.metacentrum.perun.googlegroupconnector;

import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.User;
import com.google.api.services.drive.model.TeamDrive;
import cz.metacentrum.perun.googlegroupconnector.exceptions.GoogleGroupsIOException;
import java.io.File;
import java.util.List;
import java.util.Map;

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
	 * Parse input file to pair of TeamDrive and list of Users who belong to team of G Suite domain.
	 * Format is:
	 *
	 * identifier of TeamDrive(name);mails of team members split by commas
	 *
	 * @param teamDriveFile CSV input file
	 * @return Pair of TeamDrive and List of Users(team members)
	 */
	Map<TeamDrive, List<User>> parseTeamDrivesFile(File teamDriveFile);

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
	void processGroups(List<Group> groups) throws GoogleGroupsIOException, InterruptedException;

	/**
	 * Propagates changes in groups membership from Perun to G Suite domain.
	 *
	 * @param group Group to update members for
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 * inserting/getting/deleting objects into/from G Suite.
	 * @return TRUE = group members changed / group members unchanged
	 */
	boolean processGroupMembers(Group group) throws GoogleGroupsIOException;


	/**
	 * Propagates changes in team drives from Perun to G Suite domain.
	 *
	 * @param driveWithMembers List of team drives and users from Perun
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 *                                 inserting/getting/deleting objects into/from G Suite.
	 */
	void processTeamDrives(Map<TeamDrive, List<User>> driveWithMembers) throws GoogleGroupsIOException, InterruptedException;

	/**
	 * Propagates changes in TeamDrive Permissions for its users from Perun to G Suite domain.
	 * TeamDrive must be retrieved from domain in order to contain ID.
	 *
	 * @param teamDrive TeamDrive to process (with ID set !!)
	 * @param users Users to process
	 * @throws GoogleGroupsIOException when IOException is thrown while
	 *                                 creating/deleting permissions into/from G Suite.
	 */
	void processTeamDrivePermissions(TeamDrive teamDrive, List<User> users) throws GoogleGroupsIOException;

}
