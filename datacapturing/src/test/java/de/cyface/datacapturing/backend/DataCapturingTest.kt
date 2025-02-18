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

import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread
import de.cyface.persistence.model.ParcelableGeoLocation
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Tests the correct workings of the data capturing functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.8
 * @since 1.0.0
 */
class DataCapturingTest {
    /**
     * A mocked Android `SensorManager`.
     */
    @Mock
    private val mockedSensorService: SensorManager? = null

    /**
     * A mocked Android `LocationManager`.
     */
    @Mock
    private val mockedLocationManager: LocationManager? = null

    /**
     * A mocked test location.
     */
    @Mock
    private val location: Location? = null

    /**
     * A mocked listener for capturing events.
     */
    @Mock
    private val listener: CapturingProcessListener? = null

    /**
     * A mocked `HandlerThread` for events occurring on new locations.
     */
    @Mock
    private val locationEventHandler: HandlerThread? = null

    /**
     * A mocked `HandlerThread` for events occurring on new sensor values.
     */
    @Mock
    private val sensorEventHandler: HandlerThread? = null

    /**
     * A mocked [BuildVersionProvider] for version check in [CapturingProcess].
     */
    @Mock
    private val mockedBuildVersionProvider: BuildVersionProvider? = null

    /**
     * The status handler for the geo location device.
     */
    private var locationStatusHandler: GeoLocationDeviceStatusHandler? = null

    /**
     * Initializes mocks and the status handler.
     */
    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        locationStatusHandler = object : GeoLocationDeviceStatusHandler(mockedLocationManager) {
            override fun shutdown() {
            }
        }
    }

    /**
     * Tests if `CapturingProcessListener` is correctly informed about new geo locations.
     */
    @Test
    fun testSuccessfulDataCapturing() {
        Mockito.`when`(mockedBuildVersionProvider!!.isOreoAndAbove).thenReturn(true)
        Mockito.`when`(location!!.time).thenReturn(0L)
        Mockito.`when`(location.latitude).thenReturn(51.03624633)
        Mockito.`when`(location.longitude).thenReturn(13.78828128)
        Mockito.`when`(location.hasAltitude()).thenReturn(true)
        Mockito.`when`(location.altitude).thenReturn(400.123)
        Mockito.`when`(location.speed).thenReturn(0.0f)
        Mockito.`when`(location.accuracy).thenReturn(5f)
        Mockito.`when`(location.hasVerticalAccuracy()).thenReturn(true)
        Mockito.`when`(location.verticalAccuracyMeters).thenReturn(20f)
        GeoLocationCapturingProcess(
            mockedLocationManager,
            mockedSensorService,
            locationStatusHandler,
            locationEventHandler,
            sensorEventHandler,
            100
        ).use { dataCapturing ->
            dataCapturing.setBuildVersionProvider(mockedBuildVersionProvider)
            dataCapturing.addCapturingProcessListener(listener!!)
            locationStatusHandler!!.handleFirstFix()
            dataCapturing.onLocationChanged(location)
            Mockito.verify(listener).onLocationCaptured(
                ParcelableGeoLocation(0L, 51.03624633, 13.78828128, 400.123, 0.0, 5.0, 20.0)
            )
        }
    }

    /**
     * Tests whether a point captured event is successfully issued after two location points and one
     * satellite status event are received and the two location points occurred in short succession.
     * Usually below 2 seconds.
     */
    @Test
    fun testDataCapturingInterval() {
        Mockito.`when`(location!!.time).thenReturn(0L)
        Mockito.`when`(location.latitude).thenReturn(1.0)
        Mockito.`when`(location.longitude).thenReturn(1.0)
        Mockito.`when`(location.speed).thenReturn(0.0f)
        Mockito.`when`(location.accuracy).thenReturn(0.0f)
        GeoLocationCapturingProcess(
            mockedLocationManager,
            mockedSensorService,
            locationStatusHandler,
            locationEventHandler,
            sensorEventHandler,
            100
        ).use { dataCapturing ->
            dataCapturing.addCapturingProcessListener(listener!!)
            dataCapturing.onLocationChanged(location)
            dataCapturing.onLocationChanged(location)
            locationStatusHandler!!.handleFirstFix()
            dataCapturing.onLocationChanged(location)
            Mockito.verify(listener, Mockito.times(1)).onLocationCaptured(
                ArgumentMatchers.any(
                    ParcelableGeoLocation::class.java
                )
            )
        }
    }
}