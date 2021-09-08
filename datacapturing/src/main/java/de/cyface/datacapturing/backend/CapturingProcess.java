/*
 * Copyright 2017-2021 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;
import static de.cyface.utils.TestEnvironment.isEmulator;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Point3d;
import de.cyface.utils.Validate;

/**
 * Implements the data capturing functionality for Cyface. This class implements the SensorEventListener to listen to
 * acceleration sensor events as well as the LocationListener to listen to location updates.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.1
 * @since 1.0.0
 */
public abstract class CapturingProcess implements SensorEventListener, LocationListener, Closeable {

    /**
     * The tag used to identify log messages send to logcat.
     */
    private final static String TAG = BACKGROUND_TAG;
    /**
     * A delay used to bundle capturing of sensor events, to reduce power consumption.
     */
    private static final int SENSOR_VALUE_DELAY_IN_MICROSECONDS = 500_000;
    /**
     * A delay used to reduce capturing of sensor events, to reduce data size. E.g.: 10 k = 100 Hz
     */
    private final int delayBetweenSensorEventsInMicroseconds;
    /**
     * Cache for captured but not yet processed points from the accelerometer.
     */
    private final List<Point3d> accelerations;
    /**
     * Cache for captured but not yet processed points from the gyroscope.
     */
    private final List<Point3d> rotations;
    /**
     * Cache for captured but not yet processed points from the compass.
     */
    private final List<Point3d> directions;
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
    private final GeoLocationDeviceStatusHandler locationStatusHandler;
    /**
     * Time offset used to move event time on devices measuring that time in milliseconds since device activation and
     * not Unix timestamp format. If event time is already in Unix timestamp format this should always be 0.
     */
    private Long eventTimeOffsetMillis = null;
    /**
     * Used for logging the time between sensor events. This is mainly used for debugging purposes.
     */
    private long lastSensorEventTime = 0;
    /**
     * Remembers how long geo location devices did not have a fix anymore. This prevents the system from sending
     * inaccurate values to the database. In such cases location values are filled up with zeros.
     */
    private long lastNoGeoLocationFixUpdateTime = 0;
    /**
     * A <code>HandlerThread</code> to handle new sensor events in the background. This is based on information from
     * <a href=
     * "https://stackoverflow.com/questions/6069485/sensormanager-registerlistener-handler-handler-example-please/6769218?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa">StackOverflow</a>.
     */
    private final HandlerThread sensorEventHandlerThread;
    private final HandlerThread locationEventHandlerThread;

    /**
     * Creates a new completely initialized {@code DataCapturing} object receiving updates from the provided
     * {@link LocationManager} as well as the {@link SensorManager}.
     *
     * @param locationManager The {@link LocationManager} used to get updates about the devices location.
     * @param sensorService The {@link SensorManager} used to get updates from the devices Accelerometer, Gyroscope and
     *            Compass.
     * @param geoLocationDeviceStatusHandler Handler that is notified if there is a geo location fix or not.
     * @param locationEventHandlerThread A <code>HandlerThread</code> to handle new locations in the background without
     *            blocking the calling thread.
     * @param sensorEventHandlerThread A <code>HandlerThread</code> to handle new sensor events in the background
     *            without blocking the calling thread. This is based on information from
     *            <a href=
     *            "https://stackoverflow.com/questions/6069485/sensormanager-registerlistener-handler-handler-example-please">StackOverflow</a>.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SecurityException If user did not provide permission to access geo location.
     */
    CapturingProcess(@NonNull final LocationManager locationManager, @NonNull final SensorManager sensorService,
            @NonNull final GeoLocationDeviceStatusHandler geoLocationDeviceStatusHandler,
            @NonNull final HandlerThread locationEventHandlerThread,
            @NonNull final HandlerThread sensorEventHandlerThread, final int sensorFrequency) throws SecurityException {
        Validate.notNull(locationManager, "Illegal argument: locationManager was null!");
        Validate.notNull(sensorService, "Illegal argument: sensorService was null!");
        Validate.notNull(geoLocationDeviceStatusHandler, "Illegal argument: geoLocationDeviceStatusHandler was null!");
        Validate.notNull(locationEventHandlerThread, "Illegal argument: locationEventHandlerThread was null!");
        Validate.notNull(sensorEventHandlerThread, "Illegal argument: sensorEventHandlerThread was null!");

        this.accelerations = new Vector<>(30);
        this.rotations = new Vector<>(30);
        this.directions = new Vector<>(30);
        this.listener = new HashSet<>();
        this.locationManager = locationManager;
        this.sensorService = sensorService;
        this.locationStatusHandler = geoLocationDeviceStatusHandler;
        this.locationEventHandlerThread = locationEventHandlerThread;
        this.sensorEventHandlerThread = sensorEventHandlerThread;
        this.delayBetweenSensorEventsInMicroseconds = 1_000_000 / sensorFrequency;

        locationEventHandlerThread.start();
        this.locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this,
                locationEventHandlerThread.getLooper());

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
        locationStatusHandler.setDataCapturingListener(this.listener);
    }

    @Override
    public void onLocationChanged(@NonNull final Location location) {
        locationStatusHandler.setTimeOfLastLocationUpdate(System.currentTimeMillis());

        if (locationStatusHandler.hasLocationFix()) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            long locationTime = location.getTime();
            double speed = getCurrentSpeed(location);
            float locationAccuracyMeters = location.getAccuracy();
            if (de.cyface.datacapturing.BuildConfig.DEBUG && isEmulator()) {
                locationAccuracyMeters = (float)Math.random() * 30.0f;
                Log.d(TAG, "Emulator detected, Accuracy overwritten to: " + locationAccuracyMeters);
            }

            synchronized (this) {
                for (final CapturingProcessListener listener : this.listener) {
                    listener.onLocationCaptured(
                            // The Android Location contains the accuracy in meters. GeoLocation uses cm.
                            new GeoLocation(latitude, longitude, locationTime, speed, locationAccuracyMeters * 100));
                    try {
                        listener.onDataCaptured(new CapturedData(accelerations, rotations, directions));
                    } catch (DataCapturingException e) {
                        throw new IllegalStateException(e);
                    }
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
        if (eventTimeOffsetMillis == null) {
            eventTimeOffsetMillis = eventTimeOffset(event.timestamp);
        }
        long thisSensorEventTime = event.timestamp / 1_000_000L + eventTimeOffsetMillis;

        // Notify client about sensor update & bulkInsert data into database even without location fix
        if (!locationStatusHandler.hasLocationFix() && (lastNoGeoLocationFixUpdateTime == 0
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
            } catch (SecurityException | DataCapturingException e) {
                throw new IllegalStateException(e);
            }
        }

        // Get sensor values from event
        if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))) {
            // Check if there are irregular gaps between sensor events (e.g. no location fix or data loss)
            logIrregularSensorValues(thisSensorEventTime);
            saveSensorValue(event, accelerations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE))) {
            saveSensorValue(event, rotations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD))) {
            saveSensorValue(event, directions);
        }
    }

    /**
     * Calculates the static offset (ms) which needs to be added to the `event.time` (ns) in order
     * to calculate the Unix timestamp of the event.
     * <p>
     * The official `SensorEvent#timestamp` documentation states the `event.time` the
     * `SystemClock.elapsedRealTimeNanos()` at the time of capture. But we are taking into account that some
     * manufacturers/devices use the nano-second equivalent of `SystemClock.currentTimeMillis()` instead.
     * <p>
     * As we only use `SystemClock` to calculate the offset (e.g. bootTime) this approach should
     * allow to synchronize the event timestamps between phones by syncing their system time.
     *
     * @param eventTimeNanos the timestamp of the {@code SensorEvent} used to determine the offset
     * @return the offset in milliseconds
     */
    long eventTimeOffset(final long eventTimeNanos) {

        // Capture timestamps of event reporting time
        final long elapsedRealTimeMillis = SystemClock.elapsedRealtime();
        final long upTimeMillis = SystemClock.uptimeMillis();
        final long currentTimeMillis = System.currentTimeMillis();

        // Check which timestamp the event.time is closest to, because some manufacturers and models use the currentTime
        // instead of the elapsedRealTime as the official documentation states for the `event.time`.
        // Once example could be the Nexus 4 according to https://stackoverflow.com/a/9333605/5815054,
        // but we also noticed this on some of our devices which we tested back in 2016/2017.
        final long eventTimeMillis = eventTimeNanos / 1_000_000L;
        final long elapsedTimeDiff = elapsedRealTimeMillis - eventTimeMillis;
        final long upTimeDiff = upTimeMillis - eventTimeMillis;
        final long currentTimeDiff = currentTimeMillis - eventTimeMillis;
        if (Math.min(Math.abs(currentTimeDiff), Math.abs(elapsedTimeDiff)) >= 1_000) {
            // Not really a problem but could indicate there is another case we did not think of
            Log.w(TAG,
                    "sensorTimeOffset: event delay seems to be relatively high: " + currentTimeDiff + " ms");
        }

        // Default case (elapsedRealTime, following the documentation)
        if (Math.abs(elapsedTimeDiff) <= Math.min(Math.abs(upTimeDiff), Math.abs(currentTimeDiff))) {
            final long bootTimeMillis = currentTimeMillis - elapsedRealTimeMillis;
            Log.d(TAG, "sensorTimeOffset: event.time seems to be elapsedTime, setting offset "
                    + "to bootTime: " + bootTimeMillis + " ms");
            return bootTimeMillis;
        }
        // Other seen case (currentTime, e.g. Nexus 4)
        if (Math.abs(currentTimeDiff) <= Math.abs(upTimeDiff)) {
            Log.d(TAG, "sensorTimeOffset: event.time seems to be currentTime, setting offset "
                    + "to zero ms");
            return 0;
        }
        // Possible case, but unknown if actually used by manufacturers (upTime)
        // If we calculate a static offset between currentTime and upTime this would lead to time
        // shifts when the device is in (deep) sleep again. We would need to calculate the offset
        // dynamically for each event which is quite heavy. Thus, we throw an exception to see
        // in the Play Store if this actually happens on devices. If so, this could be tested using:
        // https://developer.android.com/training/monitoring-device-state/doze-standby#testing_doze_and_app_standby
        throw new IllegalStateException("The event.time seems to be upTime. In this case we cannot"
                + " use a static offset to calculate the Unix timestamp of the event");
    }

    /**
     * Logs information about sensor update intervals.
     *
     * @param thisSensorEventTime The current sensor event time in milliseconds since the 1.1.1970 (Unix timestamp
     *            format).
     */
    private void logIrregularSensorValues(final long thisSensorEventTime) {
        // Check if there are irregular gaps between sensor events (e.g. no location fix or data loss)
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
     * Shuts down this sensor listener freeing the sensors used to capture data.
     *
     * @throws SecurityException If user did not provide permission to access fine location.
     */
    @Override
    public void close() throws SecurityException {
        locationManager.removeUpdates(this);
        locationStatusHandler.shutdown();
        sensorService.unregisterListener(this);
        sensorEventHandlerThread.quitSafely();
        locationEventHandlerThread.quitSafely();
    }

    /**
     * Saves a captured {@code SensorEvent} to the local in memory storage for that point.
     * as different vendors and Android versions store different timestamps in the event.ts
     * (e.g. uptimeNano, sysTimeNano) we use an offset from the first sample captures to get the same timestamp format.
     *
     * @param event The Android {@code SensorEvent} to store.
     * @param storage The storage to store the {@code SensorEvent} to.
     */
    private void saveSensorValue(final SensorEvent event, final List<Point3d> storage) {
        Point3d dataPoint = new Point3d(event.values[0], event.values[1], event.values[2],
                event.timestamp / 1_000_000L + eventTimeOffsetMillis);
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
            sensorService.registerListener(this, sensor, delayBetweenSensorEventsInMicroseconds,
                    SENSOR_VALUE_DELAY_IN_MICROSECONDS, sensorEventHandler);
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
