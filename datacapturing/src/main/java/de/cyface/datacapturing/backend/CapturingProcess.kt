/*
 * Copyright 2017-2023 Cyface GmbH
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

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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
import de.cyface.utils.Validate
import java.io.Closeable
import java.util.Vector
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.min

/**
 * Implements the data capturing functionality for Cyface. This class implements the SensorEventListener to listen to
 * acceleration sensor events as well as the LocationListener to listen to location updates.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.2.2
 * @since 1.0.0
 */
@SuppressLint("MissingPermission") // UI has to handle permission request before starting this
abstract class CapturingProcess internal constructor(
    locationManager: LocationManager, sensorService: SensorManager,
    geoLocationDeviceStatusHandler: GeoLocationDeviceStatusHandler,
    locationEventHandlerThread: HandlerThread,
    sensorEventHandlerThread: HandlerThread, sensorFrequency: Int
) : SensorEventListener, LocationListener, Closeable {
    /**
     * Cache for captured but not yet processed points from the accelerometer.
     */
    private val accelerations: MutableList<ParcelablePoint3D>

    /**
     * Cache for captured but not yet processed points from the gyroscope.
     */
    private val rotations: MutableList<ParcelablePoint3D>

    /**
     * Cache for captured but not yet processed points from the compass.
     */
    private val directions: MutableList<ParcelablePoint3D>

    /**
     * Cache for captured but not yet processed points from the barometer.
     */
    private val pressures: MutableList<ParcelablePressure>

    /**
     * A `List` of listeners we need to inform about captured data.
     */
    private val listener: MutableCollection<CapturingProcessListener>

    /**
     * The Android `LocationManager` used to get geo location updates.
     */
    private val locationManager: LocationManager

    /**
     * The Android `SensorManager` used to get update from the accelerometer, gyroscope and magnetometer.
     */
    private val sensorService: SensorManager

    /**
     * Status handler watching the geo location device for fix status updates (basically fix or no-fix).
     */
    private val locationStatusHandler: GeoLocationDeviceStatusHandler

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
     * A `HandlerThread` to handle new sensor events in the background. This is based on information from
     * [StackOverflow](https://stackoverflow.com/questions/6069485/sensormanager-registerlistener-handler-handler-example-please/6769218?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa).
     */
    private val sensorEventHandlerThread: HandlerThread
    private val locationEventHandlerThread: HandlerThread

    /**
     * The provider to use to check the build version of the system.
     */
    private var buildVersionProvider: BuildVersionProvider

    /**
     * Creates a new completely initialized `DataCapturing` object receiving updates from the provided
     * [LocationManager] as well as the [SensorManager].
     *
     * @param locationManager The [LocationManager] used to get updates about the devices location.
     * @param sensorService The [SensorManager] used to get updates from the devices Accelerometer, Gyroscope and
     * Compass.
     * @param geoLocationDeviceStatusHandler Handler that is notified if there is a geo location fix or not.
     * @param locationEventHandlerThread A `HandlerThread` to handle new locations in the background without
     * blocking the calling thread.
     * @param sensorEventHandlerThread A `HandlerThread` to handle new sensor events in the background
     * without blocking the calling thread. This is based on information from
     * [StackOverflow](https://stackoverflow.com/questions/6069485/sensormanager-registerlistener-handler-handler-example-please).
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SecurityException If user did not provide permission to access geo location.
     */
    init {
        Validate.notNull(locationManager, "Illegal argument: locationManager was null!")
        Validate.notNull(sensorService, "Illegal argument: sensorService was null!")
        Validate.notNull(
            geoLocationDeviceStatusHandler,
            "Illegal argument: geoLocationDeviceStatusHandler was null!"
        )
        Validate.notNull(
            locationEventHandlerThread,
            "Illegal argument: locationEventHandlerThread was null!"
        )
        Validate.notNull(
            sensorEventHandlerThread,
            "Illegal argument: sensorEventHandlerThread was null!"
        )

        this.accelerations = Vector(30)
        this.rotations = Vector(30)
        this.directions = Vector(30)
        this.pressures = Vector(30)
        this.listener = HashSet()
        this.locationManager = locationManager
        this.sensorService = sensorService
        this.locationStatusHandler = geoLocationDeviceStatusHandler
        this.locationEventHandlerThread = locationEventHandlerThread
        this.sensorEventHandlerThread = sensorEventHandlerThread
        this.buildVersionProvider = BuildVersionProviderImpl()

        locationEventHandlerThread.start()
        this.locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 0L, 0f, this,
            locationEventHandlerThread.looper
        )

        // Registering Sensors
        val accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val barometer = sensorService.getDefaultSensor(Sensor.TYPE_PRESSURE)
        sensorEventHandlerThread.start()
        val sensorEventHandler = Handler(sensorEventHandlerThread.looper)
        // A delay used to reduce capturing of sensor events, to reduce data size. E.g.: 10 k = 100 Hz
        val delayBetweenSensorEventsInMicroseconds = 1000000 / sensorFrequency
        registerSensor(accelerometer, sensorEventHandler, delayBetweenSensorEventsInMicroseconds)
        registerSensor(gyroscope, sensorEventHandler, delayBetweenSensorEventsInMicroseconds)
        registerSensor(magnetometer, sensorEventHandler, delayBetweenSensorEventsInMicroseconds)
        // The lowest possible frequency is ~5-10 Hz, which is also the normal frequency. We average the
        // data to 1 Hz to decrease database usage and to support barometers like in the Pixel 6 [STAD-400].
        registerSensor(barometer, sensorEventHandler, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Add a listener to this `DataCapturing` that is notified when new data arrives.
     *
     * @param listener The listener to add to this `CapturingProcess`.
     */
    fun addCapturingProcessListener(listener: CapturingProcessListener) {
        this.listener.add(listener)
        locationStatusHandler.setDataCapturingListener(this.listener)
    }

    override fun onLocationChanged(location: Location) {
        locationStatusHandler.setTimeOfLastLocationUpdate(System.currentTimeMillis())

        if (locationStatusHandler.hasLocationFix()) {
            val latitude = location.latitude
            val longitude = location.longitude
            // Don't write default value `0.0` when no value is available
            var altitude = if (location.hasAltitude()) location.altitude else null
            val locationTime = location.time
            val speed = getCurrentSpeed(location)
            var accuracy = location.accuracy.toDouble()
            // Don't write default value `0.0` when no value is available
            var verticalAccuracyMeters: Double? = null
            if (buildVersionProvider.isOreoAndAbove) {
                verticalAccuracyMeters =
                    if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null
            }
            if (BuildConfig.DEBUG
                && (TestEnvironment.isEmulator() || (Build.FINGERPRINT != null && Build.FINGERPRINT.startsWith(
                    "google/sdk_"
                )))
            ) {
                accuracy = Math.random() * 30.0
                verticalAccuracyMeters = accuracy * 2.5
                altitude = 400.0 + Math.random() * 2 - Math.random()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val copy = ArrayList(pressures)
                    pressures.clear()
                    pressures.addAll(
                        copy.stream().map { p: ParcelablePressure ->
                            ParcelablePressure(
                                p.timestamp,
                                p.pressure + Math.random() * 2 - Math.random()
                            )
                        }
                            .collect(Collectors.toList()))
                } else {
                    throw NotImplementedError("We don't support older emulators right now")
                }
                Log.d(
                    TAG,
                    String.format(
                        "Emulator detected, Accuracy overwritten with %f and vertical accuracy with %f",
                        accuracy, verticalAccuracyMeters
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
        if (!locationStatusHandler.hasLocationFix() && (lastNoGeoLocationFixUpdateTime == 0L
                    || (thisSensorEventTime - lastNoGeoLocationFixUpdateTime > 1000))
        ) {
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
        if (event.sensor == sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)) {
            // Check if there are irregular gaps between sensor events (e.g. no location fix or data loss)
            logIrregularSensorValues(thisSensorEventTime)
            saveSensorValue(event, accelerations)
        } else if (event.sensor == sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE)) {
            saveSensorValue(event, rotations)
        } else if (event.sensor == sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
            saveSensorValue(event, directions)
        } else if (event.sensor == sensorService.getDefaultSensor(Sensor.TYPE_PRESSURE)) {
            savePressureValue(event, pressures)
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
        locationManager.removeUpdates(this)
        locationStatusHandler.shutdown()
        sensorService.unregisterListener(this)
        sensorEventHandlerThread.quitSafely()
        locationEventHandlerThread.quitSafely()
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
        Validate.isTrue(event.values.size >= 1, "Unexpected number of values")
        val dataPoint =
            ParcelablePressure(timestampMillis(event.timestamp), event.values[0].toDouble())
        storage.add(dataPoint)
    }

    /**
     * Converts the event time a supported format.
     *
     *
     * As different vendors and Android versions store different timestamps in the `event.ts`
     * (e.g. uptimeNano, sysTimeNano) we use an offset from the first sample captures to get the same
     * timestamp format.
     *
     * @param eventTimestamp The `SensorEvent#timestamp` to convert.
     * @return The converted timestamp in milliseconds.
     */
    private fun timestampMillis(eventTimestamp: Long): Long {
        return eventTimestamp / 1000000L + eventTimeOffsetMillis!!
    }

    /**
     * Registers the provided `Sensor` with this object as a listener, if the sensor is not
     * `null`. If the sensor is `null` nothing will happen.
     *
     * @param sensor The Android `Sensor` to register.
     * @param sensorEventHandler The `Handler` to run the `onSensorEvent` method on.
     * @param delayMicros The desired delay between two consecutive events in microseconds. This is
     * only a hint to the system. Events may be received faster or slower than the
     * specified rate. Usually events are received faster. Can be one of `SENSOR_DELAY_NORMAL`,
     * `SENSOR_DELAY_UI`, `SENSOR_DELAY_GAME`, `SENSOR_DELAY_FASTEST` or the delay in
     * microseconds.
     */
    private fun registerSensor(sensor: Sensor?, sensorEventHandler: Handler, delayMicros: Int) {
        if (sensor != null) {
            sensorService.registerListener(
                this, sensor, delayMicros,
                SENSOR_VALUE_DELAY_IN_MICROSECONDS, sensorEventHandler
            )
        }
    }

    /**
     * Provides the speed the device was traveling while being at the provided location.
     *
     * @param location The location for which a speed update is requested.
     * @return The speed in m/s.
     */
    protected abstract fun getCurrentSpeed(location: Location?): Double

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
         * A delay used to bundle capturing of sensor events, to reduce power consumption.
         */
        private const val SENSOR_VALUE_DELAY_IN_MICROSECONDS = 500000
    }
}
