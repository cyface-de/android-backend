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

How to integrate the SDK
--------------------------
* Define which Activity should be launched to request the user to log in 

```java
CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;
```

* Start the DataCapturingService to communicate with the SDK

```java
CyfaceDataCapturingService cyfaceDataCapturingService = new CyfaceDataCapturingService(...,
AUTHORITY, ACCOUNT_TYPE, apiUrl, yourEventHandlingStrategyImplementation);
```

* Create an account for synchronization & start WifiSurveyor

```java
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

```java
cyfaceDataCapturingService.start(dataCapturingListener, vehicle, new StartUpFinishedHandler() {...});
cyfaceDataCapturingService.stop(new ShutDownFinishedHandler() {..});
```

* To check if the capturing is running  

```java
cyfaceDataCapturingService.isRunning(TIMEOUT_IS_RUNNING_MS, TimeUnit.MILLISECONDS, new IsRunningCallback() {});
```

### Provide a custom Capturing Notification
To continuously run an Android service, without the system killing said service, it needs to show a notification to the user in the Android status bar.
The Cyface data capturing runs as such a service and thus needs to display such a notification.
Applications using the Cyface SDK may configure style and behaviour of this notification by providing an implementation of `de.cyface.datacapturing.EventHandlingStrategy` to the constructor of the `de.cyface.datacapturing.DataCapturingService`.
An example implementation is provided by `de.cyface.datacapturing.IgnoreEventsStrategy`.
The most important step is to implement the method `de.cyface.datacapturing.EventHandlingStrategy#buildCapturingNotification(DataCapturingBackgroundService)`.
This can look like:
```java
@Override
public @NonNull Notification buildCapturingNotification(final @NonNull DataCapturingBackgroundService context) {
  final String channelId = "channel";
  NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && notificationManager.getNotificationChannel(channelId)==null) {
    final NotificationChannel channel = new NotificationChannel(channelId, "Cyface Data Capturing", NotificationManager.IMPORTANCE_DEFAULT);
    notificationManager.createNotificationChannel(channel);
  }

  return new NotificationCompat.Builder(context, channelId)
    .setContentTitle("Cyface")
    .setContentText("Running Data Capturing")
    .setOngoing(true)
    .setAutoCancel(false)
    .build();
}
```

Further details about how to create a proper notification are available via the [Google developer documentation](https://developer.android.com/guide/topics/ui/notifiers/notifications).
The most likely adaptation an application using the Cyface SDK for Android should do, is use the `android.app.Notification.Builder.setContentIntent(PendingIntent)` to call the applications main activity if the user presses the notification.

**ATTENTION:** Service notifications require an application wide unique identifier.
This identifier is 74.656.
Due to limitations in the Android framework, this is not configurable.
You must not use the same notification identifier for any other notification displayed by your app!

### TODO: add code sample for the usage of:

* ErrorHandler
* Force Synchronization
* ConnectionStatusListener
* Disable synchronization
* Show Measurements and GeoLocationTraces
* Delete measurements manually
* Usage of Camera, Bluetooth

### License
Copyright 2017 Cyface GmbH

This file is part of the Cyface SDK for Android.

The Cyface SDK for Android is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

The Cyface SDK for Android is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.