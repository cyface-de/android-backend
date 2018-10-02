Cyface Android SDK
========================

This project contains the Cyface Android SDK which is used by Cyface applications to capture data on Android devices.

Setup
-----

Geo location tracks such as those captured by the Cyface Android SDK, should only be transmitted via a secure HTTPS connection.
If you use a self-signed certificate the SDK requires a truststore containing the key of the server you are transmitting to.
Since we can not know which public key your server uses, this must be provided by you.
To do so place a truststore containing your key in

    synchronization/src/main/res/raw/truststore.jks

If this (by default empty) file is not replaced, the SDK can only communicate with servers which are certified by one its trusted Certification Authorities.

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

How to integrate the SDK
--------------------------
* Define which Activity should be launched to request the user to log in 

```
CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;
```

* Start the DataCapturingService to communicate with the SDK

```
CyfaceDataCapturingService cyfaceDataCapturingService = new CyfaceDataCapturingService(...,
AUTHORITY, ACCOUNT_TYPE, apiUrl, yourEventHandlingStrategyImplementation);
```

* Create an account for synchronization & start WifiSurveyor

```
createAccountIfNoneExists(...);

public void createAccountIfNoneExists(final Context context) {
    if (!accountManager.peekAuthToken(account, de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE) != null)) {
        cyfaceDataCapturingService.startWifiSurveyor();
        return;
    }

    accountManager.addAccount(ACCOUNT_TYPE, de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE, null,
            null, getMainActivityFromContext(context), new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    final Account account = accountManager.getAccountsByType(ACCOUNT_TYPE)[0];
                    ContentResolver.setIsSyncable(account, AUTHORITY, 1);
                    ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
                    cyfaceDataCapturingService.startWifiSurveyor();
                }
            }, null);
}
```
          
* Start / stop Capturing and register your implementation of a DataCapturingListener

```
cyfaceDataCapturingService.startAsync(dataCapturingListener, vehicle, new StartUpFinishedHandler() {...});
cyfaceDataCapturingService.stopAsync(new ShutDownFinishedHandler() {..});
```

* To check if the capturing is running  

```
cyfaceDataCapturingService.isRunning(TIMEOUT_IS_RUNNING_MS, TimeUnit.MILLISECONDS, new IsRunningCallback() {});
```

### TODO: add code sample for the usage of:

* ErrorHandler
* Force Synchronization
* ConnectionStatusListener
* Disable synchronization
* Show Measurements and GpsTraces
* Delete measurements manually
* Usage of Camera, Bluetooth