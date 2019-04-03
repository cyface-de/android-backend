Cyface Android SDK 4.0.0-alpha2 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

Upgrading from 4.0.0-alpha1
-------------------------------

If you want to migrate from an earlier version please start with the [previous migration guide](./migration-guide_4.0.0-alpha1.md).

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

The API did not change but we updated the [README](../README.md) to clarify where exactly you need
to set the [Content Provider Authority](../README.md#content-provider-authority).

### Service Initialization

#### Implement Data Capturing Listener

*No API changes.*

#### Implement UI Listener

*No API changes.*

#### Implement Event Handling Strategy

*No API changes*

##### Custom Capturing Notification

We noticed the following:
  
* If you want to use a **vector xml drawable as Notification icon** make sure to do the following:

  Even with `vectorDrawables.useSupportLibrary` enabled the vector drawable won't work as a notification icon (`notificationBuilder.setSmallIcon()`)
  on devices with API < 21. We assume that's because of the way we need to inject your custom notification.
  A simple fix is to have a the xml in `res/drawable-anydpi-v21/icon.xml` and to generate notification icon PNGs under the same resource name in the usual paths (`res/drawable-**dpi/icon.png`).

```java
class EventHandlingStrategyImpl implements EventHandlingStrategy {

    public Notification buildCapturingNotification(@NonNull final DataCapturingBackgroundService context) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(context.getText(de.cyface.datacapturing.R.string.capturing_active))
                .setContentIntent(onClickPendingIntent).setWhen(System.currentTimeMillis()).setOngoing(true)
                .setAutoCancel(false);
    
        // This leads to the crash if you only have a vector drawable in the resources
        builder.setSmallIcon(R.drawable.icon);
        return builder.build();
    }
}
```

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