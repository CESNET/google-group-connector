# google-group-connector

This repository contains java application, used by [Perun](http://perun.cesnet.cz/web/) to connect to [Google Groups](https://support.google.com/groups/answer/46601?hl=en) and synchronize users from Perun to Google Groups. Sources of the Perun are located in another [repository](https://github.com/CESNET/perun).

##Requirements
* Java >= 1.6
* Maven >= 3.1.x
* Google Apps account

##Configuration
###Set your Google Apps account

Firstly, generate your service account email in your Developers Console according to [this guide](https://developers.google.com/identity/protocols/OAuth2ServiceAccount#creatinganaccount).

Secondly, generate your P12 key in your Developers Console.

Thirdly, set your API Scopes according to [this guide](https://developers.google.com/identity/protocols/OAuth2ServiceAccount#delegatingauthority) to be able to access Google Groups data via service account. Otherwise, you will get `Insufficient Permission Exception`.

###Create necessary files

Properties file is necessary for successful execution of the application. Name your properties file according to your domain name. E.g. if your domain name is **nonexisting.domain.org** then your properties file would be named like this:

* `touch /etc/perun/google_groups-nonexisting.domain.org.properties`

After creating properties file, add three properties in it: `service_account_email` is an email generated in Developers Console, `user_email` is an email address of your google apps account and `service_account_pkcs12_file_path` is a path to your generated P12 key. It's very important to name your P12 file according to your domain name (e.g. if your domain name is **nonexisting.domain.org**, then your P12 file will be named `client_secret_pkcs12-nonexisting.domain.org.p12` and move it to the folder `/etc/perun/`. Your properties file should look like this:

```bash
service_account_email=[SERVICE_ACCOUNT_EMAIL]
user_email=[GOOGLE_APPS_ACCOUNT_EMAIL]
service_account_pkcs12_file_path=/etc/perun/name_of_your_p12_file
```

Create your input file, that you will pass to the main class as its argument. First line in the file has to contain list of all group email addresses, separated by single whitespace. All other lines in the file have to contain group email address with its members' ids, all separated by single whitespace too. Something like this:

```bash
groupName1@nonexisting.domain.org groupName2@nonexisting.domain.org groupName3@nonexisting.domain.org
groupName1@nonexisting.domain.org member1ID member2ID
groupName2@nonexisting.domain.org member1ID
groupName3@nonexisting.domain.org member2ID member3ID
```

##Example

Main class of the application needs input arguments for successful execution. First argument should be path to file containing all groups and its members, as described above. Second argument should be name of the domain that you work with. You need to run something like this (with JAVA_HOME explicitely set):

`java -jar google-group-connector/GoogleGroupConnector-1.0-SNAPSHOT.jar "PATH_TO_FIRST_FILE" "nonexisting.domain.org"`
