# Cyface Android SDK 4.0.0-alpha1 Migration Guide

This migration guide is written for apps using the `MovebisDataCapturinService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

## Upgrading from 3.1

- [Service Initialization](#service-initialization)
	- [Implement Data Capturing Listener](#implement-data-capturing-listener)
	- [Implement UI Listener](#implement-ui-listener)
	- [Implement Event Handling Strategy](#implement-event-handling-strategy)
	- [Start Service](#start-service)
	- [Reconnect to Service](#reconnect-to-service)
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

### Service Initialization

#### Implement Data Capturing Listener

*No API changes.*

#### Implement UI Listener

*No API changes.*

#### Implement Event Handling Strategy

The API of the `EventHandlingStrategy` interface did not change but we added a new the feature
to get the measurement distance. To do so during an *ongoing* capturing you need to re-load
the distance when new `GeoLocation`s are captured: 

```java
class DataCapturingListenerImpl implements DataCapturingListener {

    PersistenceLayer<DefaultPersistenceBehaviour> persistence =
        new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
    
    @Override
    public void onNewGeoLocationAcquired(GeoLocation geoLocation) {
        
        // E.g.: load current measurement distance
        final Measurement measurement;
        try {
            measurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        // New in Version 3.2.0
        final double distanceMeter = measurement.getDistance();
    }
}
```

#### Start Service

The `DataCapturingListener` is now added only once in the `DataCapturingService` constructor instead of in each `start()` call.

```java
class MainFragment {
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        
        // Version 3.1.0
        dataCapturingService = new MovebisDataCapturingService(context, dataUploadServerAddress,
            uiListener, locationUpdateRate, eventHandlingStrategy);
        
        // Version 4.0.0-alpha1
        dataCapturingService = new MovebisDataCapturingService(context, dataUploadServerAddress,
            uiListener, locationUpdateRate, eventHandlingStrategy, capturingListener);
    }
}
```

#### Reconnect to Service

The `reconnect()` method now returns true when there was a capturing running during reconnect.
This way we can use the `isRunning()` result from within `reconnect()` and avoid duplicate `isRunning()` calls which saves time. 

```java
public class DataCapturingButton implements DataCapturingListener {
    
    PersistenceLayer<DefaultPersistenceBehaviour> persistence =
        new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
    
    public void onResume(@NonNull final CyfaceDataCapturingService dataCapturingService) {
        
        // Version 3.1.0
        dataCapturingService.reconnect();
        dataCapturingService.isRunning(timeout, callbackWithSuccessLogic);
        
        // Version 4.0.0-alpha1
        if (dataCapturingService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
            // your logic, e.g. update button
        } else {
            // Attention: reconnect() only returns true if there is an OPEN measurement
            // To check for PAUSED measurements use the persistence layer.
            persistence.loadMeasurements(MeasurementStatus.PAUSED); // your logic
        }
    }
}
```

#### De-/Register JWT Auth Tokens

*No API changes.*

#### Start/Stop UI Location Updates

*No API changes.*

### Control Capturing

#### Start/Stop Capturing

The `DataCapturingListener` is now added in the `MovebisDataCapturingService` constructor instead of in `start()`, see [Start Service](#start-service).

The `stop()` API did not change. 

```java
public class DataCapturingButton implements DataCapturingListener {
    public void onClick(View view) {
        
        // Version 3.1.0
        dataCapturingService.start(dataCapturingListener, vehicle, startUpFinishedHandler);
        
        // Version 4.0.0-alpha1
        dataCapturingService.start(vehicle, startUpFinishedHandler);
    }
}
```

#### Pause/Resume Capturing

*No API changes.*

### Access Measurements

You now need to use the `PersistenceLayer` to access and control measurement data as we removed the APIs from the `DataCapturingService`. 

```java
class measurementControlOrAccessClass {
    
    PersistenceLayer<DefaultPersistenceBehaviour> persistence =
        new PersistenceLayer<>(context, contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
}
```

Loaded `Measurement`s now contain more details than just their identifier, e.g. the [Measurement Distance](#load-measurement-distance).

**Attention:** The attributes of a Measurement which is not yet finished change over time so you need to make sure you reload it.
You can find an example for this in [Implement Data Capturing Listener](#implement-data-capturing-listener).

#### Load Finished Measurements

Use the `PersistenceLayer` to access data, see [Access Measurements](#access-measurements).

```java
class measurementControlOrAccessClass {
    void loadMeasurements() {
    
        // Version 3.1.0
        dataCapturingService.loadMeasurements(MeasurementStatus.FINISHED);
        
        // Version 4.0.0-alpha1
        persistence.loadMeasurements(MeasurementStatus.FINISHED);
    }
}
```

#### Load Tracks

The `loadTracks()` method now returns a list of lists containing `GeoLocation`s.
The reason behind this is that we soon slice the track into subtracks when pause and resume is used.
Until then we return the full track as the first sub list.

Use the `PersistenceLayer` to access data, see [Access Measurements](#access-measurements).

```java
class measurementControlOrAccessClass {
    void loadTrack() {
    
        // Version 3.1.0
        List<GeoLocation> track = dataCapturingService.loadTrack(dataCapturingService.loadMeasurement(measurementId));
        
        // Version 4.0.0-alpha1
        List<List<GeoLocation>> subTracks = persistence.loadTrack(measurementId);
        if (subTracks.size() > 0 ) {
            List<GeoLocation> track = subTracks.get(0);
        }
    }
}
```

#### Load Measurement Distance

This is a new feature added in 3.2.0.

See [Implement Data Capturing Listener](#implement-data-capturing-listener) for sample code.

Use the `PersistenceLayer` to access data, see [Access Measurements](#access-measurements). 

#### Delete Measurements

Use the `PersistenceLayer` to access data, see [Access Measurements](#access-measurements).

```java
class measurementControlOrAccessClass {
    void loadMeasurements() {
    
        // Version 3.1.0
        dataCapturingService.deleteMeasurement(dataCapturingService.loadMeasurement(measurementId));
        
        // Version 4.0.0-alpha1
        persistence.delete(measurementId);
    }
}
```