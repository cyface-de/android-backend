/*
 * Copyright 2017-2025 Cyface GmbH
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
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import de.cyface.datacapturing.BuildConfig
import de.cyface.datacapturing.Constants
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.ParcelablePoint3D
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.utils.TestEnvironment
import java.io.Closeable
import java.util.Locale
import java.util.Vector
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.min

/**
 * Implements the data capturing functionality for Cyface.
 *
 * This class implements the SensorEventListener to listen to acceleration sensor events as well as
 * the LocationListener to listen to location updates.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.2.3
 * @since 1.0.0
 * @param locationCapture The [LocationCapture] which sets up the location capturing.
 * @param sensorCapture The [SensorCapture] implementation which decides if sensor data should
 * be captured and sets up the capturing if requested.
 * @throws `SecurityException` If user did not provide permission to access geo location.
 */
abstract class CapturingProcess internal constructor(
    private val locationCapture: LocationCapture,
    private val sensorCapture: SensorCapture,
) : SensorEventListener, LocationListener, Closeable {
    /**
     * Cache for captured but not yet processed points from the accelerometer.
     */
    private val accelerations: MutableList<ParcelablePoint3D> = Vector(30)

    /**
     * Cache for captured but not yet processed points from the gyroscope.
     */
    private val rotations: MutableList<ParcelablePoint3D> = Vector(30)

    /**
     * Cache for captured but not yet processed points from the compass.
     */
    private val directions: MutableList<ParcelablePoint3D> = Vector(30)

    /**
     * Cache for captured but not yet processed points from the barometer.
     */
    private val pressures: MutableList<ParcelablePressure> = Vector(30)

    /**
     * A `List` of listeners we need to inform about captured data.
     */
    private val listener: MutableCollection<CapturingProcessListener> = HashSet()

    /**
     * Time offset used to move event time on devices measuring that time in milliseconds since device activation and
     * not Unix timestamp format. If event time is already in Unix timestamp format this should always be 0.
     */
    private var eventTimeOffsetMillis: Long? = null

    /**
     * Used for logging the time between sensor events. This is mainly used for debugging purposes.
     */
    private var lastSensorEventTime: Long = 0

    /**
     * Remembers how long geo location devices did not have a fix anymore. This prevents the system from sending
     * inaccurate values to the database. In such cases location values are filled up with zeros.
     */
    private var lastNoGeoLocationFixUpdateTime: Long = 0

    /**
     * The provider to use to check the build version of the system.
     */
    private var buildVersionProvider: BuildVersionProvider = BuildVersionProviderImpl()

    init {
        locationCapture.register(listener = this)
        sensorCapture.register(listener = this)
    }

    /**
     * Add a listener to this `DataCapturing` that is notified when new data arrives.
     *
     * @param listener The listener to add to this `CapturingProcess`.
     */
    fun addCapturingProcessListener(listener: CapturingProcessListener) {
        this.listener.add(listener)
        this.locationCapture.setDataCapturingListener(this.listener)
    }

    override fun onLocationChanged(location: Location) {
        locationCapture.setTimeOfLastLocationUpdate(System.currentTimeMillis())

        if (locationCapture.hasLocationFix()) {
            val latitude = location.latitude
            val longitude = location.longitude
            // Don't write default value `0.0` when no value is available
            var altitude = if (location.hasAltitude()) location.altitude else null
            val locationTime = location.time
            val speed = getCurrentSpeed(location)
            var accuracy = location.accuracy.toDouble()
            // Don't write default value `0.0` when no value is available
            var verticalAccuracyMeters = if (location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters.toDouble()
            } else {
                null
            }
            if (BuildConfig.DEBUG
                && (TestEnvironment.isEmulator || (Build.FINGERPRINT != null && Build.FINGERPRINT.startsWith(
                    "google/sdk_"
                )))
            ) {
                accuracy = Math.random() * 25.0
                verticalAccuracyMeters = accuracy * 2.5
                altitude = 400.0 + Math.random() * 2 - Math.random()
                val copy = pressures.toList()
                pressures.clear()
                pressures.addAll(
                    copy.stream().map { p: ParcelablePressure ->
                        ParcelablePressure(
                            p.timestamp,
                            p.pressure + Math.random() * 2 - Math.random()
                        )
                    }
                        .collect(Collectors.toList()))
                Log.d(
                    TAG,
                    String.format(
                        Locale.getDefault(),
                        "Emulator detected, Accuracy overwritten with %f and vertical accuracy with %f",
                        accuracy,
                        verticalAccuracyMeters,
                    )
                )
            }

            synchronized(this) {
                for (listener in this.listener) {
                    listener.onLocationCaptured(
                        ParcelableGeoLocation(
                            locationTime, latitude, longitude, altitude, speed,
                            accuracy, verticalAccuracyMeters
                        )
                    )
                    try {
                        listener.onDataCaptured(
                            CapturedData(
                                accelerations,
                                rotations,
                                directions,
                                pressures
                            )
                        )
                    } catch (e: DataCapturingException) {
                        throw IllegalStateException(e)
                    }
                }
                accelerations.clear()
                rotations.clear()
                directions.clear()
                pressures.clear()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        // Nothing to do here.
    }

    override fun onProviderEnabled(provider: String) {
        // Nothing to do here.
    }

    override fun onProviderDisabled(provider: String) {
        // Nothing to do here.
    }

    /**
     * See [SensorEventListener.onSensorChanged]. Since this method runs in a separate thread it
     * needs to be synchronized with this object, so that transmitting the captured data and clearing the cache in
     * [.onLocationChanged] does not interfere with the same calls in this method.
     *
     * @param event See [SensorEventListener.onSensorChanged]
     */
    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        if (eventTimeOffsetMillis == null) {
            eventTimeOffsetMillis = eventTimeOffset(event.timestamp)
        }
        val thisSensorEventTime = event.timestamp / 1000000L + eventTimeOffsetMillis!!

        // Notify client about sensor update & bulkInsert data into database even without location fix
        if (!locationCapture.hasLocationFix() && (lastNoGeoLocationFixUpdateTime == 0L ||
            (thisSensorEventTime - lastNoGeoLocationFixUpdateTime > 1000))) {
            try {
                for (listener in this.listener) {
                    val capturedData = CapturedData(accelerations, rotations, directions, pressures)
                    listener.onDataCaptured(capturedData)
                }

                accelerations.clear()
                rotations.clear()
                directions.clear()
                pressures.clear()
                lastNoGeoLocationFixUpdateTime = thisSensorEventTime
            } catch (e: SecurityException) {
                throw IllegalStateException(e)
            } catch (e: DataCapturingException) {
                throw IllegalStateException(e)
            }
        }

        // Get sensor values from event
        when (event.sensor) {
            sensorCapture.defaultSensor(Sensor.TYPE_ACCELEROMETER) -> {
                // Check if there are irregular gaps between sensor events (e.g. no location fix or data loss)
                logIrregularSensorValues(thisSensorEventTime)
                saveSensorValue(event, accelerations)
            }
            sensorCapture.defaultSensor(Sensor.TYPE_GYROSCOPE) -> {
                saveSensorValue(event, rotations)
            }
            sensorCapture.defaultSensor(Sensor.TYPE_MAGNETIC_FIELD) -> {
                saveSensorValue(event, directions)
            }
            sensorCapture.defaultSensor(Sensor.TYPE_PRESSURE) -> {
                savePressureValue(event, pressures)
            }
        }
    }

    /**
     * Calculates the static offset (ms) which needs to be added to the `event.time` (ns) in order
     * to calculate the Unix timestamp of the event.
     *
     *
     * The official `SensorEvent#timestamp` documentation states the `event.time` the
     * `SystemClock.elapsedRealTimeNanos()` at the time of capture. But we are taking into account that some
     * manufacturers/devices use the nano-second equivalent of `SystemClock.currentTimeMillis()` instead.
     *
     *
     * As we only use `SystemClock` to calculate the offset (e.g. bootTime) this approach should
     * allow to synchronize the event timestamps between phones by syncing their system time.
     *
     * @param eventTimeNanos the timestamp of the `SensorEvent` used to determine the offset
     * @return the offset in milliseconds
     */
    fun eventTimeOffset(eventTimeNanos: Long): Long {
        // Capture timestamps of event reporting time

        val elapsedRealTimeMillis = SystemClock.elapsedRealtime()
        val upTimeMillis = SystemClock.uptimeMillis()
        val currentTimeMillis = System.currentTimeMillis()

        // Check which timestamp the event.time is closest to, because some manufacturers and models use the currentTime
        // instead of the elapsedRealTime as the official documentation states for the `event.time`.
        // Once example could be the Nexus 4 according to https://stackoverflow.com/a/9333605/5815054,
        // but we also noticed this on some of our devices which we tested back in 2016/2017.
        val eventTimeMillis = eventTimeNanos / 1000000L
        val elapsedTimeDiff = elapsedRealTimeMillis - eventTimeMillis
        val upTimeDiff = upTimeMillis - eventTimeMillis
        val currentTimeDiff = currentTimeMillis - eventTimeMillis
        if (min(abs(currentTimeDiff.toDouble()), abs(elapsedTimeDiff.toDouble())) >= 1000) {
            // Not really a problem but could indicate there is another case we did not think of
            Log.w(
                TAG,
                "sensorTimeOffset: event delay seems to be relatively high: $currentTimeDiff ms"
            )
        }

        // Default case (elapsedRealTime, following the documentation)
        if (abs(elapsedTimeDiff.toDouble()) <= min(
                abs(upTimeDiff.toDouble()),
                abs(currentTimeDiff.toDouble())
            )
        ) {
            val bootTimeMillis = currentTimeMillis - elapsedRealTimeMillis
            Log.d(
                TAG, "sensorTimeOffset: event.time seems to be elapsedTime, setting offset "
                        + "to bootTime: " + bootTimeMillis + " ms"
            )
            return bootTimeMillis
        }
        // Other seen case (currentTime, e.g. Nexus 4)
        if (abs(currentTimeDiff.toDouble()) <= abs(upTimeDiff.toDouble())) {
            Log.d(
                TAG, "sensorTimeOffset: event.time seems to be currentTime, setting offset "
                        + "to zero ms"
            )
            return 0
        }
        // Possible case, but unknown if actually used by manufacturers (upTime)
        // If we calculate a static offset between currentTime and upTime this would lead to time
        // shifts when the device is in (deep) sleep again. We would need to calculate the offset
        // dynamically for each event which is quite heavy. Thus, we throw an exception to see
        // in the Play Store if this actually happens on devices. If so, this could be tested using:
        // https://developer.android.com/training/monitoring-device-state/doze-standby#testing_doze_and_app_standby
        throw IllegalStateException(
            "The event.time seems to be upTime. In this case we cannot"
                    + " use a static offset to calculate the Unix timestamp of the event"
        )
    }

    /**
     * Logs information about sensor update intervals.
     *
     * @param thisSensorEventTime The current sensor event time in milliseconds since the 1.1.1970 (Unix timestamp
     * format).
     */
    private fun logIrregularSensorValues(thisSensorEventTime: Long) {
        // Check if there are irregular gaps between sensor events (e.g. no location fix or data loss)
        if (lastSensorEventTime != 0L && (thisSensorEventTime - lastSensorEventTime > 100
                    || thisSensorEventTime - lastSensorEventTime < -100)
        ) {
            Log.d(
                TAG,
                "internalOnSensorChanged: time gap between this (" + thisSensorEventTime + ") and last ("
                        + lastSensorEventTime + ") SensorEventTime - difference: "
                        + (thisSensorEventTime - lastSensorEventTime)
            )
        }
        lastSensorEventTime = thisSensorEventTime
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Nothing to do here.
    }

    /**
     * Shuts down this sensor listener freeing the sensors used to capture data.
     *
     * @throws SecurityException If user did not provide permission to access fine location.
     */
    @Throws(SecurityException::class)
    override fun close() {
        locationCapture.cleanup(this)
        sensorCapture.cleanup(this)
    }

    /**
     * Saves a captured `SensorEvent` to the local in memory storage for that point.
     *
     * @param event The Android `SensorEvent` to store.
     * @param storage The storage to store the `SensorEvent` to.
     */
    private fun saveSensorValue(event: SensorEvent, storage: MutableList<ParcelablePoint3D>) {
        val dataPoint = ParcelablePoint3D(
            timestampMillis(event.timestamp), event.values[0], event.values[1],
            event.values[2]
        )
        storage.add(dataPoint)
    }

    /**
     * Saves a captured `SensorEvent` to the local in memory storage for that point.
     *
     * @param event The Android `SensorEvent` to store.
     * @param storage The storage to store the `SensorEvent` to.
     */
    private fun savePressureValue(event: SensorEvent, storage: MutableList<ParcelablePressure>) {
        // On emulator with API 21 3 pressure values are returned on change instead of one
        require(event.values.isNotEmpty()) { "Unexpected number of values" }
        val dataPoint =
            ParcelablePressure(timestampMillis(event.timestamp), event.values[0].toDouble())
        storage.add(dataPoint)
    }

    /**
     * Converts the event time a supported format.
     *
     * Different vendors and Android versions store different timestamps in the `event.timestamp`
     * (e.g. uptimeNano, sysTimeNano). To standardize we use an offset from the first sample
     * to match the expected timestamp format.
     *
     * @param eventTimestamp The `SensorEvent#timestamp` in nanoseconds.
     * @return The converted timestamp in milliseconds.
     */
    private fun timestampMillis(eventTimestamp: Long): Long {
        return eventTimestamp / NANOS_PER_MILLI + eventTimeOffsetMillis!!
    }

    /**
     * Provides the speed the device was traveling while being at the provided location.
     *
     * @param location The location for which a speed update is requested.
     * @return The speed in m/s.
     */
    protected abstract fun getCurrentSpeed(location: Location): Double

    /**
     * @param buildVersionProvider The provider to be used to check the build version.
     */
    fun setBuildVersionProvider(buildVersionProvider: BuildVersionProvider) {
        this.buildVersionProvider = buildVersionProvider
    }

    companion object {
        /**
         * The tag used to identify log messages send to logcat.
         */
        private const val TAG = Constants.BACKGROUND_TAG

        /**
         * The number of nanoseconds per millisecond.
         */
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
