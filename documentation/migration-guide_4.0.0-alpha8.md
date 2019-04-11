Cyface Android SDK 4.0.0-alpha8 Migration Guide
=================================================

This migration guide is written for apps using the `MovebisDataCapturingService`.

If you use the `CyfaceDataCapturingService` instead, please contact us. 

Upgrading from 4.0.0-alpha7
-------------------------------

If you want to migrate from an earlier version please start with the [previous migration guide](./migration-guide_4.0.0-alpha7.md).

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

We added a parameter to the `DataCapturingService` to define a maximum sensor frequency:

```java
class MainFragment extends Fragment {
    
    private MovebisDataCapturingService dataCapturingService;
    
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        
        // Version 4.0.0-alpha7
        dataCapturingService = new MovebisDataCapturingService(context, dataUploadServerAddress,
            uiListener, locationUpdateRate, eventHandlingStrategy, capturingListener);
        
        // Version 4.0.0-alpha8
        final static int SENSOR_FREQUENCY = 100;
        dataCapturingService = new MovebisDataCapturingService(context, dataUploadServerAddress,
            uiListener, locationUpdateRate, eventHandlingStrategy, capturingListener, SENSOR_FREQUENCY);
    }
}
```

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

*No API changes.*

#### Load Measurement Distance

*No API changes.*

#### Delete Measurements

*No API changes.*