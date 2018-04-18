package de.cyface.datacapturing.backend;

import java.io.Closeable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Point3D;

/**
 * Implements the data capturing functionality for Cyface. This class implements the SensorEventListener to listen to
 * acceleration sensor events as well as the LocationListener to listen to location updates.
 *
 * @author Klemens Muthmann
 * @version 1.2.1
 * @since 1.0.0
 */
public abstract class CapturingProcess implements SensorEventListener, LocationListener, Closeable {

    /**
     * The tag used to identify log messages send to logcat.
     */
    private final static String TAG = CapturingProcess.class.getName();
    /**
     * A delay used to bundle capturing of sensor events, to reduce power consumption.
     */
    private static final int SENSOR_VALUE_DELAY_IN_MICROSECONDS = 500_000;
    /**
     * Cache for captured but not yet processed points from the accelerometer.
     */
    private final List<Point3D> accelerations;
    /**
     * Cache for captured but not yet processed points from the gyroscope.
     */
    private final List<Point3D> rotations;
    /**
     * Cache for captured but not yet processed points from the compass.
     */
    private final List<Point3D> directions;
    /**
     * A <code>List</code> of listeners we need to inform about captured data.
     */
    private final Collection<CapturingProcessListener> listener;
    /**
     * The Android <code>LocationManager</code> used to get geo location updates.
     */
    private final LocationManager locationManager;
    /**
     * The Android <code>SensorManager</code> used to get update from the accelerometer, gyroscope and magnetometer.
     */
    private final SensorManager sensorService;
    /**
     * Status handler watching the geo location device for fix status updates (basically fix or no-fix).
     */
    private final GeoLocationDeviceStatusHandler gpsStatusHandler;
    /**
     * Time offset used to move event time on devices measuring that time in milliseconds since device activation and
     * not Unix timestamp format. If event time is already in Unix timestamp format this should always be 0.
     */
    private long eventTimeOffset = 0;
    /**
     * Used for logging the time between sensor events. This is mainly used for debugging purposes.
     */
    private long lastSensorEventTime = 0;
    /**
     * Remembers how long geo location devices did not have a fix anymore. This prevents the system from sending
     * inaccurate values to the database. In such cases GPS values are filled up with zeros.
     */
    private long lastNoGeoLocationFixUpdateTime = 0;
    /**
     * A <code>HandlerThread</code> to handle new sensor events in the background. This is based on information from
     * <a href=
     * "https://stackoverflow.com/questions/6069485/sensormanager-registerlistener-handler-handler-example-please/6769218?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa">StackOverflow</a>.
     */
    private final HandlerThread sensorEventHandlerThread;

    /**
     * Creates a new completely initialized {@code DataCapturing} object receiving updates from the provided
     * {@link LocationManager} as well as the {@link SensorManager}.
     *
     * @param locationManager The {@link LocationManager} used to get updates about the devices location.
     * @param sensorService The {@link SensorManager} used to get updates from the devices Accelerometer, Gyroscope and
     *            Compass.
     * @param geoLocationDeviceStatusHandler Handler that is notified if there is a geo location fix or not.
     * @throws SecurityException If user did not provide permission to access geo location.
     */
    CapturingProcess(final LocationManager locationManager, final SensorManager sensorService,
            final GeoLocationDeviceStatusHandler geoLocationDeviceStatusHandler) throws SecurityException {
        if (locationManager == null) {
            throw new IllegalArgumentException("Illegal argument: locationManager was null!");
        }
        if (sensorService == null) {
            throw new IllegalArgumentException("Illegal argument: sensorService was null!");
        }
        if (geoLocationDeviceStatusHandler == null) {
            throw new IllegalArgumentException("Illegal argument: gpsHandler was null!");
        }

        this.accelerations = new Vector<>(30);
        this.rotations = new Vector<>(30);
        this.directions = new Vector<>(30);
        this.listener = new HashSet<>();
        this.locationManager = locationManager;
        this.sensorService = sensorService;
        this.locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this);
        this.gpsStatusHandler = geoLocationDeviceStatusHandler;
        this.sensorEventHandlerThread = new HandlerThread("de.cyface.sensoreventhandler");

        // Registering Sensors
        Sensor accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorEventHandlerThread.start();
        Handler sensorEventHandler = new Handler(sensorEventHandlerThread.getLooper());
        registerSensor(accelerometer, sensorEventHandler);
        registerSensor(gyroscope, sensorEventHandler);
        registerSensor(magnetometer, sensorEventHandler);
    }

    /**
     * Add a listener to this {@code DataCapturing} that is notified when new data arrives.
     *
     * @param listener The listener to add to this {@code CapturingProcess}.
     */
    void addCapturingProcessListener(final CapturingProcessListener listener) {
        this.listener.add(listener);
        gpsStatusHandler.setDataCapturingListener(this.listener);
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (location == null) {
            return;
        }
        gpsStatusHandler.setTimeOfLastLocationUpdate(System.currentTimeMillis());

        if (gpsStatusHandler.hasGpsFix()) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            long gpsTime = location.getTime();
            double speed = getCurrentSpeed(location);
            float gpsAccuracy = location.getAccuracy();

            synchronized (this) {
                for (CapturingProcessListener listener : this.listener) {
                    listener.onLocationCaptured(new GeoLocation(latitude, longitude, gpsTime, speed, gpsAccuracy));
                    listener.onDataCaptured(new CapturedData(accelerations, rotations, directions));
                }
                accelerations.clear();
                rotations.clear();
                directions.clear();
            }
        }
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
        // Nothing to do here.
    }

    @Override
    public void onProviderEnabled(final String provider) {
        // Nothing to do here.
    }

    @Override
    public void onProviderDisabled(final String provider) {
        // Nothing to do here.
    }

    /**
     * See {@link SensorEventListener#onSensorChanged(SensorEvent)}. Since this method runs in a separate thread it
     * needs to be synchronized with this object, so that transmitting the captured data and clearing the cache in
     * {@link #onLocationChanged(Location)} does not interfere with the same calls in this method.
     *
     * @param event See {@link SensorEventListener#onSensorChanged(SensorEvent)}
     */
    @Override
    public synchronized void onSensorChanged(final @NonNull SensorEvent event) {
        // The following block was moved before the setting of thisSensorEventTime without really knowing why it has
        // been the other way around.
        if (eventTimeOffset == 0) {
            // event.timestamp on Nexus5 with Android 6.0.1 seems to be the uptime in Nanoseconds (resets with
            // rebooting)
            eventTimeOffset = System.currentTimeMillis() - event.timestamp / 1_000_000L;
        }
        long thisSensorEventTime = event.timestamp / 1_000_000L + eventTimeOffset;

        // Notify client about sensor update & bulkInsert data into database even without gps fix
        if (!gpsStatusHandler.hasGpsFix() && (lastNoGeoLocationFixUpdateTime == 0
                || (thisSensorEventTime - lastNoGeoLocationFixUpdateTime > 1_000))) {
            try {
                for (CapturingProcessListener listener : this.listener) {
                    CapturedData capturedData = new CapturedData(accelerations, rotations, directions);
                    listener.onDataCaptured(capturedData);
                }

                accelerations.clear();
                rotations.clear();
                directions.clear();
                lastNoGeoLocationFixUpdateTime = thisSensorEventTime;
            } catch (SecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        // Get sensor values from event
        if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))) {
            // Check if there are irregular gaps between sensor events (e.g. no GPS fix or data loss)
            logIrregularSensorValues(thisSensorEventTime);
            saveSensorValue(event, accelerations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE))) {
            saveSensorValue(event, rotations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD))) {
            saveSensorValue(event, directions);
        }
    }

    /**
     * Logs information about sensor update intervals.
     *
     * @param thisSensorEventTime The current sensor event time in milliseconds since the 1.1.1970 (Unix timestamp
     *            format).
     */
    private void logIrregularSensorValues(final long thisSensorEventTime) {
        // Check if there are irregular gaps between sensor events (e.g. no GPS fix or data loss)
        if (lastSensorEventTime != 0 && (thisSensorEventTime - lastSensorEventTime > 100
                || thisSensorEventTime - lastSensorEventTime < -100)) {
            Log.d(TAG,
                    "internalOnSensorChanged: time gap between this (" + thisSensorEventTime + ") and last ("
                            + lastSensorEventTime + ") SensorEventTime - difference: "
                            + (thisSensorEventTime - lastSensorEventTime));
        }
        lastSensorEventTime = thisSensorEventTime;
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do here.
    }

    /**
     * Shuts down this sensor listener freeing the sensors used to caputre data.
     *
     * @throws SecurityException If user did not provide permission to access GPS location.
     */
    @Override
    public void close() throws SecurityException {
        locationManager.removeUpdates(this);
        gpsStatusHandler.shutdown();
        sensorService.unregisterListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            sensorEventHandlerThread.quitSafely();
        } else {
            sensorEventHandlerThread.quit();
        }
    }

    /**
     * Saves a captured {@code SensorEvent} to the local in memory storage for that point.
     * as different vendors and Android versions store different timestamps in the event.ts
     * (e.g. uptimeNano, sysTimeNano) we use an offset from the first sample captures to get the same timestamp format.
     *
     * @param event The Android {@code SensorEvent} to store.
     * @param storage The storage to store the {@code SensorEvent} to.
     */
    private void saveSensorValue(final SensorEvent event, final List<Point3D> storage) {
        Point3D dataPoint = new Point3D(event.values[0], event.values[1], event.values[2],
                event.timestamp / 1000000L + eventTimeOffset);
        storage.add(dataPoint);
    }

    /**
     * Registers the provided <code>Sensor</code> with this object as a listener, if the sensor is not
     * <code>null</code>. If the sensor is <code>null</code> nothing will happen.
     *
     * @param sensor The Android <code>Sensor</code> to register.
     * @param sensorEventHandler The <code>Handler</code> to run the <code>onSensorEvent</code> method on.
     */
    private void registerSensor(final Sensor sensor, final @NonNull Handler sensorEventHandler) {
        if (sensor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                sensorService.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST,
                        SENSOR_VALUE_DELAY_IN_MICROSECONDS, sensorEventHandler);
            } else {
                sensorService.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, sensorEventHandler);
            }
        }
    }

    /**
     * Provides the speed the device was traveling while being at the provided location.
     *
     * @param location The location for which a speed update is requested.
     * @return The speed in m/s.
     */
    protected abstract double getCurrentSpeed(final Location location);
}
