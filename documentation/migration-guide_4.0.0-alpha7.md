Cyface Android SDK 4.0.0-alpha7 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

Upgrading from 4.0.0-alpha2
-------------------------------

If you want to migrate from an earlier version please start with the [previous migration guide](./migration-guide_4.0.0-alpha2.md).

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

*No API changes.*

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

We're currently forced to use global broadcasts to check weather the `DataCapturingBackgroundService` is running.

In order to make sure that multiple SDK implementing apps don't influence each other when installed on the same device
we use a device-wide app-unique prefix for those broadcasts.

So far we used the `deviceIdentifier` which is generated for each app installation.
With the bug fix introduced in this release we now cannot access the `deviceIdentifier` anymore where required.

For this reason we had to change the **API for the `StartupFinishedHandler`** so
**you need to use the `packageName` instead** as constructor parameter:

```java
public class DataCapturingButton implements DataCapturingListener {
    public void onClick(View view) {
        
        // Version 4.0.0-alpha2
        dataCapturingService.start(vehicle, new StartUpFinishedHandler(persistence.getDeviceIdentifier()) {
            @Override
            public void startUpFinished(final long measurementIdentifier) {
                // Your logic, e.g.:
                setButtonStatus(button, true);
            }
        });
        
        // Version 4.0.0-alpha7
        dataCapturingService.start(vehicle, new StartUpFinishedHandler(context.getPackageName()) {
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

*No API changes.*

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