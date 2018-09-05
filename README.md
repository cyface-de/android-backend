Cyface Android SDK
========================

This project contains the Cyface Android SDK which is used by Cyface applications to capture data on Android devices.

Known Issues
------------

Problem that still exists is that you need to set the same provider when
creating a DataCapturingService as well as in your manifest. The
manifest also is required to override the default content provider as
declared by the persistence project. This needs to be done by each using
application separately.

How to build the SDK
-------------------------
* build project with Build Variant CyfaceFullRelease
* copy the following aar files to your app's "./libs" folder:
 * persistence/build/outputs/aar/persistence-cyface-full-release.aar
 * datacapturing/build/outputs/aar/datacapturing-cyface-full-release.aar
 * synchronization/build/outputs/aar/synchronization-cyface-full-release.aar
* For more details see: https://stackoverflow.com/a/27565045/5815054