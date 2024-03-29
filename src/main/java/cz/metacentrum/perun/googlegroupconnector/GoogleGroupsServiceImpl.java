package cz.metacentrum.perun.googlegroupconnector;

import com.google.api.services.directory.model.UserName;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.PermissionList;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import cz.metacentrum.perun.googlegroupconnector.exceptions.GoogleGroupsIOException;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.Members;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.Users;
import com.google.api.services.drive.model.TeamDriveList;
import com.google.api.services.drive.model.TeamDrive;
import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * GoogleGroupsServiceImpl is an implementation of GoogleGroupsService interface.
 * <p>
 * This class calls GoogleGroupsConnectionImpl to prepare connection to Google
 * Groups via API and then handles propagation of changes from Perun to Google
 * Groups (insert/delete entries);
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 * @author Pavel Zlamal <zlamal@cesnet.cz>
 */
public class GoogleGroupsServiceImpl implements GoogleGroupsService {

	private final static org.slf4j.Logger log = LoggerFactory.getLogger(GoogleGroupsServiceImpl.class);
	private static Directory service;
	private static Drive driveService;
	private String domainName;
	private Properties properties;
	private final Map<String, List<String>> groupsMembers = new HashMap<>();

	private static int usersInserted = 0;
	private static int usersUpdated = 0;
	private static int usersSuspended = 0;
	private static int usersDeleted = 0;
	private static int groupsInserted = 0;
	private static int groupsUpdated = 0;
	private static int groupsDeleted = 0;
	private static int groupsUpdatedMembers = 0;
	private static int teamDrivesInserted = 0;
	private static int teamDrivesDeleted = 0;
	private static int teamDriveUsersAdded = 0;
	private static int teamDriveUsersDeleted = 0;

	private static boolean dryRun = false;


	/**
	 * Main method starting (de)provisioning of G Suite on your domain.
	 *
	 * Based on configuration in /etc/perun/google_groups-your.domain.com.properties
	 * and passed arguments it perform different actions:
	 *
	 * Args:
	 * [0] domain name
	 * [1] action: "users", "groups", "teamDrives"
	 * [2] path to CSV file with data
	 *
	 * @param args [0] domain name, [1] action [2] path to CSV file with data
	 * @throws IOException When reading of input file fails
	 * @throws GeneralSecurityException When connector is unable to access G Suite API
	 * @throws GoogleGroupsIOException When specific API call to G Suite returns Exception
	 */
	public static void main(String[] args) throws IOException, GeneralSecurityException, GoogleGroupsIOException {

		try {

			String domainFile = "/etc/perun/google_groups-";
			String action = null;
			String inputFilePath = null;

			if (args.length > 2) {
				domainFile = domainFile + args[0] + ".properties";
				action = args[1];
				inputFilePath = args[2];
			} else {
				throw new IllegalArgumentException("Wrong number of input arguments (less than 3).");
			}

			if (inputFilePath == null || inputFilePath.isEmpty()) {
				log.error("Input file path is empty.");
				throw new IllegalArgumentException("File path can't be empty.");
			}

			File inputFile = new File(inputFilePath);

			GoogleGroupsConnectionImpl connection = new GoogleGroupsConnectionImpl(domainFile);
			service = connection.getDirectoryService();
			driveService = connection.getDriveService();

			GoogleGroupsServiceImpl session = new GoogleGroupsServiceImpl();
			session.domainName = connection.getDomainName();
			session.properties = connection.getProperties();
			dryRun = Boolean.parseBoolean(session.properties.getProperty("dry_run", "false"));

			if (dryRun) System.out.println("========== DRY RUN ==========\n* Only READ operations with Google API are done.\n* WRITE operations are not actually called, but only logged.\n=============================");

			switch (action) {
				case "users":
					List<User> users = session.parseUserFile(inputFile);
					log.info("Users file parsed...");
					if (users == null || users.isEmpty()) {
						log.warn("Processing of users skipped.");
					} else {
						session.processUsers(users);
						log.info("Processing of users done.");
					}
					System.out.println("Users inserted: "+usersInserted);
					System.out.println("Users updated: "+usersUpdated);
					System.out.println("Users suspended: "+usersSuspended);
					System.out.println("Users deleted: "+usersDeleted);
					return;
				case "groups":
					List<Group> groups = session.parseGroupsFile(inputFile);
					log.info("Groups file parsed...");
					if (groups == null || groups.isEmpty()) {
						log.warn("Processing of groups skipped.");
					} else {
						session.processGroups(groups);
						log.info("Processing of groups done.");
					}
					System.out.println("Groups inserted: "+groupsInserted);
					System.out.println("Groups updated: "+groupsUpdated);
					System.out.println("Groups with updated members: "+groupsUpdatedMembers);
					System.out.println("Groups deleted: "+groupsDeleted);
					return;

				case "teamDrives":
					Map<TeamDrive, List<User>> drivesWithMembers = session.parseTeamDrivesFile(inputFile);
					log.info("Team drives file parsed...");
					if (drivesWithMembers == null || drivesWithMembers.isEmpty()) {
						log.warn("Processing of team drives skipped.");
					} else {
						session.processTeamDrives(drivesWithMembers);
						log.info("Processing of team drives done.");
					}
					System.out.println("Team drives inserted: " + teamDrivesInserted);
					System.out.println("Team drives deleted: " + teamDrivesDeleted);
					System.out.println("Team drive permissions added: " + teamDriveUsersAdded);
					System.out.println("Team drive permissions deleted: " + teamDriveUsersDeleted);
					return;


				default:
					log.error("Invalid action: {}. Please use: \"users\" or \"groups\" as action.", action);
					throw new IllegalArgumentException("Invalid action: " + action + ". Please use: \"users\" or \"groups\" as action.");
			}

		} catch (Throwable ex) {
			// make sure java ends with non-zero exit code on fail.
			System.out.println(ex.getClass().getSimpleName() + ": " + ex.getMessage());
			System.exit(1);
		}

	}

	@Override
	public List<User> parseUserFile(File usersFile) {

		List<User> result = new ArrayList<>();
		FileReader fileReader = null;
		try {

			fileReader = new FileReader(usersFile);
			CSVReader reader = createCSVReader(fileReader);

			List<String[]> lines = reader.readAll();
			if (lines != null && !lines.isEmpty()) {

				for (String[] line : lines) {

					if (line.length < 4) {
						log.error("Users file contains row with less than 4 columns: {}", (Object) line);
						throw new IllegalArgumentException("Users file contains row with less than 4 columns:" + line[0]);
					}

					User user = new User();
					// primary user identifier
					user.setPrimaryEmail(line[0]);

					// skip group outside own domain !!
					if (!Objects.equals(user.getPrimaryEmail().substring(user.getPrimaryEmail().indexOf("@")+1), domainName)) {
						log.warn("User: {} is not from your domain: {}. Skip it.", user, domainName);
						continue;
					}

					UserName name = new UserName();
					String fullName = "";
					// set given name
					if (line[1] != null && !line[1].isEmpty()) {
						name.setGivenName(line[1]);
						fullName += line[1];
					}
					// set family name
					if (line[2] != null && !line[2].isEmpty()) {
						name.setFamilyName(line[2]);
						// correctly set full name
						if (line[1] != null && !line[1].isEmpty()) {
							fullName += " " + line[2];
						} else {
							fullName += line[2];
						}
					}
					if (!fullName.isEmpty()) name.setFullName(fullName);

					// set name to user
					user.setName(name);

					// set status
					user.setSuspended(("suspended".equals(line[3])));

					// add user to list
					result.add(user);
				}

				return result;

			} else {
				log.error("Users file contains no rows.");
				throw new IllegalArgumentException("Users file contains no rows.");
			}

		} catch (FileNotFoundException ex) {
			log.error("Users file {} was not found: {}", usersFile.getAbsolutePath(), ex);
		} catch (IOException ex) {
			log.error("Problem with I/O operation while reading lines of file {} by FileReader.readNext() or getting file: {}", usersFile.getAbsolutePath(), ex);
		} catch (CsvException ex) {
			log.error("Users file {} is invalid CSV. ", usersFile.getAbsolutePath(), ex);
		} finally {
			try {
				if (fileReader != null) fileReader.close();
			} catch (IOException ex) {
				log.error("Problem with I/O operation while closing file {} : {}", usersFile.getAbsolutePath(), ex);
			}
		}

		return null;

	}

	@Override
	public List<Group> parseGroupsFile(File groupsFile) {

		List<Group> result = new ArrayList<>();
		FileReader fileReader = null;
		try {

			fileReader = new FileReader(groupsFile);

			CSVReader reader = createCSVReader(fileReader);

			List<String[]> lines = reader.readAll();
			if (lines != null && !lines.isEmpty()) {

				for (String[] line : lines) {

					if (line.length < 3) {
						log.error("Groups file contains row with less than 3 columns: {}", (Object) line);
						throw new IllegalArgumentException("Groups file contains row with less than 3 columns:" + line[0]);
					}

					Group group = new Group();
					group.setEmail(line[0].toLowerCase()); // since Google is case insensitive
					if (line[1] != null && !line[1].isEmpty()) {
						group.setName(line[1]);
					}

					// skip group outside own domain !!
					if (!Objects.equals(group.getEmail().substring(group.getEmail().indexOf("@")+1), domainName)) {
						log.warn("Group: {} is not from your domain: {}. Skip it.", group, domainName);
						continue;
					}

					result.add(group);

					if (line[2] != null && !line[2].isEmpty()) {
						groupsMembers.putIfAbsent(group.getEmail(), Arrays.asList(line[2].split(",")));
					} else {
						groupsMembers.putIfAbsent(group.getEmail(), new ArrayList<>());
					}

				}

				return result;

			} else {
				log.error("Groups file contains no rows.");
				throw new IllegalArgumentException("Groups file contains no rows.");
			}

		} catch (FileNotFoundException ex) {
			log.error("Groups  file {} was not found: {}", groupsFile.getAbsolutePath(), ex);
		} catch (IOException ex) {
			log.error("Problem with I/O operation while reading lines of file {} by FileReader.readNext() or getting file: {}", groupsFile.getAbsolutePath(), ex);
		} catch (CsvException ex) {
			log.error("Groups file {} is invalid CSV. ", groupsFile.getAbsolutePath(), ex);
		} finally {
			try {
				if (fileReader != null) fileReader.close();
			} catch (IOException ex) {
				log.error("Problem with I/O operation while closing file {} : {}", groupsFile.getAbsolutePath(), ex);
			}
		}

		return null;

	}

	@Override
	public Map<TeamDrive, List<User>> parseTeamDrivesFile(File teamDriveFile) {

		Map<TeamDrive, List<User>> result = new HashMap<>();
		FileReader fileReader = null;

		try {

			fileReader = new FileReader(teamDriveFile);

			CSVReader reader = createCSVReader(fileReader);

			List<String[]> lines = reader.readAll();
			if (lines != null && !lines.isEmpty()) {

				for (String[] line : lines) {

					if (line.length < 2) {
						log.error("TeamDrive file contains row with less than 2 columns: {}", (Object) line);
						throw new IllegalArgumentException("TeamDrive file contains row with less than 3 columns:" + line[0]);
					}

					TeamDrive teamDriveResult = new TeamDrive();
					List<User> userListResult = new ArrayList<>();

					teamDriveResult.setName(line[0]);

					if (line[1] != null && !line[1].isEmpty()) {
						String[] membersEmail = line[1].split(",");
						for (String userMail : membersEmail) {
							userMail = userMail.replaceAll("\\s+", "");
							User user = new User();
							user.setPrimaryEmail(userMail);
							userListResult.add(user);
						}
					}

					result.put(teamDriveResult, userListResult);
				}

				return result;
			} else {
				log.error("Team drive file contains no rows.");
				throw new IllegalArgumentException("Team drive file contains no rows.");
			}
		} catch (IOException ex) {
			log.error("Problem with I/O operation while reading lines of file {} by FileReader.readNext() or getting file: {}", teamDriveFile.getAbsolutePath(), ex);
		} catch (CsvException ex) {
			log.error("TeamDrive file {} is invalid CSV. ", teamDriveFile.getAbsolutePath(), ex);
		} finally {
			try {
				if (fileReader != null) fileReader.close();
			} catch (IOException ex) {
				log.error("Problem with I/O operation while closing file {} : {}", teamDriveFile.getAbsolutePath(), ex);
			}
		}
		return null;
	}

	@Override
	public void processUsers(List<User> users) throws GoogleGroupsIOException {

		Users du = getDomainUsers(domainName);
		if (du != null && !du.isEmpty() && du.getUsers() != null && !du.getUsers().isEmpty()) {

			// domain is not empty, compare state
			List<User> domainUsers = new ArrayList<>(du.getUsers());

			for (User user : users) {
				User domainUser = null;
				for (User userInDomain : domainUsers) {
					if (Objects.equals(userInDomain.getPrimaryEmail(), user.getPrimaryEmail())) {
						domainUser = userInDomain;
						break;
					}
				}

				if (domainUser == null) {

					// create new user
					if (!user.getSuspended()) {
						insertUser(user);
						log.info("User created: {}", user.getPrimaryEmail());
						usersInserted++;
					} else {
						log.warn("User not created - is in suspended state: {}", user.getPrimaryEmail());
					}

				} else {

					// already in domain - update name and status if changed
					if (!Objects.equals(user.getName().getFamilyName(), domainUser.getName().getFamilyName()) ||
							!Objects.equals(user.getName().getGivenName(), domainUser.getName().getGivenName()) ||
							!Objects.equals(user.getSuspended(), domainUser.getSuspended())) {

						updateUser(user.getPrimaryEmail(), user);
						log.info("User updated: {}", user.getPrimaryEmail());
						usersUpdated++;
						if (user.getSuspended()) usersSuspended++;

					} else {
						log.info("User skipped: {}", user.getPrimaryEmail());
					}

				}

			}

			// check users for removal
			for (User userInDomain : domainUsers) {
				boolean isInPerun = false;
				for (User user : users) {
					if (Objects.equals(userInDomain.getPrimaryEmail(), user.getPrimaryEmail())) {
						isInPerun = true;
						break;
					}
				}

				if (!isInPerun) {

					if (Boolean.getBoolean(properties.getProperty("allow_delete", "false"))) {
						// deleting domain users is allowed
						deleteUser(userInDomain.getPrimaryEmail());
						log.info("User deleted: {}", userInDomain.getPrimaryEmail());
						usersDeleted++;
					} else {
						// deletion of domain users is disabled - suspend instead
						if (!userInDomain.getSuspended()) {
							userInDomain.setSuspended(true);
							updateUser(userInDomain.getPrimaryEmail(), userInDomain);
							log.info("User suspended: {}", userInDomain.getPrimaryEmail());
							usersSuspended++;
						}
					}

				}

			}

		} else {

			// domain is empty, add all Perun users
			for (User user : users) {
				if (!user.getSuspended()) {
					insertUser(user);
					log.info("User created: {}", user.getPrimaryEmail());
					usersInserted++;
				} else {
					log.warn("User not created - is in suspended state: {}", user.getPrimaryEmail());
				}
			}

		}

	}

	@Override
	public void processGroups(List<Group> groups) throws GoogleGroupsIOException, InterruptedException {

		Groups dg = getDomainGroups(domainName);
		if (dg != null && !dg.isEmpty() && dg.getGroups() != null && !dg.getGroups().isEmpty()) {

			// domain is not empty, compare state
			List<Group> domainGroups = new ArrayList<>(dg.getGroups());

			for (Group group : groups) {
				Group domainGroup = null;
				for (Group groupInDomain : domainGroups) {
					if (Objects.equals(groupInDomain.getEmail(), group.getEmail())) {
						domainGroup = groupInDomain;
						break;
					}
				}

				if (domainGroup == null) {

					// not in domain - create group
					insertGroup(group);
					log.info("Group created: {}", group.getEmail());
					groupsInserted++;

					// FIXME - We must wait before asking for members of newly created groups
					Thread.sleep(2000);
					// handle group members
					processGroupMembers(group);

				} else {

					// already in domain - update group name

					// normalize group names - empty strings to nulls
					if (domainGroup.getName() != null && domainGroup.getName().isEmpty()) {
						domainGroup.setName(null);
					}
					if (group.getName() != null && group.getName().isEmpty()) {
						group.setName(null);
					}

					if (!Objects.equals(domainGroup.getName(), group.getName())) {

						updateGroup(domainGroup.getEmail(), group);
						log.info("Group updated: {}", group.getEmail());
						groupsUpdated++;

					} else {
						log.info("Group skipped: {}", group.getEmail());
					}

					// handle group members
					boolean changed = processGroupMembers(group);
					if (changed) groupsUpdatedMembers++;

				}

			}

			// check groups for removal
			for (Group groupInDomain : domainGroups) {
				boolean isInPerun = false;
				for (Group group : groups) {
					if (Objects.equals(groupInDomain.getEmail(), group.getEmail())) {
						isInPerun = true;
						break;
					}
				}

				if (!isInPerun) {
					deleteGroup(groupInDomain.getEmail());
					log.info("Group deleted: {}", groupInDomain.getEmail());
					groupsDeleted++;
				}

			}

		} else {

			// domain is empty - add all Perun groups
			for (Group group : groups) {

				insertGroup(group);
				log.info("Group created: {}", group.getEmail());
				groupsInserted++;
				// FIXME - We must wait before asking for members of newly created groups
				Thread.sleep(2000);
				processGroupMembers(group);

			}

		}

	}

	@Override
	public boolean processGroupMembers(Group group) throws GoogleGroupsIOException {

		boolean changed = false;

		// check, what kind of user/member identifier is used in groups file (by config)
		String memberIdType = properties.getProperty("member_identifier", "id");

		if (!Objects.equals("id", memberIdType) &&
				!Objects.equals("email", memberIdType)) {
			log.warn("Type of member id must be one of 'id' or 'email', but was: {}. Falling back to: {}", memberIdType, "id");
			memberIdType = "id";
		}

		Members dgm = getGroupsMembers(group.getEmail());
		if (dgm != null && !dgm.isEmpty() && dgm.getMembers() != null && !dgm.getMembers().isEmpty()) {

			// domain group is not empty, compare state
			List<Member> domainGroupMembers = new ArrayList<>(dgm.getMembers());

			for (String perunMemberId : groupsMembers.get(group.getEmail())) {

				Member domainGroupMember = null;
				for (Member memberOfGroupInDomain : domainGroupMembers) {
					// compare ID or Email
					if (Objects.equals((("id".equals(memberIdType)) ? memberOfGroupInDomain.getId() : memberOfGroupInDomain.getEmail()), perunMemberId)) {
						domainGroupMember = memberOfGroupInDomain;
						break;
					}
				}

				if (domainGroupMember == null) {

					// not in group in domain - add member
					Member member = new Member();
					if (Objects.equals("id", memberIdType)) {
						member.setId(perunMemberId);
						insertMember(group.getEmail(), member);
						log.info("Member: {} inserted to Group: {}", member.getId(), group.getEmail());
					} else {
						member.setEmail(perunMemberId);
						insertMember(group.getEmail(), member);
						log.info("Member: {} inserted to Group: {}", member.getEmail(), group.getEmail());
					}
					changed = true;

				} else {

					// we do not update Member object in groups

				}

			}

			// check members for removal
			for (Member memberInGroup: domainGroupMembers) {
				String domainMemberId = (("id".equals(memberIdType)) ? memberInGroup.getId() : memberInGroup.getEmail());
				boolean isInPerun = false;
				for (String perunMemberId : groupsMembers.get(group.getEmail())) {
					// Compare ID of Email
					if (Objects.equals(domainMemberId, perunMemberId)) {
						isInPerun = true;
						break;
					}
				}

				if (!isInPerun) {
					deleteMember(group.getEmail(), domainMemberId);
					log.info("Member: {} deleted from Group: {}", domainMemberId, group.getEmail());
					changed = true;
				}

			}

		} else {

			List<String> perunMembers = groupsMembers.get(group.getEmail());
			for (String memberId : perunMembers) {

				Member member = new Member();
				if (Objects.equals("id", memberIdType)) {
					member.setId(memberId);
					insertMember(group.getEmail(), member);
					log.info("Member: {} inserted to Group: {}", member.getId(), group.getEmail());
				} else {
					member.setEmail(memberId);
					insertMember(group.getEmail(), member);
					log.info("Member: {} inserted to Group: {}", member.getEmail(), group.getEmail());
				}
				changed = true;

			}

		}

		return changed;

	}

	/**
	 * Return List of Groups in domain.
	 *
	 * @return List of all domain groups.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private Groups getDomainGroups(String domainName) throws GoogleGroupsIOException {
		try {
			log.debug("Listing Groups from Domain: {}", domainName);
			Groups groups = service.groups().list().setDomain(domainName).execute();
			// fill list of users by next page
			boolean next = (groups.getNextPageToken() != null);
			while (next) {
				Groups groups2 = service.groups().list().setDomain(domainName).setPageToken(groups.getNextPageToken()).execute();
				groups.getGroups().addAll(groups2.getGroups());
				groups.setNextPageToken(groups2.getNextPageToken());
				next = (groups2.getNextPageToken() != null);
			}
			return groups;
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while getting groups from domain " + domainName + " in Google Groups", ex);
		}
	}

	/**
	 * Insert new group to your domain.
	 *
	 * @param group Group to be inserted
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void insertGroup(Group group) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.groups().insert(group).execute();
			log.debug("Creating group: {}", group);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while inserting group " + group.getEmail() + " to Google Groups", ex);
		}
	}

	/**
	 * Delete Group by mail from your domain.
	 *
	 * @param email Email of the Group to delete.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void deleteGroup(String email) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.groups().delete(email).execute();
			log.debug("Deleting group: {}", email);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while deleting group " + email + " from Google Groups", ex);
		}
	}

	/**
	 * Update group in your domain.
	 *
	 * @param groupKey Unique group identifier (email).
	 * @param group Group to be updated
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void updateGroup(String groupKey, Group group) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.groups().update(groupKey, group).execute();
			log.debug("Updating group: {}", group);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while updating group " + group.getEmail() + " in Google Groups", ex);
		}
	}

	/**
	 * Return List of Users in domain.
	 *
	 * @param domainName Name of domain to get all Users for
	 * @return List of all domain users.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private Users getDomainUsers(String domainName) throws GoogleGroupsIOException {
		try {
			log.debug("Listing Users from Domain: {}", domainName);
			Users users = service.users().list().setDomain(domainName).setMaxResults(500).setOrderBy("email").execute();
			// fill list of users by next page
			boolean next = (users.getNextPageToken() != null);
			while (next) {
				Users users2 = service.users().list().setDomain(domainName).setMaxResults(500).setOrderBy("email").setPageToken(users.getNextPageToken()).execute();
				users.getUsers().addAll(users2.getUsers());
				users.setNextPageToken(users2.getNextPageToken());
				next = (users.getNextPageToken() != null);
			}

			return users;
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while getting users from domain " + domainName + " in Google Groups", ex);
		}
	}

	/**
	 * Insert new user to your domain.
	 *
	 * @param user User to be created in your domain.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void insertUser(User user) throws GoogleGroupsIOException {
		try {

			// give users random passwords needed for creation
			char[] possibleCharacters = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}\\|;:'\",<.>/?").toCharArray();
			String randomStr = RandomStringUtils.random( 40, 0, possibleCharacters.length-1, false, false, possibleCharacters, new SecureRandom());
			user.setPassword(randomStr);

			if (!dryRun) service.users().insert(user).execute();
			log.debug("Creating user: {}", user);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while inserting user " + user.getPrimaryEmail() + " to Google Groups", ex);
		}
	}

	/**
	 * Delete User from your domain by key (email).
	 *
	 * @param userKey Email to delete user by.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void deleteUser(String userKey) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.users().delete(userKey).execute();
			log.debug("Deleting user: {}", userKey);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while deleting user " + userKey + " from Google Groups", ex);
		}
	}

	/**
	 * Update user in your domain.
	 *
	 * @param userKey Key to identify User to update
	 * @param user User with updated properties
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void updateUser(String userKey, User user) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.users().update(userKey, user).execute();
			log.debug("Updating user: {}", user);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while updating user " + user.getPrimaryEmail() + " in Google Groups", ex);
		}
	}

	/**
	 * Return List of groups Members.
	 *
	 * @param groupName Name of group to get members
	 * @return List of all group Members.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private Members getGroupsMembers(String groupName) throws GoogleGroupsIOException {
		try {
			log.debug("Listing Members of Group: {}", groupName);
			Members members = service.members().list(groupName).execute();
			// fill list of members by next page
			boolean next = (members.getNextPageToken() != null);
			while (next) {
				Members members2 = service.members().list(groupName).setPageToken(members.getNextPageToken()).execute();
				members.getMembers().addAll(members2.getMembers());
				members.setNextPageToken(members2.getNextPageToken());
				next = (members2.getNextPageToken() != null);
			}
			return members;
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while getting members of group " + groupName + " in Google Groups", ex);
		}
	}

	/**
	 * Insert Member to the group in your domain.
	 *
	 * @param groupName Group to have member inserted.
	 * @param member Member to be inserted.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void insertMember(String groupName, Member member) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.members().insert(groupName, member).execute();
			String memberIdType = properties.getProperty("member_identifier", "id");

			if (Objects.equals("id", memberIdType)) {
				log.debug("Inserting member: {} to group: {}", member.getId(), groupName);
			} else {
				log.debug("Inserting member: {} to group: {}", member.getEmail(), groupName);
			}

		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while inserting member " + member.getEmail() + " into group " + groupName + " in Google Groups", ex);
		}
	}

	/**
	 * Delete Member from the group in your domain.
	 *
	 * @param groupName Group to have member deleted.
	 * @param memberId Member to be deleted.
	 * @throws GoogleGroupsIOException  When API call fails.
	 */
	private void deleteMember(String groupName, String memberId) throws GoogleGroupsIOException {
		try {
			if (!dryRun) service.members().delete(groupName, memberId).execute();
			log.debug("Deleting member: {} from group: {}", memberId, groupName);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while deleting member with ID " + memberId + " from group " + groupName + " in Google Groups", ex);
		}
	}


	@Override
	public void processTeamDrives(Map<TeamDrive, List<User>> driveWithMembers) throws GoogleGroupsIOException, InterruptedException {

		TeamDriveList dd = getTeamDrives();

		if (dd != null && !dd.isEmpty() && dd.getTeamDrives() != null && !dd.getTeamDrives().isEmpty()) {

			// domain is not empty, compare state
			List<TeamDrive> domainDrives = new ArrayList<>(dd.getTeamDrives());

			// create team drives, process users permissions

			for (Map.Entry<TeamDrive, List<User>> dm : driveWithMembers.entrySet()) {

				TeamDrive domainDrive = null;
				for (TeamDrive td : domainDrives) {
					if (Objects.equals(td.getName(), dm.getKey().getName())) {
						domainDrive = td;
						break;
					}
				}

				if (domainDrive == null) {

					// drive not in domain, create
					insertTeamDrive(dm.getKey());
					log.info("TeamDrive created: {}", dm.getKey().getName());
					teamDrivesInserted++;
					// put back for update permissions
					domainDrive = dm.getKey();

				}

				// update permission for both new and existing TeamDrives
				// use domainDrive object, since it contains ID !!
				// FIXME - We must wait before asking for users of newly created team drive
				Thread.sleep(2000);
				processTeamDrivePermissions(domainDrive, dm.getValue());

			}

			// delete team drives
			TeamDrive perunDrive = null;
			for (TeamDrive domainDrive : domainDrives) {
				for (TeamDrive teamDrive : driveWithMembers.keySet()) {
					if (Objects.equals(teamDrive.getName(), domainDrive.getName())) {
						perunDrive = teamDrive;
						break;
					}
				}

				if (perunDrive == null) {

					if (Boolean.getBoolean(properties.getProperty("allow_delete_teamdrive", "false"))) {
						// delete domain drive (not found from Perun)
						deleteTeamDrive(domainDrive);
						teamDrivesDeleted++;
					} else {
						// keep teamdrive but remove all permissions
						processTeamDrivePermissions(domainDrive, new ArrayList<>());
					}

				}
			}

		} else {

			// domain is empty, add all drives
			for (Map.Entry<TeamDrive, List<User>> dm : driveWithMembers.entrySet()) {
				// create new teamDrive
				insertTeamDrive(dm.getKey());
				log.info("TeamDrive created: {}", dm.getKey().getName());
				teamDrivesInserted++;

				// FIXME - We must wait before asking for users of newly created team drive
				Thread.sleep(2000);
				// handle team drive users
				processTeamDrivePermissions(dm.getKey(), dm.getValue());

			}

		}

	}

	@Override
	public void processTeamDrivePermissions(TeamDrive teamDrive, List<User> users) throws GoogleGroupsIOException {

		List<Permission> permissions = getPermissions(teamDrive).getPermissions();

		if (permissions != null && !permissions.isEmpty()) {

			// add new permissions
			for (User user : users) {
				boolean notInDrive = true;
				for (Permission permission : permissions) {
					if (StringUtils.equalsIgnoreCase(user.getPrimaryEmail(), permission.getEmailAddress())) {
						notInDrive = false;
						break;
					}
				}

				if (notInDrive) {
					insertPermission(teamDrive, user);
					teamDriveUsersAdded++;
				}
			}

			// remove old permission
			for (Permission permission : permissions) {
				boolean notInPerun = true;
				for (User user : users) {
					if (StringUtils.equalsIgnoreCase(user.getPrimaryEmail(), permission.getEmailAddress())) {
						notInPerun = false;
						break;
					}
				}

				// remove missing user -> never remove service-account permission
				if (notInPerun && !StringUtils.equalsIgnoreCase(GoogleGroupsConnectionImpl.USER_EMAIL, permission.getEmailAddress())) {
					deletePermission(teamDrive, permission);
					teamDriveUsersDeleted++;
				}
			}

		} else {

			// permissions are empty - insert all from Perun
			for (User user : users) {
				insertPermission(teamDrive, user);
				teamDriveUsersAdded++;
			}

		}

	}

	/**
	 * Return TeamDriveList of existing team drives.
	 *
	 * @return TeamDriveList of all existing team drives.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private TeamDriveList getTeamDrives() throws GoogleGroupsIOException {

		try {
			log.debug("Listing existing TeamDrives from Domain: {}", domainName);
			TeamDriveList teamDriveList = driveService.teamdrives().list()
					.setFields("nextPageToken, teamDrives(id, name)")
					.setUseDomainAdminAccess(true)
					.execute();
			// fill list of users by next page
			boolean next = (teamDriveList.getNextPageToken() != null);
			while (next) {
				TeamDriveList teamDriveList2 = driveService.teamdrives().list()
						.setFields("nextPageToken, teamDrives(id, name)")
						.setUseDomainAdminAccess(true)
						.setPageToken(teamDriveList.getNextPageToken())
						.execute();

				teamDriveList.getTeamDrives().addAll(teamDriveList2.getTeamDrives());
				teamDriveList.setNextPageToken(teamDriveList2.getNextPageToken());
				next = (teamDriveList2.getNextPageToken() != null);
			}

			return teamDriveList;

		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while getting all team drives", ex);
		}

	}

	/**
	 * Return TeamDrive permissions
	 *
	 * @param teamDrive TeamDrive to get permissions for
	 * @return PermissionList of all existing permissions for selected Drive.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private PermissionList getPermissions(TeamDrive teamDrive) throws GoogleGroupsIOException {

		try {
			log.debug("Listing existing TeamDrives Permissions from Domain: {}", domainName);
			PermissionList permissionList = driveService.permissions().list(teamDrive.getId())
					.setFields("kind, nextPageToken, permissions(id, type, role, emailAddress)")
					.setSupportsTeamDrives(true)
					.setUseDomainAdminAccess(true)
					.execute();
			// fill list of users by next page
			boolean next = (permissionList.getNextPageToken() != null);
			while (next) {
				PermissionList permissionList2 = driveService.permissions().list(teamDrive.getId())
						.setFields("kind, nextPageToken, permissions(id, type, role, emailAddress)")
						.setUseDomainAdminAccess(true)
						.setSupportsTeamDrives(true)
						.setPageToken(permissionList.getNextPageToken())
						.execute();

				permissionList.getPermissions().addAll(permissionList2.getPermissions());
				permissionList.setNextPageToken(permissionList2.getNextPageToken());
				next = (permissionList2.getNextPageToken() != null);
			}
			return permissionList;

		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while getting permissions for TeamDrive.", ex);
		}

	}

	/**
	 * Insert new team drive.
	 *
	 * @param teamDrive drive to be created.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void insertTeamDrive(TeamDrive teamDrive) throws GoogleGroupsIOException {
		try {
			// since original object contains members, we pick only name param
			TeamDrive teamDriveMetaData = new TeamDrive();
			teamDriveMetaData.setName(teamDrive.getName());
			String requestId = UUID.randomUUID().toString();
			if (!dryRun) {
				TeamDrive returnedTeamDrive = driveService.teamdrives().create(requestId, teamDriveMetaData).execute();
				// push back new object IDs so we can
				teamDrive.setId(returnedTeamDrive.getId());
			}
			log.debug("Creating TeamDrive: {}", teamDrive);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while inserting new team drive", ex);
		}
	}

	/**
	 * Delete existing team drive.
	 *
	 * @param teamDrive drive to be deleted.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void deleteTeamDrive(TeamDrive teamDrive) throws GoogleGroupsIOException {
		try {
			String key = teamDrive.getId();
			if (!dryRun) driveService.teamdrives().delete(teamDrive.getId());
			log.debug("Deleting TeamDrive: {} ", key);
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while deleting team drive", ex);
		}
	}

	/**
	 * Insert new TeamDrive Permission
	 *
	 * @param teamDrive drive to have permission created
	 * @param user to have permission created
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void insertPermission(TeamDrive teamDrive, User user) throws GoogleGroupsIOException {

		Permission newOrganizerPermission = new Permission()
				.setType("user")
				.setRole("organizer")
				.setEmailAddress(user.getPrimaryEmail());

		try {
			if (!dryRun) {
				Permission result = driveService.permissions()
						.create(teamDrive.getId(), newOrganizerPermission)
						.setUseDomainAdminAccess(true)
						.setSupportsTeamDrives(true)
						.setFields("id")
						.execute();
				log.debug("Creating TeamDrive Permission: {} ", result);
			} else {
				log.debug("Creating TeamDrive Permission: {} ", newOrganizerPermission);
			}
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while creating new permission: " + newOrganizerPermission, ex);
		}

	}

	/**
	 * Delete existing Permission on TeamDrive.
	 *
	 * @param permission permission to be deleted.
	 * @throws GoogleGroupsIOException When API call fails.
	 */
	private void deletePermission(TeamDrive teamDrive, Permission permission) throws GoogleGroupsIOException {
		try {
			if (!dryRun) {
				driveService.permissions().delete(teamDrive.getId(), permission.getId())
						.setUseDomainAdminAccess(true)
						.setSupportsTeamDrives(true)
						.execute();
			}
			log.debug("Deleting TeamDrive Permission: {} ", permission.getId());
		} catch (IOException ex) {
			throw new GoogleGroupsIOException("Something went wrong while deleting team drive permission", ex);
		}
	}

	/**
	 * Create custom CSVReader for the passed FileReader, where separator is ';'
	 *
	 * @param fileReader file to be read
	 * @return CSVReader
	 */
	private CSVReader createCSVReader(FileReader fileReader) {

		char separator = ';';
		CSVReaderBuilder csvReaderBuilder = new CSVReaderBuilder(fileReader);
		CSVParserBuilder csvParserBuilder = new CSVParserBuilder();
		csvParserBuilder.withSeparator(separator);
		csvReaderBuilder.withCSVParser(csvParserBuilder.build());
		return csvReaderBuilder.build();

	}

}
