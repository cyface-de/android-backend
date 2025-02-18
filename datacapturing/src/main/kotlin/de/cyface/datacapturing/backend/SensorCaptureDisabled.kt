package de.cyface.datacapturing.backend

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.os.HandlerThread
import android.os.Parcel
import android.os.Parcelable

/**
 * Implementation of [SensorCapture] that disables sensor data collection.
 * This class serves as a no-op alternative when sensor data capturing is not required.
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
