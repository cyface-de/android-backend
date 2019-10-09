Cyface Android SDK 5.0.0-beta1 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

Upgrading from 4.2.3
-------------------------------

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