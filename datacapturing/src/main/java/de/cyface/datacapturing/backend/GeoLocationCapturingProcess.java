package de.cyface.datacapturing.backend;

import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.HandlerThread;
import androidx.annotation.NonNull;

/**
 * An implementation of a <code>CapturingProcess</code> getting all data from the geolocation provider.
 *
 * @author Klemens Muthmann
 * @version 3.0.0
 * @since 1.0.0
 */
public class GeoLocationCapturingProcess extends CapturingProcess {

    /**
     * Creates a new completely initialized <code>GeoLocationCapturingProcess</code> receiving location and sensor
     * updates.
     * 
     * @param locationManager The Android <code>LocationManager</code> that provides updates about location changes from
     *            the location provider.
     * @param sensorService The Android <code>SensorManager</code> used to access the systems accelerometer, gyroscope
     *            and magnetometer.
     * @param locationStatusHandler Status handler, that informs listeners about geo location device (in this case
     *            location provider) fix status changes.
     */
    GeoLocationCapturingProcess(final @NonNull LocationManager locationManager,
            final @NonNull SensorManager sensorService,
            final @NonNull GeoLocationDeviceStatusHandler locationStatusHandler,
            final @NonNull HandlerThread geoLocationEventHandlerThread,
            final @NonNull HandlerThread sensorEventHandlerThread) {
        super(locationManager, sensorService, locationStatusHandler, geoLocationEventHandlerThread,
                sensorEventHandlerThread);
    }

    @Override
    protected double getCurrentSpeed(final Location location) {
        return location.getSpeed();
    }
}
