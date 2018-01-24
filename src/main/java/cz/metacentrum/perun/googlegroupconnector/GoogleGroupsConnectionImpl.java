package cz.metacentrum.perun.googlegroupconnector;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;

import com.google.api.services.admin.directory.Directory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.google.api.services.drive.Drive;
import org.slf4j.LoggerFactory;


/**
 * GoogleGroupsConnectionImpl is an implementation of GoogleGroupsConnection
 * interface.
 *
 * This class handles connection to Google Groups using their API and sets
 * suitable scope for the application.
 *
 * In order to be able to work with this class you need to have
 * google_groups.properties file on the expected place with service account
 * email and user email filled in.
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 * @date 29.7.2015
 */
public class GoogleGroupsConnectionImpl implements GoogleGroupsConnection {

	private static String PROPERTIES_PATH;
	private static final String APPLICATION_NAME = "Google Groups Perun Service";
	private static JsonFactory JSON_FACTORY;
	private static HttpTransport HTTP_TRANSPORT;
	private static String SERVICE_ACCOUNT_EMAIL;

	// email of the User that Application will work behalf on.
	private static String USER_EMAIL;

	// scopes required by the application
	private static List<String> SCOPES;

	//Generated at https://console.developers.google.com/project according to
	// https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount
	private static String SERVICE_ACCOUNT_PKCS12_FILE_PATH;

	private final static org.slf4j.Logger log = LoggerFactory.getLogger(GoogleGroupsConnectionImpl.class);

	private Properties prop = new Properties();

	public GoogleGroupsConnectionImpl(String domainFile) throws IOException, GeneralSecurityException {
		GoogleGroupsConnectionImpl.PROPERTIES_PATH = domainFile;
		loadProperties();
	}

	/**
	 * Build and returns a Directory service object authorized with the service
	 * accounts that act on behalf of the given user.
	 *
	 * @return Directory service object that is ready to make requests.
	 */
	@Override
	public Directory getDirectoryService() {
		return new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize()).setApplicationName(APPLICATION_NAME).build();
	}

	/**
	 * Returns variable containing domain name.
	 *
	 * @return DOMAIN_NAME name of the domain in Google Groups
	 */
	public String getDomainName() {
		return USER_EMAIL.substring(USER_EMAIL.indexOf("@") + 1);
	}

	@Override
	public Properties getProperties() {
		return prop;
	}

	/**
	 * Loads properties and sets static class variables.
	 */
	private void loadProperties() throws IOException, GeneralSecurityException {

		InputStream input = null;

		try {
			input = new FileInputStream(PROPERTIES_PATH);

			// load a properties file
			prop.load(input);

			// store values from properties file in static variables
			GoogleGroupsConnectionImpl.SERVICE_ACCOUNT_EMAIL = prop.getProperty("service_account_email");
			GoogleGroupsConnectionImpl.USER_EMAIL = prop.getProperty("user_email");
			GoogleGroupsConnectionImpl.SERVICE_ACCOUNT_PKCS12_FILE_PATH = prop.getProperty("service_account_pkcs12_file_path");
			GoogleGroupsConnectionImpl.JSON_FACTORY = JacksonFactory.getDefaultInstance();
			GoogleGroupsConnectionImpl.HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			GoogleGroupsConnectionImpl.SCOPES = Arrays.asList(prop.getProperty("scopes").split(","));

		} catch (IOException ex) {
			String msg = "Problem with I/O operation while reading google_groups.properties file.";
			log.error(msg, ex);
			throw new IOException(msg);
		} catch (GeneralSecurityException ex) {
			String msg = "Problem with general security while getting newTrustedTransport() for HttpTransport class.";
			log.error(msg, ex);
			throw new GeneralSecurityException(msg);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException ex) {
					log.error("Problem with I/O operation while closing file \'" + PROPERTIES_PATH + "\'.", ex);
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
			return new GoogleCredential.Builder()
					.setTransport(HTTP_TRANSPORT)
					.setJsonFactory(JSON_FACTORY)
					.setServiceAccountId(SERVICE_ACCOUNT_EMAIL)
					.setServiceAccountScopes(SCOPES)
					.setServiceAccountUser(USER_EMAIL)
					.setServiceAccountPrivateKeyFromP12File(new java.io.File(SERVICE_ACCOUNT_PKCS12_FILE_PATH))
					.build();
		} catch (IOException ex) {
			log.error("Problem with I/O operation while building GoogleCredential object in authorize() method.", ex);
		} catch (GeneralSecurityException ex) {
			log.error("Problem with security while building GoogleCredential object in authorize() method.", ex);
		}
		return null;
	}






	/**
	 * Creates an authorized Credential object.
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	/*
	New way of google authorization, for groups managements and for team drives management too.
	Eventually it will be necesseray to remake authorization. New authorization needs to register aplication
	or generate JSON file from developer console.

	import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
	import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
	import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
	import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

	import com.google.api.client.util.store.FileDataStoreFactory;

	import java.io.InputStreamReader;
	- needed imports

		<dependency>
			<groupId>com.google.oauth-client</groupId>
			<artifactId>google-oauth-client-jetty</artifactId>
			<version>1.11.0-beta</version>
		</dependency>
		- needed dependency to pom.xml for import com.google.api.client.extensions.*

		<dependency>
			<groupId>com.google.oauth-client</groupId>
			<artifactId>google-oauth-client</artifactId>
			<version>1.22.0</version>
		</dependency>
		- needed dependency to pom.xml for import com.google.api.client.googleapis.auth.ouath2



	private static final java.io.File DATA_STORE_DIR = new java.io.File(
		System.getProperty("user.home"), "/etc/perun/google_groups-einfra.cesnet.cz.properties.");

	// Global instance of the {@link FileDataStoreFactory}.
	private static FileDataStoreFactory DATA_STORE_FACTORY;
	-needed attributes for store credentials:

	static {
		try {
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			log.error("Problem with initialization of DATA_STORE_FACTORY", t);
		}
	}


	//class representing new google authorization
	private static Credential newGoogleAuthorize() throws IOException {
		// Load client secrets.
		InputStream in =
				GoogleGroupsConnectionImpl.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		try {
			GoogleAuthorizationCodeFlow flow =
					new GoogleAuthorizationCodeFlow.Builder(
							HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
							.setDataStoreFactory(DATA_STORE_FACTORY)
							.setAccessType("offline")
							.build();
			Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("perun@einfra.cesnet.cz");
			System.out.println(
					"Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
			return credential;
		} catch (Exception ex) {
			log.error("Problem with authorization",ex);
		}
		return null;
	}*/

	@Override
	public Drive getDriveService() throws IOException {
		Credential credential = authorize();
		return new Drive.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}


}
