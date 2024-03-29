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

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread
import android.os.SystemClock
import de.cyface.datacapturing.model.CapturedData
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.utils.Validate
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.Collections
import java.util.Random

/**
 * Test cases to test the correct working of the data capturing process.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.8
 * @since 2.0.0
 */
class CapturingProcessTest {

    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    /**
     * An object of the class under test.
     */
    private var oocut: CapturingProcess? = null

    /**
     * A mock `SensorManager` for a real Android `SensorManager`.
     */
    @Mock
    private val sensorManager: SensorManager? = null

    /**
     * A mock `LocationManager` for a real Android `LocationManager`.
     */
    @Mock
    private val locationManager: LocationManager? = null

    /**
     * A mock for the thread handling occurrence of new geo locations.
     */
    @Mock
    private val geoLocationEventHandlerThread: HandlerThread? = null

    /**
     * A mock for the thread handling the occurrence of new sensor values.
     */
    @Mock
    private val sensorEventHandlerThread: HandlerThread? = null

    /**
     * A listener for the capturing process used to receive test events and assert against those events.
     */
    private var testListener: TestCapturingProcessListener? = null

    /**
     * Initializes all required properties and adds the `testListener` to the `CapturingProcess`.
     */
    @Before
    fun setUp() {
        oocut = GeoLocationCapturingProcess(
            locationManager!!, sensorManager!!,
            object : GeoLocationDeviceStatusHandler(locationManager) {
                public override fun shutdown() {}
                public override fun hasLocationFix(): Boolean {
                    return true
                }
            }, geoLocationEventHandlerThread!!, sensorEventHandlerThread!!, 100
        )
        testListener = TestCapturingProcessListener()
        oocut!!.addCapturingProcessListener(testListener)
        val accelerometer = initSensor("accelerometer")
        Mockito.`when`(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))
            .thenReturn(accelerometer)
    }

    /**
     * Tests the happy path of capturing accelerometer data with 200 Hz and geo locations with 1 Hz.
     */
    @Test
    fun testCaptureSensorDataAlongWithGeoLocation() {
        val random = Random(System.currentTimeMillis())
        for (i in 1..400) {
            val currentTimestamp = Integer.valueOf(i).toLong() * 5L
            val accelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val sensorEvent = createSensorEvent(
                accelerometer!!, random.nextFloat(), random.nextFloat(),
                random.nextFloat(), currentTimestamp * 1000000L
            )
            oocut!!.onSensorChanged(sensorEvent)
            if (i % 200 == 0) {
                val location = Mockito.mock(
                    Location::class.java
                )
                Mockito.`when`(location.time).thenReturn(currentTimestamp)
                oocut!!.onLocationChanged(location)
            }
        }
        MatcherAssert.assertThat(testListener!!.getCapturedData(), Matchers.hasSize(2))
        MatcherAssert.assertThat(
            testListener!!.getCapturedData()[0].accelerations.size
                    + testListener!!.getCapturedData()[1].accelerations.size,
            Matchers.`is`(Matchers.equalTo(400))
        )
        MatcherAssert.assertThat(
            testListener!!.getCapturedLocations(), Matchers.hasSize(2)
        )
    }

    /**
     * Tests that the correct `eventTimeOffset` is calculated for known `event.time` implementations.
     */
    @Test
    fun testEventTimeOffset() {

        // Arrange
        val eventDelayNanos: Long = 9000000
        val elapsedRealTimeMillis = SystemClock.elapsedRealtime()
        val currentTimeMillis = System.currentTimeMillis()
        val eventTimeDefaultImplementation = elapsedRealTimeMillis - eventDelayNanos
        val eventTimeBasedOnCurrentTime = currentTimeMillis * 1000000 - eventDelayNanos

        // Act
        val eventTimeOffsetDefault = oocut!!.eventTimeOffset(eventTimeDefaultImplementation)
        val eventTimeOffsetCurrentTime = oocut!!.eventTimeOffset(eventTimeBasedOnCurrentTime)

        // Arrange
        val expectedEventTimeOffset = currentTimeMillis - elapsedRealTimeMillis // bootTime
        val expectedEventTimeOffsetCurrent: Long = 0 // event.time equals currentTime of event
        // As we call `currentTimeMillis` after `elapsedRealTimeMillis` a milliseconds might have passed
        MatcherAssert.assertThat(
            eventTimeOffsetDefault, CoreMatchers.`is`(
                CoreMatchers.both(Matchers.greaterThanOrEqualTo(expectedEventTimeOffset))
                    .and(Matchers.lessThanOrEqualTo(expectedEventTimeOffset + 1))
            )
        )
        MatcherAssert.assertThat(
            eventTimeOffsetCurrentTime,
            CoreMatchers.equalTo(expectedEventTimeOffsetCurrent)
        )
    }

    /**
     * A convenience method to ease the creation of new Android `SensorEvent` objects.
     *
     * @param sensor The sensor to create a new `SensorEvent` for. Refer for example to
     * `SensorManager#getDefaultSensor(int)` to get a feeling for how to retrieve such an object from
     * the Android API.
     * @param x The x coordinate for the new `SensorEvent`.
     * @param y The y coordinate for the new `SensorEvent`.
     * @param z The z coordinate for the new `SensorEvent`.
     * @param timestamp The timestamp of the new `SensorEvent` in nanoseconds.
     * @return The newly created and completely initialized `SensorEvent`.
     */
    private fun createSensorEvent(
        sensor: Sensor, x: Float, y: Float, z: Float,
        timestamp: Long
    ): SensorEvent {
        return try {
            val sensorEvent = Mockito.mock(SensorEvent::class.java)
            sensorEvent.sensor = sensor
            val valuesField = sensorEvent.javaClass.getField("values")
            valuesField.isAccessible = true
            val values = floatArrayOf(x, y, z)
            valuesField[sensorEvent] = values
            val timestampField = sensorEvent.javaClass.getField("timestamp")
            timestampField.isAccessible = true
            timestampField[sensorEvent] = timestamp
            sensorEvent
        } catch (e: NoSuchFieldException) {
            throw IllegalStateException(e)
        } catch (e: IllegalAccessException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * Initializes a sensor with the provided type and name.
     *
     * @param name The name of the sensor
     * @return The newly initialized `Sensor`.
     */
    private fun initSensor(@Suppress("SameParameterValue") name: String): Sensor {
        Validate.notEmpty(name)
        val sensor = Mockito.mock(Sensor::class.java)
        Mockito.`when`(sensor.name).thenReturn(name)
        Mockito.`when`(sensor.vendor).thenReturn("Cyface")
        return sensor
    }

    /**
     * Listener reacting to events from the `CapturingProcess`. This listener provides accessors to check
     * whether the test did run correctly.
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 2.0.0
     * @since 2.0.0
     */
    private class TestCapturingProcessListener : CapturingProcessListener {
        /**
         * `GeoLocation` instances this listener was informed about.
         */
        private val capturedLocations: MutableList<ParcelableGeoLocation> = ArrayList()

        /**
         * Captured sensor data this listener was informed about.
         */
        private val capturedData: MutableList<CapturedData> = ArrayList()
        override fun onLocationCaptured(location: ParcelableGeoLocation) {
            capturedLocations.add(location)
        }

        override fun onDataCaptured(data: CapturedData) {
            capturedData.add(data)
        }

        override fun onLocationFix() {
            // nothing to do
        }

        override fun onLocationFixLost() {
            // nothing to do
        }

        /**
         * @return `GeoLocation` instances this listener was informed about.
         */
        fun getCapturedLocations(): List<ParcelableGeoLocation> {
            return Collections.unmodifiableList(capturedLocations)
        }

        /**
         * @return Captured sensor data this listener was informed about.
         */
        fun getCapturedData(): List<CapturedData> {
            return Collections.unmodifiableList(capturedData)
        }
    }
}