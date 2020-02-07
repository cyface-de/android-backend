Cyface Android SDK 5.0.0-beta1 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

- [Integration Changes](#integration-changes)
- [API Changes](#api-changes)
- [Developer Changes](#developer-changes)


Integration Changes
---------------------

This section was newly added as this library is now published to the Github Package Registry.

To use it as a dependency in your app you need to:

1. Make sure you are authenticated to the repository:

    * You need a Github account with read-access to this Github repository
    * Create a [personal access token on Github](https://github.com/settings/tokens) with "read:packages" permissions
    * Create or adjust a `local.properties` file in the project root containing:

    ```
    github.user=YOUR_USERNAME
    github.token=YOUR_ACCESS_TOKEN
    ```

    * Add the custom repository to your app's `build.gradle`:

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
    dependencies {
        # To use the 'movebis' flavour, use: 'datacapturingMovebis' 
        implementation "de.cyface:datacapturing:$cyfaceBackendVersion"
        # To use the 'movebis' flavour, use: 'synchronizationMovebis'
        implementation "de.cyface:synchronization:$cyfaceBackendVersion"
        # There is only one 'persistence' flavor
        implementation "de.cyface:persistence:$cyfaceBackendVersion"
    }
    ```

3. Set the `$cyfaceBackendVersion` gradle variable to the [latest version](https://github.com/cyface-de/android-backend/releases).


API Changes
---------------------------

Upgrading from 4.2.3:

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

### Resource Files

#### Truststore

*No API changes.*

#### Content Provider Authority

*No API changes.*

### Service Initialization

#### Implement Data Capturing Listener

*No API changes*

#### Implement UI Listener

*No API changes.*

#### Implement Event Handling Strategy

*No API changes*

##### Custom Capturing Notification

*No API changes.*

#### Start Service

*No API changes.*

#### Reconnect to Service

*No API changes.*

#### Link your Login Activity

*No API changes.*

#### Start WifiSurveyor

*No API changes.*

#### De-/Register JWT Auth Tokens

*No API changes.*

#### Start/Stop UI Location Updates

*No API changes.*

### Control Capturing

#### Start/Stop Capturing

The class `Vehicle` required in the `MovebisDataCapturingService.start()` method
is renamed to `Modality` as this describes the data collected more precisely.

```java
public class DataCapturingButton implements DataCapturingListener {
    public void onClick(View view) {
        
        // Before
        dataCapturingService.start(Vehicle.BICYCLE, new StartUpFinishedHandler(context.getPackageName()) {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                // Your logic, e.g.:
                setButtonStatus(button, true);
            }
        });
        
        // Now
        dataCapturingService.start(Modality.BICYCLE, new StartUpFinishedHandler(context.getPackageName()) {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                // Your logic, e.g.:
                setButtonStatus(button, true);
            }
        });
    }
}
```

#### Pause/Resume Capturing

The method `dataCapturingService.pause(finishedHandler)` now does not throw a `DataCapturingException` anymore. 

### Access Measurements

*No API changes.*

#### Load Finished Measurements

*No API changes.*

#### Load Tracks

*No API changes.*

#### Load Measurement Distance

*No API changes.*

#### Delete Measurements

*No API changes.*

#### Load Events

The `loadEvents()` method returns a chronologically ordered list of `Event`s.

These Events log `Measurement` related interactions of the user, e.g.:

Until now there were only:

- EventType.LIFECYCLE_START, EventType.LIFECYCLE_PAUSE, EventType.LIFECYCLE_RESUME, EventType.LIFECYCLE_STOP
  whenever a user starts, pauses, resumes or stops the Measurement.
  
We added the following EventType:
  
- EventType.MODALITY_TYPE_CHANGE at the start of a Measurement to define the Modality used in the Measurement
  and when the user selects a new `Modality` type during an ongoing (or paused) Measurement.
  The later is logged when `persistenceLayer.changeModalityType(Modality newModality)` is called with a different Modality than the current one.
  
- The `Event` class now contains a `getValue()` attribute which contains the `newModality`
  in case of a `EventType.MODALITY_TYPE_CHANGE` or else `Null`

```java
class measurementControlOrAccessClass {
    void loadEvents() {
        
        // Still supported:
        // To retrieve all Events of that Measurement
        //noinspection UnusedAssignment
        List<Event> events = persistence.loadEvents(measurementId);
        
        // Newly added:
        // To retrieve only the Events of a specific EventType
        events = persistence.loadEvents(measurementId, EventType.MODALITY_TYPE_CHANGE);
        
        //noinspection StatementWithEmptyBody
        if (events.size() > 0 ) {
            // your logic
        }
    }
}
```

Developer Changes
---------------------------

This section was newly added and is only relevant for developers of this library.

### Release a new version

To release a new version:

1. Create a new branch following the format `release/x.y.z/PRJ-<Number>_some-optional-explanation`. 
Where `x.y.z` is the number of the new version following semantic versioning, `PRJ` is the project this release has been created for, `<Number>` is the issue in the task tracker created for this release.
You may also add an optional human readable explanation.
2. Increase version numbers in `build.gradle`.
3. Commit and push everything to Github.
4. Create Pull Requests to master and dev branches.
5. If those Pull Requests are accepted merge them back, but make sure, you are still based on the most recent versions of master and dev.
6. Create a tag with the version on the merged master branch and push that tag to the repository.
7. Make sure the new version is successfully publish by the [Github Actions](https://github.com/cyface-de/android-backend/actions/new) to the [Github Registry](https://github.com/cyface-de/android-backend/packages).
8. Mark the released version as 'new Release' on [Github](https://github.com/cyface-de/data-collector/releases).


In case you need to publish _manually_ to the Github Registry:

1. Make sure you are authenticated to the repository:

    * You need a Github account with write-access to this Github repository 
    * Create a [personal access token on Github](https://github.com/settings/tokens) with "write:packages" permissions
    * Create or adjust a `local.properties` file in the project root containing:

    ```
    github.user=YOUR_USERNAME
    github.token=YOUR_ACCESS_TOKEN
    ```

2. Execute the publish command `./gradlew publishAll`