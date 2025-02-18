/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.backend

import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread

/**
 * An implementation of a `CapturingProcess` getting all data from the geolocation provider.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.1
 * @since 1.0.0
 */
class GeoLocationCapturingProcess
/**
 * Creates a new completely initialized `GeoLocationCapturingProcess` receiving location and sensor
 * updates.
 *
 * @param locationManager The Android `LocationManager` that provides updates about location changes from
 * the location provider.
 * @param sensorService The Android `SensorManager` used to access the systems accelerometer, gyroscope
 * and magnetometer.
 * @param locationStatusHandler Status handler, that informs listeners about geo location device (in this case
 * location provider) fix status changes.
 * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
 * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
 * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
 */
internal constructor(
    locationManager: LocationManager,
    sensorService: SensorManager,
    locationStatusHandler: GeoLocationDeviceStatusHandler,
    geoLocationEventHandlerThread: HandlerThread,
    sensorEventHandlerThread: HandlerThread, sensorFrequency: Int
) : CapturingProcess(
    locationManager, sensorService, locationStatusHandler, geoLocationEventHandlerThread,
    sensorEventHandlerThread, sensorFrequency
) {
    override fun getCurrentSpeed(location: Location): Double {
        return location.speed.toDouble()
    }
}
