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
     * <p>
     * primaryMail;givenName;familyName;fullName
     *
     * @param usersFile CSV input file
     * @return List of users from file
     */
    List<User> parseUserFile(File usersFile);

    /**
     * Parse input file to list of Group of G Suite domain.
     * Format is:
     * <p>
     * identifier(mail);displayName;list,of,member,identifiers,split,by,commas
     *
     * @param groupsFile CSV input file
     * @return List of groups from file
     */
    List<Group> parseGroupsFile(File groupsFile);

    /**
     * Parse input file to pair of TeamDrive and list of Users who belong to team of G Suite domain.
     * Format is:
     * <p>
     * identifier of TeamDrive(name);mails of team members split by commas
     *
     * @param teamDriveFile CSV input file
     * @return Pair of TeamDrive and List of Users(team members)
     */
    List<GoogleGroupsServiceImpl.DriveWithMembers> parseTeamDrivesFile(File teamDriveFile);

    /**
     * Propagates changes in users from Perun to G Suite domain.
     *
     * @param users List of Users from Perun
     * @throws GoogleGroupsIOException when IOException is thrown while
     *                                 inserting/getting/deleting objects into/from G Suite.
     */

    void processUsers(List<User> users) throws GoogleGroupsIOException;

    /**
     * Propagates changes in groups from Perun to G Suite domain.
     *
     * @param groups List of Groups from Perun
     * @throws GoogleGroupsIOException when IOException is thrown while
     *                                 inserting/getting/deleting objects into/from G Suite.
     */
    void processGroups(List<Group> groups) throws GoogleGroupsIOException, InterruptedException;

    /**
     * Propagates changes in groups membership from Perun to G Suite domain.
     *
     * @param group Group to update members for
     * @return TRUE = group members changed / group members unchanged
     * @throws GoogleGroupsIOException when IOException is thrown while
     *                                 inserting/getting/deleting objects into/from G Suite.
     */
    boolean processGroupMembers(Group group) throws GoogleGroupsIOException;

    /**
     * Propagates changes in team drives from Perun to G Suite domain.
     *
     * @param driveWithMembers List of team drives and users from Perun
     * @throws GoogleGroupsIOException when IOException is thrown while
     *                                 inserting/getting/deleting objects into/from G Suite.
     */
    void processTeamDrives(List<GoogleGroupsServiceImpl.DriveWithMembers> driveWithMembers) throws GoogleGroupsIOException, InterruptedException;

    /**
     * /**
     * Propagates changes in team drive permissions for its users from Perun to G Suite domain.
     *
     * @param driveWithMembers List of team drives and users from Perun
     * @throws GoogleGroupsIOException when IOException is thrown while
     *                                 creating/deleting permissions into/from G Suite.
     */
    void processTeamDriveUsers(GoogleGroupsServiceImpl.DriveWithMembers driveWithMembers) throws GoogleGroupsIOException;

}
