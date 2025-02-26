/*
 * Copyright 2025 Cyface GmbH
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

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Parcelable

/**
 * Interface for handling sensor data capturing.
 *
 * Implementations can enable or disable sensor data collection.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.13.0
 */
interface SensorCapture : Parcelable {

    /**
     * Sets up the Android service for data capturing.
     *
     * @param sensorManager The service to register the data capturing from.
     */
    fun setup(sensorManager: SensorManager)

    /**
     * Registers the requested sensors if available on the device, if not, nothing will happen.
     *
     * @param listener The listener to inform about sensor capture events.
     */
    fun register(listener: SensorEventListener)

    /**
     * Returns the Android sensor for a specified [sensorType].
     *
     * @param sensorType The type of the sensor to return.
     * @return The `Sensor` or `null` if not available.
     */
    fun defaultSensor(sensorType: Int): Sensor?

    /**
     * Cleans up resources related to sensor capturing.
     *
     * @param listener The listener to unregister from the Android service.
     */
    fun cleanup(listener: SensorEventListener)
}
