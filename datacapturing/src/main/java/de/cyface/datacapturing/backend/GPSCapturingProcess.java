package de.cyface.datacapturing.backend;

import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;

/**
 * Created by muthmann on 29.01.18.
 */

public class GPSCapturingProcess extends CapturingProcess {
    public GPSCapturingProcess(final LocationManager locationManager, SensorManager sensorService, GPSStatusHandler gpsStatusHandler) {
        super(locationManager,sensorService,gpsStatusHandler);
    }

    @Override
    protected double getCurrentSpeed(final Location location) {
        return location.getSpeed();
    }
}
