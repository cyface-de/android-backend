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
import android.os.Parcel
import android.os.Parcelable

/**
 * Implementation of [SensorCapture] that disables sensor data collection.
 *
 * This class serves as a no-op alternative when sensor data capturing is not required.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.13.0
 */
class SensorCaptureDisabled() : SensorCapture {
    /**
     * Constructs a [SensorCaptureDisabled] object from a `Parcel`.
     * This is used for deserialization when passing the object between components.
     *
     * @param parcel The `Parcel` containing the serialized object data.
     */
    @Suppress("UNUSED_PARAMETER") // Required by Parcelable interface
    constructor(parcel: Parcel) : this()

    override fun setup(sensorManager: SensorManager) {
        // Nothing to do
    }

    override fun register(listener: SensorEventListener) {
        // Nothing to do since sensor capturing is disabled
    }

    override fun defaultSensor(sensorType: Int): Sensor? {
        error("SensorCaptureDisabled should not result into sensor events.")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) = Unit

    override fun cleanup(listener: SensorEventListener) {
        // Nothing to do
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SensorCaptureDisabled> {
        override fun createFromParcel(parcel: Parcel) = SensorCaptureDisabled(parcel)
        override fun newArray(size: Int) = arrayOfNulls<SensorCaptureDisabled?>(size)
    }
}
