/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * @author Armin Schnabel
 * @version 4.0.0
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
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     */
    GeoLocationCapturingProcess(@NonNull final LocationManager locationManager,
            @NonNull final SensorManager sensorService,
            @NonNull final GeoLocationDeviceStatusHandler locationStatusHandler,
            @NonNull final HandlerThread geoLocationEventHandlerThread,
            @NonNull final HandlerThread sensorEventHandlerThread, final int sensorFrequency) {
        super(locationManager, sensorService, locationStatusHandler, geoLocationEventHandlerThread,
                sensorEventHandlerThread, sensorFrequency);
    }

    @Override
    protected double getCurrentSpeed(final Location location) {
        return location.getSpeed();
    }
}
