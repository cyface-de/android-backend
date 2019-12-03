Cyface Android SDK
========================

This project contains the Cyface Android SDK which is used by Cyface applications to capture data on Android devices.

- [Integration Guide](#integration-guide)
- [API Usage Guide](#api-usage-guide)
- [Migration Guide](#migration-guide)
- [Developer Guide](#developer-guide)
- [License](#license)


Integration Guide
---------------------

This library is published to the Github Package Registry.

To use it as a dependency you need to:

1. Make sure you are authenticated to the repository:

    * You need a Github account with read-access to this Github repository
    * Create a [personal access token on Github](https://github.com/settings/tokens) with "read:packages" permissions
    * Create or adjust a `local.properties` file in the project root containing:

    ```
    github.user=YOUR_USERNAME
    github.token=YOUR_ACCESS_TOKEN
    ```

    * Add the custom repository to your `build.gradle`:

    ``` 
    def properties = new Properties()
    properties.load(new FileInputStream("local.properties"))

    repositories {
        // Other maven repositories, e.g.:
        jcenter()
        google()
        // Repository for this library
        maven {
            url = uri("https://maven.pkg.github.com/cyface-de/android-backend")
            credentials {
                username = properties.getProperty("github.user")
                password = properties.getProperty("github.token")
            }
        }
    }
    ```
    
2. Add this package as a dependency to your app's `build.gradle`:

    ```
    # Attention: If you require another flavor like "movebis" use 'datacapturingMovebis', etc.!
    
    dependencies {
        implementation "de.cyface:datacapturing:$cyfaceBackendVersion"
        implementation "de.cyface:synchronization:$cyfaceBackendVersion"
        implementation "de.cyface:persistence:$cyfaceBackendVersion"
    }
    ```

3. Set the `$cyfaceBackendVersion` gradle variable to the [latest version](https://github.com/cyface-de/android-backend/releases). 


API Usage Guide
---------------------------

- [Resource Files](#resource-files)
    - [Truststore](#truststore)
    - [Content Provider Authority](#content-provider-authority)
- [Service Initialization](#service-initialization)
	- [Implement Data Capturing Listener](#implement-data-capturing-listener)
	- [Implement UI Listener](#implement-ui-listener)
	- [Implement Event Handling Strategy](#implement-event-handling-strategy)
	    - [Custom Capturing Notification](#custom-capturing-notification)
	- [Start Service](#start-service)
	- [Reconnect to Service](#reconnect-to-service)
	- [Link your Login Activity](#link-your-login-activity)
	- [Start WifiSurveyor](#start-wifisurveyor)
	- [De-/Register JWT Auth Tokens](#de-register-jwt-auth-tokens)
	- [Start/Stop UI Location Updates](#startstop-ui-location-updates)
- [Control Capturing](#control-capturing)
	- [Start/Stop Capturing](#startstop-capturing)
	- [Pause/Resume Capturing](#pauseresume-capturing)
- [Access Measurements](#access-measurements)
	- [Load finished measurements](#load-finished-measurements)
	- [Load Tracks](#load-tracks)
	- [Load Measurement Distance (new feature)](#load-measurement-distance)
	- [Delete Measurements](#delete-measurements)
	- [Load Events](#load-events)
- [Documentation Incomplete](#documentation-incomplete)

### Resource Files

The following steps are required before you can start coding.

#### Truststore

Geo location tracks such as those captured by the Cyface Android SDK, should only be transmitted
via a secure HTTPS connection.

If you use a self-signed certificate the SDK requires a truststore containing the key of the server
you are transmitting to.

Since we can not know which public key your server uses, this must be provided by you.
To do so place a truststore containing your key in:

    synchronization/src/main/res/raw/truststore.jks

If this (by default empty) file is not replaced, the SDK can only communicate with
servers which are certified by one of its trusted Certification Authorities.

#### Content Provider Authority

You need to set a provider and to make sure you use the same provider everywhere:

* The `AndroidManifest.xml` is required to override the default content provider as
declared by the persistence project. This needs to be done by each SDK integrating
application separately.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="your.domain.app"> <!-- replace this! -->

    <application>
        <!-- This overwrites the provider in the SDK. This way the app can
        be installed next to other SDK using apps.
        The "authorities" must match the one in your AndroidManifest.xml! -->
        <provider
            android:name="de.cyface.persistence.MeasuringPointsContentProvider"
            android:authorities="your.domain.app.provider"
            android:exported="false"
            android:process=":persistence_process"
            android:syncable="true"
            tools:replace="android:authorities" />
    </application>

</manifest>
```

* Define your authority which you must use as parameter in `new Cyface/MovebisDataCapturingService()` (see sample below). 
  This must be the same as defined in the `AndroidManifest.xml` above.
  
```java
public class Constants {
    public final static String AUTHORITY = "your.domain.app.provider"; // replace this
}
```

* Create a resource file `src/main/res/xml/sync_adapter.xml` and use the same provider:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<sync-adapter xmlns:android="http://schemas.android.com/apk/res/android"
    android:contentAuthority="your.domain.app.provider"
    android:accountType="your.domain.app"
    android:userVisible="false"
    android:supportsUploading="true"
    android:allowParallelSyncs="false"
    android:isAlwaysSyncable="true" />
```

### Service Initialization

The core of our SDK is the `DataCapturingService` which controls the capturing process.

We provide two interfaces for this service: `CyfaceDataCapturingService` and `MovebisDataCapturingService`.
Unless you are part of the *Movebis project* `CyfaceDataCapturingService` is your candidate.

To keep this documentation lightweight, we currently only use `MovebisDataCapturingService` in the samples
but the interface for `CyfaceDataCapturingService` is mostly the same.

The following steps are required to communicate with this service.

#### Implement Data Capturing Listener

This interface informs your app about data capturing events. Implement the interface to update your UI on those events.

Please use `dataCapturingService.loadCurrentlyCapturedMeasurement()` instead of `persistenceLayer.loadCurrentlyCapturedMeasurement()`
when you need to load (update) the measurement data for the currently captured measurement very frequently
(like here: on each location update) as this uses a cache to reduce database access.

Here is a basic example implementation.

```java
class DataCapturingListenerImpl implements DataCapturingListener {
    
    @Override
    public void onNewGeoLocationAcquired(GeoLocation geoLocation) {
        
        // To identify invalid ("unclean") location, check geoLocation.isValid()
        
        // Load updated measurement distance
        final Measurement measurement;
        try {
            measurement = dataCapturingService.loadCurrentlyCapturedMeasurement();
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        
        final double distanceMeter = measurement.getDistance();
        // Your logic, e.g. update the UI with the current distance
    }
    
    // The other interface methods
}
```

#### Implement UI Listener

This is only required for `MovebisDataCapturingService`.

#### Implement Event Handling Strategy

This interface allows us to inject your custom strategies into our SDK.

##### Custom Capturing Notification

To continuously run an Android service, without the system killing said service,
it needs to show a notification to the user in the Android status bar.

The Cyface data capturing runs as such a service and thus needs to display such a notification.
Applications using the Cyface SDK may configure style and behaviour of this notification by
providing an implementation of `de.cyface.datacapturing.EventHandlingStrategy` to the constructor
of the `de.cyface.datacapturing.DataCapturingService`.

An example implementation is provided by `de.cyface.datacapturing.IgnoreEventsStrategy`.
The most important step is to implement the method
`de.cyface.datacapturing.EventHandlingStrategy#buildCapturingNotification(DataCapturingBackgroundService)`. 

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
* If you want to use a **vector xml drawable as Notification icon** make sure to do the following:

  Even with `vectorDrawables.useSupportLibrary` enabled the vector drawable won't work as a notification icon (`notificationBuilder.setSmallIcon()`)
  on devices with API < 21. We assume that's because of the way we need to inject your custom notification.
  A simple fix is to have the xml in `res/drawable-anydpi-v21/icon.xml` and to generate notification icon PNGs under the same resource name in the usual paths (`res/drawable-**dpi/icon.png`). 
  
#### Start Service

To save resources your should create your service when the view is created
and reuse this instance when you need to communicate with it.

```java
class MainFragment extends Fragment {
    
    private MovebisDataCapturingService dataCapturingService;
    
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        
        final static int SENSOR_FREQUENCY = 100;
        dataCapturingService = new MovebisDataCapturingService(context, dataUploadServerAddress,
            uiListener, locationUpdateRate, eventHandlingStrategy, capturingListener, SENSOR_FREQUENCY);
        
        // dataCapturingButton is our sample DataCapturingListenerImpl
        // Depending on your implementation you need to register the DataCapturingService:
        this.dataCapturingButton.setDataCapturingService(dataCapturingService);
    }
}
```

#### Reconnect to Service

When your UI resumes you need to reconnect to your service:

The `reconnect()` method returns true when there was a capturing running during reconnect.
This way we can use the `isRunning()` result from within `reconnect()` and avoid duplicate
`isRunning()` calls which saves time. 

```java
public class DataCapturingButton implements DataCapturingListener {
    
    PersistenceLayer<DefaultPersistenceBehaviour> persistence =
        new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
    
    public void onResume(@NonNull final CyfaceDataCapturingService dataCapturingService) {
        
        if (dataCapturingService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
            // Your logic, e.g.:
            setButtonStatus(button, true);
        } else {
            // Attention: reconnect() only returns true if there is an OPEN measurement
            // To check for PAUSED measurements use the persistence layer.
            persistence.loadMeasurements(MeasurementStatus.PAUSED);
            // Your logic, e.g.:
            setButtonStatus(button, false);
        }
    }
}
```

#### Link your Login Activity

This is only required for `CyfaceDataCapturingService`. 

Define which Activity should be launched to request the user to log in: 

```java
public class CustomApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;
    }
}
```

#### Start WifiSurveyor

This is only required for `CyfaceDataCapturingService`.

Create an account for synchronization and start `WifiSurveyor`:

```java
public class MainFragment extends Fragment implements ConnectionStatusListener {
    
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        try {
            // dataCapturingService = ... - see above
            
            // Needs to be called after new CyfaceDataCapturingService()
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
        
        // Login via LoginActivity, create account and using dynamic tokens
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

#### De-/Register JWT Auth Tokens

This is only required for `MovebisDataCapturingService`.

#### Start/Stop UI Location Updates

This is only required for `MovebisDataCapturingService`.

### Control Capturing

Now you can actually use the `DataCapturingService` instance to capture data.

#### Start/Stop Capturing

To capture a measurement you need to start the capturing and stop it after some time:

```java
public class DataCapturingButton implements DataCapturingListener {
    public void onClick(View view) {
        dataCapturingService.start(Modality.BICYCLE, new StartUpFinishedHandler(context.getPackageName()) {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                // Your logic, e.g.:
                setButtonStatus(button, true);
            }
        });
        // or
        dataCapturingService.stop(new ShutDownFinishedHandler() {
            @Override
            public void shutDownFinished(final long measurementIdentifier) {
                // Your logic, e.g.:
                setButtonStatus(button, false);
                setButtonEnabled(button);
            }
        });
    }
}
```

#### Pause/Resume Capturing

If you want to pause and continue a measurement you can use:

```java
public class DataCapturingButton implements DataCapturingListener {
    public void onClick(View view) {
        dataCapturingService.pause(finishedHandler);
        // or
        dataCapturingService.resume(finishedHandler);
    }
}
```

### Access Measurements

You now need to use the `PersistenceLayer` to access and control captured *measurement data*. 

```java
class measurementControlOrAccessClass {
    
    PersistenceLayer<DefaultPersistenceBehaviour> persistence =
        new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
}
```

* Use `persistenceLayer.loadMeasurement(mid)` to load a specific measurement 
* Use `loadMeasurements()` or `loadMeasurements(MeasurementStatus)` to load multiple measurements (of a specific state)

Loaded `Measurement`s contain details, e.g. the [Measurement Distance](#load-measurement-distance).

**Attention:** The attributes of a Measurement which is not yet finished change
over time so you need to make sure you reload it.
You can find an example for this in [Implement Data Capturing Listener](#implement-data-capturing-listener).

#### Load Finished Measurements

Finished measurements are measurements which are stopped (i.e. not paused or ongoing).

```java
class measurementControlOrAccessClass {
    void loadMeasurements() {
    
        persistence.loadMeasurements(MeasurementStatus.FINISHED);
    }
}
```

#### Load Tracks

The `loadTracks()` method returns a chronologically ordered list of `Track`s.

Each time a measurement is paused and resumed, a new `Track` is started for the same measurement.

A `Track` contains the chronologically ordered `GeoLocation`s captured.

You can ether load the raw track or a "cleaned" version of it. See the `DefaultLocationCleaningStrategy` class for details.

```java
class measurementControlOrAccessClass {
    void loadTrack() {
        
        // Raw track:
        List<Track> tracks = persistence.loadTracks(measurementId);
        
        // or, "cleaned" track:
        List<Track> tracks = persistence.loadTracks(measurementId, new DefaultLocationCleaningStrategy());
        
        //noinspection StatementWithEmptyBody
        if (tracks.size() > 0 ) {
            // your logic
        }
    }
}
```

#### Load Measurement Distance

To display the distance for an ongoing measurement (which is updated about once per second) you need to call
`dataCapturingService.loadCurrentlyCapturedMeasurement()` regularly, e.g. on each location update to always have the most recent information.

For this you need to implement the `DataCapturingListener` interface to be notified on `onNewGeoLocationAcquired(GeoLocation)` events.

See [Implement Data Capturing Listener](#implement-data-capturing-listener) for sample code.

#### Delete Measurements

To delete the measurement data stored on the device for finished or synchronized measurements use:

```java
class measurementControlOrAccessClass {
    
    void deleteMeasurement(final long measurementId) throws CursorIsNullException {
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

#### Load Events

The `loadEvents()` method returns a chronologically ordered list of `Event`s.

These Events log `Measurement` related interactions of the user, e.g.:

- EventType.LIFECYCLE_START, EventType.LIFECYCLE_PAUSE, EventType.LIFECYCLE_RESUME, EventType.LIFECYCLE_STOP
  whenever a user starts, pauses, resumes or stops the Measurement.
  
- EventType.MODALITY_TYPE_CHANGE at the start of a Measurement to define the Modality used in the Measurement
  and when the user selects a new `Modality` type during an ongoing (or paused) Measurement.
  The later is logged when `persistenceLayer.changeModalityType(Modality newModality)` is called with a different Modality than the current one.
  
- The `Event` class contains a `getValue()` attribute which contains the `newModality`
  in case of a `EventType.MODALITY_TYPE_CHANGE` or else `Null`

```java
class measurementControlOrAccessClass {
    void loadEvents() {
        
        // To retrieve all Events of that Measurement
        //noinspection UnusedAssignment
        List<Event> events = persistence.loadEvents(measurementId);
        
        // Or to retrieve only the Events of a specific EventType
        events = persistence.loadEvents(measurementId, EventType.MODALITY_TYPE_CHANGE);
        
        //noinspection StatementWithEmptyBody
        if (events.size() > 0 ) {
            // your logic
        }
    }
}
```

### Documentation Incomplete

This documentation still lacks of samples for the following features:

* ErrorHandler
* Force Synchronization
* ConnectionStatusListener
* Disable synchronization
* Enable synchronization on metered connections
* The synchronization talks to a [Cyface Data Collector](https://github.com/cyface-de/data-collector) 


Migration Guide
--------------------------------

 - [Migrate to 4.1.0](documentation/migration-guide_4.1.0.md)
 - [Migrate to 5.0.0-beta1](documentation/migration-guide_5.0.0-beta1.md)
 - TODO: migrate to 5.0.0-beta2


Developer Guide
---------------------------

### Release a new version

This library is published to the Github Package Registry.

To publish a new version you need to:

1. Make sure you are authenticated to the repository:

    * You need a Github account with write-access to this Github repository 
    * Create a [personal access token on Github](https://github.com/settings/tokens) with "write:packages" permissions
    * Create or adjust a `local.properties` file in the project root containing:

    ```
    github.user=YOUR_USERNAME
    github.token=YOUR_ACCESS_TOKEN
    ```

2. Publish a new version

    * Increment the `build.gradle`'s `ext.version`
    * Execute the publish command `./gradlew publishAll`


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
