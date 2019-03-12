Cyface Android SDK
========================

This project contains the Cyface Android SDK which is used by Cyface applications to capture data on Android devices.

- [Setup](#setup)
- [Known Issues](#known-issues)
- [How to Integrate the SDK](#how-to-integrate-the-sdk)
	- [Provide a Custom Capturing Notification](#provide-a-custom-capturing-notification)
	- [Access Measurements via PersistenceLayer](#access-measurements-via-persistenceLayer)
	    - [Load Measurements](#load-measurements)
	    - [Delete Measurements](#delete-measurements)
	- [TODO: add code sample for the usage of:](#todo-add-code-sample-for-the-usage-of)
- [Migration from Earlier Versions](#migration-from-earlier-versions)
- [License](#license)

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

How to Integrate the SDK
--------------------------
* Define which Activity should be launched to request the user to log in 

```java
public class CustomApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;
    }
}
```

* Start the DataCapturingService to communicate with the SDK

```java
public class MainFragment extends Fragment {
    
    private CyfaceDataCapturingService dataCapturingService;
    
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
        final Bundle savedInstanceState) {
        dataCapturingService = new CyfaceDataCapturingService(/*...,*/
        customEventHandlingStrategy, capturingListener);
    }
}
```

or

```java

public class MainFragment extends Fragment {
    
    private MovebisDataCapturingService dataCapturingService;
    
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
        final Bundle savedInstanceState) {
        dataCapturingService = MovebisDataCapturingService(/*...,*/
        uiListener, locationUpdateRate, customEventHandlingStrategy, capturingListener);
    }
}
```

* Create an account for synchronization & start WifiSurveyor

```java
public class MainFragment extends Fragment implements ConnectionStatusListener {
    
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        try {
            // dataCapturingService = ... - see above
            
            // Needs to be called after DataCapturingService()
            startSynchronization(context);
            
            // If you want to receive events for the synchronization status
            dataCapturingService.addConnectionStatusListener(this);
        } catch (final SetupException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
    }
    
    @SuppressWarnings("WeakerAccess")
    public void startSynchronization(final Context context) {
        final AccountManager accountManager = AccountManager.get(context);
        final boolean validAccountExists = accountWithTokenExists(accountManager);
    
        if (validAccountExists) {
            try {
                dataCapturingService.startWifiSurveyor();
            } catch (SetupException e) {
                throw new IllegalStateException(e);
            }
            return;
        }
        
        // Login and create account 
        // a) Static token variant:
        dataCapturingService.registerJWTAuthToken(username, token);
        // or b) Login via LoginActivity and using dynamic tokens
        // The LoginActivity is called by Android which handles the account creation
        accountManager.addAccount(ACCOUNT_TYPE, AUTH_TOKEN_TYPE, null, null,
            getMainActivityFromContext(context), new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        // noinspection unused - this allows us to detect when LoginActivity is closed
                        final Bundle bundle = future.getResult();

                        // The LoginActivity created a temporary account which cannot yet be used for synchronization.
                        // As the login was successful we now register the account correctly:
                        final AccountManager accountManager = AccountManager.get(context);
                        final Account account = accountManager.getAccountsByType(ACCOUNT_TYPE)[0];
                        dataCapturingService.getWifiSurveyor().makeAccountSyncable(account, syncEnabledPreference);

                        dataCapturingService.startWifiSurveyor();
                    } catch (OperationCanceledException e) {
                        // This closes the app when the LoginActivity is closed
                        getMainActivityFromContext(context).finish();
                    } catch (AuthenticatorException | IOException | SetupException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }, null);
    }
    
    private static boolean accountWithTokenExists(final AccountManager accountManager) {
        final Account[] existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        Validate.isTrue(existingAccounts.length < 2, "More than one account exists.");
        return existingAccounts.length != 0
                && accountManager.peekAuthToken(existingAccounts[0], AUTH_TOKEN_TYPE) != null;
    }
}
```
          
* Register your DataCapturingListener implementation and control data capturing

```java
public class MainFragment extends Fragment {
    
    private DataCapturingButton dataCapturingButton;
    
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
        final Bundle savedInstanceState) {
        this.dataCapturingButton = new DataCapturingButton();
        
        // dataCapturingService = ... - see above
        
        this.dataCapturingButton.setDataCapturingService(dataCapturingService);
    }
}

public class DataCapturingButton implements AbstractButton, DataCapturingListener {
    
    @Override
    public void onClick(View view) {
        dataCapturingService.start(vehicle, new StartUpFinishedHandler(did) {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                setButtonStatus(button, true);
            }
        });
        // or
        dataCapturingService.stop(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished(final long measurementIdentifier) {
                setButtonStatus(button, false);
                setButtonEnabled(button);
            }
        });
    }
}
```

* To check if the capturing is running  

```java
public class DataCapturingButton implements AbstractButton, DataCapturingListener {
    
    public void onResume() {
        if (dataCapturingService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
            setButtonStatus(button, true);
        } else {
            setButtonStatus(button, false);
        }
    }
}
```

### Provide a Custom Capturing Notification
To continuously run an Android service, without the system killing said service, it needs to show a notification to the user in the Android status bar.
The Cyface data capturing runs as such a service and thus needs to display such a notification.
Applications using the Cyface SDK may configure style and behaviour of this notification by providing an implementation of `de.cyface.datacapturing.EventHandlingStrategy` to the constructor of the `de.cyface.datacapturing.DataCapturingService`.
An example implementation is provided by `de.cyface.datacapturing.IgnoreEventsStrategy`.
The most important step is to implement the method `de.cyface.datacapturing.EventHandlingStrategy#buildCapturingNotification(DataCapturingBackgroundService)`.

This can look like:

```java
public class EventHandlingStrategyImpl implements EventHandlingStrategy {
    
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
        .setSmallIcon(R.drawable.your_icon) // see "attention" notes below
        .setContentText("Running Data Capturing")
        .setOngoing(true)
        .setAutoCancel(false)
        .build();
    }
}
```

Further details about how to create a proper notification are available via the [Google developer documentation](https://developer.android.com/guide/topics/ui/notifiers/notifications).
The most likely adaptation an application using the Cyface SDK for Android should do, is use the `android.app.Notification.Builder.setContentIntent(PendingIntent)` to call the applications main activity if the user presses the notification.

**ATTENTION:**
* Service notifications require an application wide unique identifier.
  This identifier is 74.656.
  Due to limitations in the Android framework, this is not configurable.
  You must not use the same notification identifier for any other notification displayed by your app!
* If you want to use a **vector xml drawable as Notification icon** make sure to do the follwing:
  Even with `vectorDrawables.useSupportLibrary` enabled the vector drawable won't work as a notification icon (`notificationBuilder.setSmallIcon()`)
  on devices with API < 21. We assume that's because of the way we need to inject your custom notification.
  A simple fix is to have a the xml in `drawable-anydpi-v21/icon.xml` and to generate notification icon PNGs under the same name in the usual paths (`drawable-**dpi/icon.png`). 
  



### Access Measurements via PersistenceLayer
Use the `PersistenceLayer<DefaultPersistenceBehaviour>` to manage and load measurements as demonstrated in the sample code below.

* Use `persistenceLayer.loadMeasurement(mid)` to load a specific measurement 
* Use `loadMeasurements()` or `loadMeasurements(MeasurementStatus)` to load multiple measurements (of a specific state)
                                                              
#### Load Measurements
                                                              
**ATTENTION:** The attributes of `MeasurementStatus#OPEN` and `MeasurementStatus#PAUSED`
measurements are only valid in the moment they are loaded from the database. Changes
after this call are not pushed into the `Measurement` object returned by this call.

**Measurement distance**

To display the distance for an ongoing measurement (which is updated about once per second)
make sure to call `persistenceLayer.loadCurrentlyCapturedMeasurement()` *on each location
update* to always have the most recent information. For this you need to implement the `DataCapturingListener`
interface to be notified on `onNewGeoLocationAcquired(GeoLocation)` events.

**Load Track**

To display the track of a finished measurement use `persistenceLayer.loadTrack(measurementId)`
which returns a list of lists containing `GeoLocations`. Currently there is always just one
sub list which contains the full track. We'll change this soon so that tracks are sliced into sub tracks
when pause/resume was used which is the reason behind the return type. 

#### Delete Measurements

The following code snippet shows how to manage stored measurements:

```java
public class MeasurementOverviewFragment extends Fragment {
    
    private PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        persistenceLayer = new PersistenceLayer<>(inflater.getContext(), inflater.getContext().getContentResolver(),
                AUTHORITY, new DefaultPersistenceBehaviour());
    }
    
    private void deleteMeasurement(final long measurementId) throws CursorIsNullException {
        
        // To make sure you don't delete the ongoing measurement because this leads to an exception
        Measurement currentlyCapturedMeasurement;
        try {
            currentlyCapturedMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        } catch (NoSuchMeasurementException e) {
            // do nothing
        }
        
        if (currentlyCapturedMeasurement == null || currentlyCapturedMeasurement.getIdentifier() != measurementId) {
            new DeleteFromDBTask()
                    .execute(new DeleteFromDBTaskParams(persistenceLayer, this, measurementId));
        } else {
            Log.d(TAG, "Not deleting currently captured measurement: " + measurementId);
        }
    }

    private static class DeleteFromDBTaskParams {
        final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
        final long measurementId;

        DeleteFromDBTaskParams(final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer,
                final long measurementId) {
            this.persistenceLayer = persistenceLayer;
            this.measurementId = measurementId;
        }
    }

    private class DeleteFromDBTask extends AsyncTask<DeleteFromDBTaskParams, Void, Void> {
        protected Void doInBackground(final DeleteFromDBTaskParams... params) {
            final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer = params[0].persistenceLayer;
            final long measurementId = params[0].measurementId;
            persistenceLayer.delete(measurementId);
        }

        protected void onPostExecute(Void v) {
            // Your logic
        }
    }
}
```

### TODO: add code sample for the usage of:

* ErrorHandler
* Force Synchronization
* ConnectionStatusListener
* Disable synchronization
* Show Measurements and GeoLocationTraces
* Usage of Camera, Bluetooth

Migration from Earlier Versions
--------------------------------
 - [Migrate to 4.0.0-alpha2](documentation/migration-guide_4.0.0-alpha2.md)
 - [Migrate to 4.0.0-alpha1](documentation/migration-guide_4.0.0-alpha1.md)

License
-------------------
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
