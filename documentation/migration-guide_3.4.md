# Cyface Android SDK 3.4 Migration Guide

## Upgrading from 3.1

- [Service Initialization](#service-initialization)
	- [Implement Data Capturing Listener](#implement-data-capturing-listener)
	- [Implement UI Listener](#implement-ui-listener)
	- [Implement Event Handling Strategy](#implement-event-handling-strategy)
	- [Start Service](#start-service)
	- [De-/Register JWT Auth Tokens](#de-register-jwt-auth-tokens)
	- [Start/Stop UI Location Updates](#start-stop-ui-location-updates)
- [Control Capturing](#control-capturing)
	- [Start/Stop capturing](#start-stop-capturing)
	- [Pause/Resume Capturing](#pause-resume-capturing)
- [Access Measurements](#access-measurements)
	- [Load finished measurements](#load-finished-measurements)
	- [Load Tracks](#load-tracks)
	- [Delete Measurements](#delete-measurements)

### Service Initialization

#### Implement Data Capturing Listener

#### Implement UI Listener

#### Implement Event Handling Strategy

#### Start Service

```java
// Version 3.1
dataCapturingService = new MovebisDataCapturingService(...);
dataCapturingService.reconnect();
deviceId = dataCapturingService.getDeviceId();

// Version 3.4
dataCapturingService = new MovebisDataCapturingService(...);
dataCapturingService.reconnect();
deviceId = dataCapturingService.getDeviceId();
```

#### De-/Register JWT Auth Tokens

#### Start/Stop UI Location Updates

### Control Capturing

#### Start/Stop Capturing

#### Pause/Resume Capturing

### Access Measurements

#### Load Finished Measurements

#### Load Tracks 

#### Delete Measurements

