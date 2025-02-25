package de.cyface.datacapturing.backend

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Parcelable

/**
 * Interface for handling sensor data capturing.
 * Implementations can enable or disable sensor data collection.
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
