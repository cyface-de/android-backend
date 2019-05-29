Cyface Android SDK 4.1.0 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

Upgrading from 4.0.0
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

### Resource Files

#### Truststore

*No API changes.*

#### Content Provider Authority

*No API changes.*

### Service Initialization

#### Implement Data Capturing Listener

Please use `dataCapturingService.loadCurrentlyCapturedMeasurement()` instead of `persistenceLayer.loadCurrentlyCapturedMeasurement()`
when you need to load (update) the measurement data for the currently captured measurement very frequently
(like here: on each location update) as this uses a cache to reduce database access.

```java
class DataCapturingListenerImpl implements DataCapturingListener {
    
    @Override
    public void onNewGeoLocationAcquired(GeoLocation geoLocation) {
        
        // Load updated measurement distance
        final Measurement measurement;
        try {
            measurement = dataCapturingService.loadCurrentlyCapturedMeasurement();
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        
        final double distanceMeter = measurement.getDistance();
    }
    
    // The other interface methods
}
```

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

*No API changes.*

#### Pause/Resume Capturing

The method `dataCapturingService.pause(finishedHandler)` now does not throw a `DataCapturingException` anymore. 

### Access Measurements

*No API changes.*

#### Load Finished Measurements

*No API changes.*

#### Load Tracks

We did not change the existing API but added a method to load the "cleaned" track.

See the `DefaultLocationCleaningStrategy` class for details.

```java
class measurementControlOrAccessClass {
    void loadTrack() {
        
        // Version 4.0.0 only offered the raw track: 
        List<Track> tracks = persistence.loadTracks(measurementId);
        
        // Version 4.1.0 now also offers the "cleaned" track:
        List<Track> tracks = persistence.loadTracks(measurementId, new DefaultLocationCleaningStrategy());
        
        //noinspection StatementWithEmptyBody
        if (tracks.size() > 0 ) {
            // your logic
        }
    }
}
```

#### Load Measurement Distance

*No API changes.*

#### Delete Measurements

*No API changes.*