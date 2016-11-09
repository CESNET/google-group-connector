package cz.metacentrum.perun.core.googlegroupconnector;

import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import com.google.api.services.admin.directory.model.Member;
import com.google.api.services.admin.directory.model.Members;
import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Class representing google_groups service for Perun.
 * It work with GoogleGroupsConnection, which prepares connection to Google Groups via API
 * and then handles propagation of changes from Perun to Google Groups (insert/delete entries);
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 * @date 29.7.2015
 */
public class GoogleGroupsService {

    private final static org.slf4j.Logger log = LoggerFactory.getLogger(GoogleGroupsService.class);
    private static Directory service;
    private String domainName;
    private String groupName;
    private List groups;
    private List membersInPerun;

    public void compareAndPropagateData(File file) {

        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
            if (fileReader == null) {
                throw new FileNotFoundException("File was not found!");
            }
            char separator = ' ';
            CSVReader reader = new CSVReader(fileReader, separator);
            String[] row;

            // first row of file contains all group names
            row = reader.readNext();
            if (row == null || row.length <= 0) {
                log.error("File contains no rows.");
                throw new IllegalArgumentException("File contains no rows.");
            } else {
                groups = new ArrayList<>(Arrays.asList(row));

                /* extracts domain name from group's name (everything after '@')
                 and compares with user's domain from properties */
                this.compareDomain(row[0].substring(row[0].indexOf("@") + 1));
                this.compareGroups();
            }

            // all other rows contain groups' members
            while ((row = reader.readNext()) != null) {
                this.groupName = row[0];

                this.membersInPerun = new ArrayList<>();
                for (int i = 1; i < row.length; i++) {
                    this.membersInPerun.add(row[i]);
                }
                this.compareMembers();
            }
        } catch (FileNotFoundException ex) {
            log.error("File was not found in compareAndPropagateData(File file).", ex);
        } catch (IOException ex) {
            log.error("Problem with I/O operation while reading lines of file by FileReader.readNext() or getting file.", ex);
        } finally {
            try {
                fileReader.close();
            } catch (IOException ex) {
                log.error("Problem with I/O operation while closing file.", ex);
            }
        }
    }

    /**
     * Checks if the domain name is the same in Perun as in the Google Groups
     *
     * @param domainNameInFile name of the domain in Perun
     */
    private void compareDomain(String domainNameInFile) {
        if (domainNameInFile == null) {
            throw new IllegalArgumentException("Row in the file from Perun contains incorrect element - group without domain name.");
        }

        if (!domainName.equals(domainNameInFile)) {
            log.error("Domain name in Perun is different from user's domain name in Google Groups.", new IllegalArgumentException());
        }
    }

    /**
     * Checks if there are any groups in domain or not.
     *
     * - If there's no group, then we add everything from Perun to Google
     * Groups.
     *
     * - If domain is not empty, then we need to compare this group(s) with
     * group(s) in Perun and also group(s) in Perun compare with group(s) in
     * Google Groups.
     *
     * @throws IOException
     */
    private void compareGroups() throws IOException {
        try {
            Groups result = service.groups().list().setDomain(domainName).execute();
            if (result.isEmpty()) {
                String msg = "Google Groups returned no groups.";
                log.error(msg);
                throw new NullPointerException(msg);
            }

            List<Group> groupsInDomain = result.getGroups();

            if (groupsInDomain != null && !groupsInDomain.isEmpty()) {

                // check if there's any group that needs to be added to domain
                for (Object groupInPerun : groups) {
                    boolean isInDomain = false;

                    for (Group groupInDomain : groupsInDomain) {
                        if (groupInDomain.getEmail().equals(((String) groupInPerun).toLowerCase())) {
                            isInDomain = true;
                        }
                    }

                    if (!isInDomain) {
                        Group addedGroup = new Group();
                        addedGroup.setEmail(((String) groupInPerun).toLowerCase());
                        service.groups().insert(addedGroup).execute();
                    }
                }

                // check if there's any group that needs to be removed from domain
                for (Group groupInDomain : groupsInDomain) {
                    boolean shouldBeRemoved = true;

                    for (Object groupInPerun : groups) {
                        if (groupInDomain.getEmail().equals(((String) groupInPerun).toLowerCase())) {
                            shouldBeRemoved = false;
                        }
                    }

                    // group was not found in Perun
                    if (shouldBeRemoved) {
                        service.groups().delete(groupInDomain.getEmail()).execute();
                    }
                }
            } else {
                for (Object groupInPerun : groups) {
                    String groupMailInPerun = ((String) groupInPerun).toLowerCase();

                    Group addedGroup = new Group();
                    addedGroup.setEmail(groupMailInPerun);
                    service.groups().insert(addedGroup).execute();
                }
            }
        } catch (IOException ex) {
            throw new IOException("Something went wrong while inserting/deleting/getting group from Google Groups", ex);
        }
    }

    /**
     * Checks if there are any members in group or not.
     *
     * - If there's no member, then we add everyone from Perun's group to group
     * in Google Groups.
     *
     * - If group is not empty, then we need to compare this member(s) with
     * member(s) in Perun and also member(s) in Perun compare with member(s) in
     * group from Google Groups.
     * 
     * @throws IOException
     */
    private void compareMembers() throws IOException {
        try {
            Members result = service.members().list(groupName).execute();
            if (result.isEmpty()) {
                String msg = "Google Groups returned no groups.";
                log.error(msg);
                throw new NullPointerException(msg);
            }

            List<Member> membersInGroup = result.getMembers();

            if (membersInGroup != null && !membersInGroup.isEmpty()) {
                // check if there's any member that needs to be added to group
                for (Object memberInPerun : membersInPerun) {
                    boolean isInGroup = false;

                    for (Member memberInGroup : membersInGroup) {
                        if (memberInGroup.getId().equals((String) memberInPerun)) {
                            isInGroup = true;
                        }
                    }

                    if (!isInGroup) {
                        String memberIdInPerun = (String) memberInPerun;

                        Member member = new Member();
                        member.setId(memberIdInPerun);

                        service.members().insert(groupName, member).execute();
                    }
                }

                // check if there's any member that needs to be removed from group
                for (Member memberInGroup : membersInGroup) {
                    boolean shouldBeRemoved = true;

                    for (Object memberInPerun : membersInPerun) {
                        if (memberInGroup.getId().equals((String) memberInPerun)) {
                            shouldBeRemoved = false;
                        }
                    }

                    // member was not found in Perun
                    if (shouldBeRemoved) {
                        service.members().delete(groupName, memberInGroup.getId()).execute();
                    }
                }
            } else {
                for (Object memberInPerun : membersInPerun) {
                    String memberIdInPPerun = (String) memberInPerun;

                    Member member = new Member();
                    member.setId(memberIdInPPerun);

                    service.members().insert(groupName, member).execute();
                }
            }
        } catch (IOException ex) {
            throw new IOException("Something went wrong while inserting/deleting/getting group from Google Groups", ex);
        }
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        String filePath = null;
        String domainFile = "/etc/perun/google_groups-";

        if (args.length > 1) {
            filePath = args[0];
            domainFile = domainFile + args[1] + ".properties";
        } else {
            throw new IllegalArgumentException("Main class has wrong number of input arguments (less than 2).");
        }

        if (filePath == null || filePath.isEmpty()) {
            log.error("File path is empty.");
            throw new IllegalArgumentException("File path can't be empty.");
        }

        File inputFile = new File(filePath);
        GoogleGroupsConnection connection = new GoogleGroupsConnection(domainFile);
        service = connection.getDirectoryService();
        GoogleGroupsService session = new GoogleGroupsService();
        session.domainName = connection.getDomainName();
        session.compareAndPropagateData(inputFile);
    }
}
