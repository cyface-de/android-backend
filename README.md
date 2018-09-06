Cyface Android SDK
==================

This project contains the Cyface Android SDK which is used by Cyface applications to capture data on Android devices.

Setup
-----

Geo location tracks such as those captured by the Cyface Android SDK, should only be transmitted via a secure HTTPS connection.
To make this possible the SDK requires a truststore containing the key of the server you are transmitting to.
Since we can not know which public key your server uses, this must be provided by you.
To do so place a truststore containing your key in

    src/main/res/raw/truststore.jks

Known Issues
------------

Problem that still exists is that you need to set the same provider when
creating a DataCapturingService as well as in your manifest. The
manifest also is required to override the default content provider as
declared by the persistence project. This needs to be done by each using
application separately.
