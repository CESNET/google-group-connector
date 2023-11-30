# G Suite connector

![](https://github.com/CESNET/google-group-connector/workflows/test%20build/badge.svg)

This repository contains java application, used by [Perun](https://perun-aai.org) to connect to [G Suite](https://gsuite.google.com/) service and synchronize users and groups from Perun to your domain in G Suite. 

You can also use this application without Perun. You just need to make necessary setup and pass input file in expected format.

Sources of the Perun are located in own [repository](https://github.com/CESNET/perun).

## Requirements

* Java >= 17
* Maven >= 3.6.x
* G suite account

## Configuration

### Set your G suite account

Firstly, generate your service account email in your Developers Console according to [this guide](https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount).

Secondly, generate your P12 key in your Developers Console.

Thirdly, set your API Scopes according to [this guide](https://developers.google.com/identity/protocols/OAuth2ServiceAccount#delegatingauthority) to be able to access Google Groups data via service account. Otherwise, you will get `Insufficient Permission Exception`.

If you wish to use Drive API, please enable it in developer console. Just adding scopes is not sufficient.

### Create necessary files

Properties file is necessary for successful execution of the application. Name your properties file according to your domain name. E.g. if your domain name is **domain.org** then your properties file would be named like this:

* `touch /etc/perun/google_groups-domain.org.properties`

After creating properties file, add following properties in it: 

* `service_account_email` is an email generated in Developers Console
* `user_email` is an email address of your google apps account
* `service_account_pkcs12_file_path` is a path to your generated P12 key. It's very important to name your P12 file according to your domain name (e.g. if your domain name is **domain.org**, then your P12 file will be named `client_secret_pkcs12-domain.org.p12` and move it to the folder `/etc/perun/`. 
* `scopes` list of scopes allowed for your service account necessary to perform expected changes
* `member_identifier` type of identifier you use in input files to identifier user
* `allow_delete` true/false value determine, if users missing in input file are deleted from domain or just suspended
* `allow_delete_teamdrive` true/false value determine, if TeamDrive is deleted when missing in input file or just all permissions are removed
* `dry_run` true/false value determine, if this tool performs only READ or READ/WRITE operations with Gooogle API. If true, WRITE actions are logged, but not actually performed.

Your properties file should look like this:

```bash
service_account_email=[SERVICE_ACCOUNT_EMAIL]
user_email=[GOOGLE_APPS_ACCOUNT_EMAIL]
service_account_pkcs12_file_path=/etc/perun/name_of_your_p12_file
scopes=https://www.googleapis.com/auth/admin.directory.group,https://www.googleapis.com/auth/admin.directory.orgunit,https://www.googleapis.com/auth/admin.directory.user,https://www.googleapis.com/auth/groups,https://www.googleapis.com/auth/userinfo.email
member_identifier=id
allow_delete=false
dry_run=false
```

## Usage

Based on desired action, you must prepare CSV file splitted by `;` containing either domain users, groups and their members or teamDrives and their users. You will then pass it to the main class as its argument. 

#### Users.csv example

Format is: `primaryMail;givenName;FamilyName;suspended flag`

```bash
user1@domain.org;User;One;
user2@domain.org;User;Two;
user3@domain.org;User;Three;suspended
user4@domain.org;User;Four;
```

New users will have random password generated, so different type of authentication must be provided for your domain - e.g. using Shibboleth IdP.
You can mark users as suspended to suspend them in G Suite. Existing user name is updated if changed. 

Domain users missing in input file are suspended by default, but you can allow deletion by setting `allow_delete=true` in properties file.

#### Groups.csv example

Format is: `email;name;members_identifiers`

```bash
group1@domain.org;Group One;user1@domain.org,user2@domain.org,user3@domain.org
group2@domain.org;Group Two;
group3@domain.org;Group Three;user4@domain.org
```

Groups are identified by their mail. Name of Group is updated if changed. Group members are updated if changed.
Based on properties file config Member identifier is either primaryMail or ID.

For managing users and groups together using 'email' as Member identifier is suggested, since this connector doesn't retrieve User IDs back from G Suite.
For managing only groups (filled with public google accounts outside your domain) you can use IDs as provided by their Google Identity registered in Perun.

#### TeamDrives.csv example

Format is: `name; primaryMails`

```bash
TeamDriveNameOne; user1@domain.org,user2@domain.org
TeamDriveNameTwo;
```

TeamDrives are identified by their name. TeamDrive users are updated if changed.
User identifier is just primaryMail. Every User has equal permission with full access as organizer.

For managing users in teamDrives is enough to update input file. For new Users will be created new Permission.
Users missing in input file are suspended by default.
TeamDrives missing in input file are suspended by default.


#### Execution

Main class of the application needs input arguments for successful execution. 
First argument is your domain name. 
Second argument is type of action: "users", "groups" or "teamDrives".
Third argument is path to CSV file (users, groups or teamDrives - depending on action)

```
java -jar ./google-group-connector-2.0.2.jar DOMAIN ACTION PATH_TO_CSV_FILE
```

By default, application logs to console. You can change default logging by passing own logback configuration.

```$xslt
java -Dlogback.configurationFile=file:///etc/perun/logback-google-groups.xml -jar ./google-group-connector-2.0.2.jar DOMAIN ACTION PATH_TO_CSV_FILE
```
