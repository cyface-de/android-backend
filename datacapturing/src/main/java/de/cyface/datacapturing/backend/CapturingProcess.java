package de.cyface.datacapturing.backend;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.io.Closeable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Point3D;

/**
 * Implements the data capturing functionality for Cyface. This class implements the SensorEventListener to listen to
 * acceleration sensor events as well as the LocationListener to listen to location updates.
 *
 * @author Klemens Muthmann
 * @version 1.2.0
 * @since 1.0.0
 */
public abstract class CapturingProcess implements SensorEventListener, LocationListener, Closeable {

    private final static String TAG = CapturingProcess.class.getName();

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

    private final Collection<CapturingProcessListener> listener;
    private final LocationManager locationManager;
    private final SensorManager sensorService;
    private final GPSStatusHandler gpsStatusHandler;
    private long eventTimeOffset = 0;
    private long lastSensorEventTime = 0;
    private long lastNoGpsFixUpdateTime = 0;

    /**
     * Creates a new completely initialized {@code DataCapturing} object receiving updates from the provided {@link LocationManager} as well as the {@link SensorManager}.
     *
     * @param locationManager The {@link LocationManager} used to get updates about the devices location.
     * @param sensorService   The {@link SensorManager} used to get updates from the devices Accelerometer, Gyroscope and Compass.
     * @param gpsStatusHandler
     * @throws SecurityException If user did not provide permission to access GPS location.
     */
    public CapturingProcess(final LocationManager locationManager, final SensorManager sensorService, final GPSStatusHandler gpsStatusHandler) throws SecurityException {
        if(locationManager==null) {
            throw new IllegalArgumentException("Illegal argument: locationManager was null!");
        }
        if(sensorService==null) {
            throw new IllegalArgumentException("Illegal argument: sensorService was null!");
        }
        if(gpsStatusHandler==null) {
            throw new IllegalArgumentException("Illegal argument: gpsHandler was null!");
        }

        this.accelerations = new Vector<>(30);
        this.rotations = new Vector<>(30);
        this.directions = new Vector<>(30);
        this.listener = new HashSet<>();
        this.locationManager = locationManager;
        this.sensorService = sensorService;
        this.locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0L,0f,this);
        this.gpsStatusHandler = gpsStatusHandler;

        // Registering Sensors
        Sensor accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer != null) {
            sensorService.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (gyroscope != null) {
            sensorService.registerListener(this,gyroscope,SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (magnetometer != null) {
            sensorService.registerListener(this,magnetometer,SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    /**
     * <p>
     * Add a listener to this {@code DataCapturing} that is notified when new data arrives.
     * </p>
     *
     * @param listener The listener to add to this {@code DataCapturing}.
     */
    public void addCapturingProcessListener(final CapturingProcessListener listener) {
        this.listener.add(listener);
        gpsStatusHandler.setDataCapturingListener(this.listener);
    }

    public void onLocationChanged(final Location location) {
        if (location == null) {
            return;
        }
        gpsStatusHandler.setTimeOfLastLocationUpdate(System.currentTimeMillis());

        if(gpsStatusHandler.hasGpsFix()) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            long gpsTime = location.getTime();
            double speed = getCurrentSpeed(location);
            // Android gets the accuracy in meters but we save it in centimeters.
            int gpsAccuracy = Math.round(location.getAccuracy() * 100);
            for (CapturingProcessListener listener : this.listener) {
                listener.onPointCaptured(new CapturedData(latitude,longitude,gpsTime,speed,gpsAccuracy,accelerations,rotations,directions));
            }
            accelerations.clear();
            rotations.clear();
            directions.clear();
        }
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {

    }

    @Override
    public void onProviderEnabled(final String provider) {

    }

    @Override
    public void onProviderDisabled(final String provider) {

    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        long thisSensorEventTime = event.timestamp / 1000000L + eventTimeOffset;
        if (eventTimeOffset == 0) {
            // event.timestamp on Nexus5 with Android 6.0.1 seems to be the uptime in Nanoseconds (resets with rebooting)
            eventTimeOffset = System.currentTimeMillis() - event.timestamp / 1000000L;
        }

        // Notify client about sensor update & bulkInsert data into database even without gps fix
        if (!gpsStatusHandler.hasGpsFix() && (lastNoGpsFixUpdateTime == 0 || (thisSensorEventTime - lastNoGpsFixUpdateTime > 1000))) {
            for (CapturingProcessListener listener : this.listener) {
                listener.onPointCaptured(new CapturedData(0.0,0.0,0L,0.0,0,accelerations,rotations,directions));
            }
        }
        accelerations.clear();
        rotations.clear();
        directions.clear();
        lastNoGpsFixUpdateTime = thisSensorEventTime;

        // Get sensor values from event
        if(event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))) {
            // Check if there are irregular gaps between sensor events (e.g. no GPS fix or data loss)
            logIrregularSensorValues(thisSensorEventTime);
            saveSensorValue(event, accelerations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE))) {
            saveSensorValue(event, rotations);
        } else if (event.sensor.equals(sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD))) {
            saveSensorValue(event, directions);
        }
    }

    private void logIrregularSensorValues(final long thisSensorEventTime) {
        // Check if there are irregular gaps between sensor events (e.g. no GPS fix or data loss)
        if (lastSensorEventTime != 0 && (thisSensorEventTime - lastSensorEventTime > 100 || thisSensorEventTime - lastSensorEventTime < -100)) {
            Log.d(TAG, "internalOnSensorChanged: time gap between this (" + thisSensorEventTime
                    + ") and last (" + lastSensorEventTime + ") SensorEventTime - difference: " +
                    (thisSensorEventTime - lastSensorEventTime));
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
    }

    /**
     * Saves a captured {@code SensorEvent} to the local in memory storage for that point.
     * as different vendors and Android versions store different timestamps in the event.ts
     * (e.g. uptimeNano, sysTimeNano) we use an offset from the first sample captures to get the same timestamp format.
     *
     * @param event   The Android {@code SensorEvent} to store.
     * @param storage The storage to store the {@code SensorEvent} to.
     */
    private void saveSensorValue(final SensorEvent event, final List<Point3D> storage) {
        Point3D dataPoint = new Point3D(event.values[0], event.values[1], event.values[2], event.timestamp / 1000000L + eventTimeOffset);
        storage.add(dataPoint);
    }

    protected abstract double getCurrentSpeed(final Location location);
}
