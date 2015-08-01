package cz.metacentrum.perun.core.googlegroupconnector;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;

import com.google.api.services.admin.directory.DirectoryScopes;
import com.google.api.services.admin.directory.Directory;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.LoggerFactory;

/**
 * GoogleGroupsConnection class handles connection to Google Groups using their
 * API and sets suitable scope for the application.
 *
 * In order to be able to work with this class you need to have
 * google_groups.properties file on the expected place with service account
 * email and user email filled in.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 * @date 29.7.2015
 */
public class GoogleGroupsConnection {

    /**
     * Path to properties file.
     */
    private static final String PROPERTIES_PATH = "/etc/perun/google_groups.properties";

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Google Groups Perun Service";

    /**
     * Global instance of the JSON factory.
     */
    private static JsonFactory JSON_FACTORY;

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this application.
     */
    private static List<String> SCOPES;

    /**
     * Email of the Service Account.
     */
    private static String SERVICE_ACCOUNT_EMAIL;

    /**
     * Email of the User that Application will work behalf on.
     */
    private static String USER_EMAIL;

    /**
     * Path to the Service Account Private Key file.
     *
     * Generated at https://console.developers.google.com/project according to
     * https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount
     */
    private static final String SERVICE_ACCOUNT_PKCS12_FILE_PATH = "/etc/perun/client_secret_pkcs12.p12";

    private final static org.slf4j.Logger log = LoggerFactory.getLogger(GoogleGroupsConnection.class);

    public GoogleGroupsConnection() throws IOException, GeneralSecurityException {
        this.loadProperties();
    }

    /**
     * Returns variable containing domain name.
     *
     * @return DOMAIN_NAME name of the domain in Google Groups
     */
    public String getDomainName() {
        return USER_EMAIL.substring(USER_EMAIL.indexOf("@") + 1);
    }

    /**
     * Loads properties and sets static class variables.
     *
     */
    private void loadProperties() throws IOException, GeneralSecurityException {

        Properties prop = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream(PROPERTIES_PATH);

            // load a properties file
            prop.load(input);

            // store values from properties file in static variables
            GoogleGroupsConnection.SERVICE_ACCOUNT_EMAIL = prop.getProperty("service_account_email");
            GoogleGroupsConnection.USER_EMAIL = prop.getProperty("user_email");
            GoogleGroupsConnection.JSON_FACTORY = JacksonFactory.getDefaultInstance();
            GoogleGroupsConnection.HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            GoogleGroupsConnection.SCOPES = Arrays.asList(DirectoryScopes.ADMIN_DIRECTORY_USER,
                    DirectoryScopes.ADMIN_DIRECTORY_USER_READONLY,
                    DirectoryScopes.ADMIN_DIRECTORY_GROUP,
                    DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER);

        } catch (IOException ex) {
            String msg = "Problem with I/O operation while reading google_groups.properties file.";
            log.error(msg, ex);
            throw new IOException(msg);
        } catch (GeneralSecurityException ex) {
            String msg = "Something is wrong with general security while getting newTrustedTransport() for HttpTransport class.";
            log.error(msg, ex);
            throw new GeneralSecurityException(msg);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error("Problem with I/O operation while closing file.", ex);
                }
            }
        }
    }

    /**
     * Creates Credential object.
     *
     * @return an authorized Credential object.
     */
    private static Credential authorize() {

        try {
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(HTTP_TRANSPORT)
                    .setJsonFactory(JSON_FACTORY)
                    .setServiceAccountId(SERVICE_ACCOUNT_EMAIL)
                    .setServiceAccountScopes(SCOPES)
                    .setServiceAccountUser(USER_EMAIL)
                    .setServiceAccountPrivateKeyFromP12File(
                            new java.io.File(SERVICE_ACCOUNT_PKCS12_FILE_PATH))
                    .build();

            return credential;
        } catch (IOException ex) {
            log.error("Problem with I/O operation while building GoogleCredential object in authorize() method.", ex);
        } catch (GeneralSecurityException ex) {
            log.error("Problem with security while building GoogleCredential object in authorize() method.", ex);
        }

        return null;
    }

    /**
     * Build and returns a Directory service object authorized with the service
     * accounts that act on behalf of the given user.
     *
     * @return Directory service object that is ready to make requests.
     */
    public Directory getDirectoryService() {

        Directory service = new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize())
                .setApplicationName(APPLICATION_NAME).build();

        return service;
    }
}
