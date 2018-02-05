package de.cyface.datacapturing.backend;

import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;

/**
 * An implementation of a <code>CapturingProcess</code> getting all data from the GPS device.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class GPSCapturingProcess extends CapturingProcess {

    /**
     * Creates a new completely initialized <code>GPSCapturingProcess</code> receiving location and sensor updates.
     * 
     * @param locationManager The Android <code>LocationManager</code> that provides updates about location changes from
     *            the GPS device.
     * @param sensorService The Android <code>SensorManager</code> used to access the systems accelerometer, gyroscope
     *            and magnetometer.
     * @param gpsStatusHandler Status handler, that informs listeners about geo location device (in this case GPS
     *            device) fix status changes.
     */
    GPSCapturingProcess(final LocationManager locationManager, SensorManager sensorService,
            GeoLocationDeviceStatusHandler gpsStatusHandler) {
        super(locationManager, sensorService, gpsStatusHandler);
    }

    @Override
    protected double getCurrentSpeed(final Location location) {
        return location.getSpeed();
    }
}
