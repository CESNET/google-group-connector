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

Properties file is necessary for successful execution of the application:

* `touch /etc/perun/google_groups.properties`

After creating properties file, add two properties in it: `service_account_email` is an email generated in Developers Console and `user_email` is an email address of your google apps account. Your properties file should look like this:

```bash
service_account_email=[SERVICE_ACCOUNT_EMAIL]
user_email=[GOOGLE_APPS_ACCOUNT_EMAIL]
```

Move your P12 key to `/etc/perun` to have this location:

* `/etc/perun/client_secret_pkcs12.p12`

Create your input file, that you will pass to the main class as an argument. First line in the file has to contain list of all group email addresses, separated by single whitespace. All other lines in the file have to contain group email address with its members' ids, all separated by single whitespace too. Something like this:

```bash
groupName1@domain.name.com groupName2@domain.name.com groupName3@domain.name.com
groupName1@domain.name.com member1ID member2ID
groupName2@domain.name.com member1ID
groupName3@domain.name.com member2ID member3ID
```

##Example

Main class of the application needs input argument for successful execution. This argument should be path to file containing all groups and its members, as described above. You need to run something like this (with JAVA_HOME explicitely set):

`mvn "-Dexec.args=-classpath %classpath cz.metacentrum.perun.core.googlegroupconnector.GoogleGroupsService PATH_TO_FILE" -Dexec.executable="$JAVA_HOME"/bin/java org.codehaus.mojo:exec-maven-plugin:1.2.1:exec`