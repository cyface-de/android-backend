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

import android.location.Location

/**
 * An implementation of a [CapturingProcess] receiving location and sensor updates.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.1
 * @since 1.0.0
 * @param locationCapture The [LocationCapture] which sets up location capturing.
 * @param sensorCapture The [SensorCapture] implementation which decides if sensor data should
 * be captured and sets it up if requested.
 */
class GeoLocationCapturingProcess internal constructor(
    locationCapture: LocationCapture,
    sensorCapture: SensorCapture,
) : CapturingProcess(locationCapture, sensorCapture) {
    override fun getCurrentSpeed(location: Location): Double {
        return location.speed.toDouble()
    }
}
