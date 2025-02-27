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
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.os.Parcelable
import de.cyface.datacapturing.backend.SensorCaptureEnabled.CREATOR.SENSOR_VALUE_DELAY_IN_MICROSECONDS

/**
 * Implementation of [SensorCapture] that disables all sensor data capturing but pressure data.
 *
 * This class is used when accelerometer, rotation and direction data is not required.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.13.0
 */
class SensorCaptureDisabled() : SensorCapture {
    /**
     * The Android service to request sensor capture events from.
     */
    @Transient private lateinit var service: SensorManager

    /**
     * A `HandlerThread` to handle new capture events in the background without blocking the calling
     * thread. This is based on information from https://stackoverflow.com/q/6069485/5815054.
     */
    @Transient private var eventHandlerThread: HandlerThread? = null

    /**
     * Constructs a [SensorCaptureDisabled] object from a `Parcel`.
     * This is used for deserialization when passing the object between components.
     *
     * @param parcel The `Parcel` containing the serialized object data.
     */
    @Suppress("UNUSED_PARAMETER") // Required by Parcelable interface
    constructor(parcel: Parcel) : this()

    override fun setup(sensorManager: SensorManager) {
        this.service = sensorManager
    }

    override fun register(listener: SensorEventListener) {
        this.eventHandlerThread = HandlerThread("de.cyface.sensor_event").apply { start() }

        // The `Handler` to run the `onSensorEvent` method on.
        val eventHandler = Handler(eventHandlerThread!!.looper)

        /**
         * A `List` of `Pair`s with
         * - The sensor type to request.
         * - The desired delay between two consecutive events in microseconds. This is only a hint
         * to the system. Events may be received faster or slower than the specified rate. Usually
         * events are received faster. Can be one of `SENSOR_DELAY_NORMAL`, `SENSOR_DELAY_UI`,
         * `SENSOR_DELAY_GAME`, `SENSOR_DELAY_FASTEST` or the delay in microseconds.
         */
        val requestedSensors = listOf(
            // The lowest possible frequency is ~5-10 Hz, which is also the normal frequency. We
            // average the data to 1 Hz to decrease database usage and to support barometers like
            // in the Pixel 6 [STAD-400].
            Pair(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL),
        )

        // Register sensors if available (if not, nothing will happen)
        requestedSensors.forEach { (sensorType, delayMicros) ->
            service.getDefaultSensor(sensorType)?.let { sensor ->
                service.registerListener(
                    listener,
                    sensor,
                    delayMicros,
                    SENSOR_VALUE_DELAY_IN_MICROSECONDS,
                    eventHandler,
                )
            }
        }
    }

    override fun defaultSensor(sensorType: Int): Sensor? {
        return service.getDefaultSensor(sensorType)
    }

    override fun cleanup(listener: SensorEventListener) {
        service.unregisterListener(listener)
        eventHandlerThread?.quitSafely()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) = Unit

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SensorCaptureDisabled> {
        override fun createFromParcel(parcel: Parcel) = SensorCaptureDisabled(parcel)
        override fun newArray(size: Int) = arrayOfNulls<SensorCaptureDisabled?>(size)
    }
}
