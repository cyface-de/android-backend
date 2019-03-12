# Cyface Android SDK 4.0.0-alpha2 Migration Guide

This migration guide is written for apps using the `MovebisDataCapturinService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

## Upgrading from 4.0.0-alpha1

If you want to migrate from an earlier version please start with the [previous migration guide](./migration-guide_4.0.0-alpha1.md).

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

*No API changes.*

#### Start Service

*No API changes.*

#### Reconnect to Service

*No API changes.*

#### De-/Register JWT Auth Tokens

*No API changes.*

#### Start/Stop UI Location Updates

*No API changes.*

### Control Capturing

#### Start/Stop Capturing

*No API changes.*

#### Pause/Resume Capturing

*No API changes.*

### Access Measurements

*No API changes.*

#### Load Finished Measurements

*No API changes.*

#### Load Tracks

In the last version we returned the full track as the first sub list.
Now we implemented the "slicing" of the track into sub `Track`s (sliced before resume events). 
I.e. the `loadTracks()` method now returns a list of chronologically ordered `Track`s, each containing chronologically ordered `GeoLocation`s.

Use the `PersistenceLayer` to access data, see [Access Measurements](#access-measurements).

```java
class measurementControlOrAccessClass {
    void loadTrack() {
        
        // Version 4.0.0-alpha1
        List<List<GeoLocation>> tracks = persistence.loadTrack(measurementId);
        if (tracks.size() > 0 ) {
            List<GeoLocation> fullTrack = tracks.get(0);
        }
        
        // Version 4.0.0-alpha2
        List<Track> tracks = persistence.loadTracks(measurementId);
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