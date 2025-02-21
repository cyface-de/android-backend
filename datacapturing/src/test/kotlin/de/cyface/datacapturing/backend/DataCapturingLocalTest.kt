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

import android.os.Build
import android.os.Parcelable
import de.cyface.datacapturing.EventHandlingStrategy
import de.cyface.datacapturing.MessageCodes
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.ParcelablePoint3D
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.persistence.strategy.DistanceCalculationStrategy
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.testutils.SharedTestUtils.generateGeoLocation
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Spy
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random
import kotlin.math.abs

/**
 * Tests the inner workings of the [DataCapturingBackgroundService] without any calls to the Android system. Uses
 * fake data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.5
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1]) // To be able to execute tests with Java 8 (instead of 9)
class DataCapturingLocalTest {

    /**
     * We require Mockito to avoid calling Android system functions. This rule is responsible for the initialization of
     * the Spies and Mocks.
     */
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    /**
     * The object of the class under test
     */
    @Spy
    var oocut: DataCapturingBackgroundService? = null

    /**
     * Mocking the persistence layer to avoid calling Android system functions.
     */
    @Spy
    var mockPersistence: PersistenceLayer<CapturingPersistenceBehaviour>? = null

    /**
     * Mocking the persistence behaviour to avoid calling Android system functions.
     */
    @Mock
    var mockBehaviour: CapturingPersistenceBehaviour? = null

    @Mock
    var distanceCalculationStrategy: DistanceCalculationStrategy? = null

    @Mock
    var locationCleaningStrategy: LocationCleaningStrategy? = null

    @Mock
    var mockEventHandlingStrategy: EventHandlingStrategy? = null
    private val base = 0
    private val location1 = generateGeoLocation(base, 1L)

    @Before
    fun setUp() {

        // Replace attributes of DataCapturingBackgroundService with mocked objects
        oocut!!.persistenceLayer = mockPersistence!!
        oocut!!.capturingBehaviour = mockBehaviour
        oocut!!.eventHandlingStrategy = mockEventHandlingStrategy
        oocut!!.distanceCalculationStrategy = distanceCalculationStrategy
        oocut!!.locationCleaningStrategy = locationCleaningStrategy
        oocut!!.startupTime = location1.timestamp // locations with a smaller timestamp are filtered
    }

    /**
     * This test case checks the internal workings of the onLocationCaptured method.
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testOnLocationCapturedDistanceCalculation() {

        // Arrange
        val expectedDistance = 2
        val location2 = generateGeoLocation(base + expectedDistance)
        val location3 = generateGeoLocation(base + 2 * expectedDistance)

        // Mock
        Mockito.`when`(distanceCalculationStrategy!!.calculateDistance(location1, location2))
            .thenReturn(java.lang.Double.valueOf(expectedDistance.toDouble()))
        Mockito.`when`(distanceCalculationStrategy!!.calculateDistance(location2, location3))
            .thenReturn(java.lang.Double.valueOf(expectedDistance.toDouble()))
        Mockito.`when`(
            locationCleaningStrategy!!.isClean(
                ArgumentMatchers.any(
                    ParcelableGeoLocation::class.java
                )
            )
        ).thenReturn(true)
        Mockito.doNothing().`when`(oocut)!!.informCaller(
            ArgumentMatchers.anyInt(), ArgumentMatchers.any(
                Parcelable::class.java
            )
        )

        // Act
        oocut!!.onLocationCaptured(location1)
        oocut!!.onLocationCaptured(location2) // On second call a distance should be calculated
        oocut!!.onLocationCaptured(location3) // Now the two distances should be added

        // Assert
        Mockito.verify(mockBehaviour, Mockito.times(1))!!
            .updateDistance(expectedDistance.toDouble())
        Mockito.verify(mockBehaviour, Mockito.times(1))!!
            .updateDistance((2 * expectedDistance).toDouble())
    }

    /**
     * This test case checks the internal workings of the onLocationCaptured method in the special case
     * where a cached location with a timestamp smaller than the start time of the background service is returned.
     *
     * Those "cached" locations are filtered by the background service (STAD-140).
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testOnLocationCapturedDistanceCalculation_withCachedLocation() {

        // Arrange
        val expectedDistance = 2
        val cachedLocation = generateGeoLocation(base - expectedDistance, 0L)
        val location2 = generateGeoLocation(base + expectedDistance, 1L)
        val location3 = generateGeoLocation(base + 2 * expectedDistance, 2L)

        // Mock
        // When the onLocationCaptured implementation is correct, this method is never called.
        // But we need to keep this mock or else this test won't fail when the startupTime filter is missing
        Mockito.`when`(distanceCalculationStrategy!!.calculateDistance(cachedLocation, location1))
            .thenReturn(java.lang.Double.valueOf(expectedDistance.toDouble()))
        Mockito.`when`(distanceCalculationStrategy!!.calculateDistance(location1, location2))
            .thenReturn(java.lang.Double.valueOf(expectedDistance.toDouble()))
        Mockito.`when`(distanceCalculationStrategy!!.calculateDistance(location2, location3))
            .thenReturn(java.lang.Double.valueOf(expectedDistance.toDouble()))
        Mockito.`when`(
            locationCleaningStrategy!!.isClean(
                ArgumentMatchers.any(
                    ParcelableGeoLocation::class.java
                )
            )
        ).thenReturn(true)
        Mockito.doNothing().`when`(oocut)!!.informCaller(
            ArgumentMatchers.anyInt(), ArgumentMatchers.any(
                Parcelable::class.java
            )
        )

        // Act
        oocut!!.onLocationCaptured(cachedLocation)
        oocut!!.onLocationCaptured(location1)
        oocut!!.onLocationCaptured(location2) // On second call a distance should be calculated
        oocut!!.onLocationCaptured(location3) // Now the two distances should be added

        // Assert
        Mockito.verify(mockBehaviour, Mockito.times(1))!!
            .updateDistance(expectedDistance.toDouble())
        Mockito.verify(mockBehaviour, Mockito.times(1))!!
            .updateDistance((2 * expectedDistance).toDouble())
        Mockito.verify(mockBehaviour, Mockito.times(0))!!
            .updateDistance((3 * expectedDistance).toDouble())
    }

    /**
     * Tests if splitting large data sets works as intended. This is required to avoid the infamous
     * `TransactionTooLargeException`.
     */
    @Test
    fun testSplitOfLargeCapturedDataInstances() {
        val someLargeOddNumber = 1247
        val random = Random()
        val accelerationsSize = someLargeOddNumber * 2
        // noinspection UnnecessaryLocalVariable - because this is better readable
        val directionsSize = someLargeOddNumber / 2
        val pressuresSize = someLargeOddNumber / 2
        val accelerations: MutableList<ParcelablePoint3D> = ArrayList(accelerationsSize)
        val rotations: MutableList<ParcelablePoint3D> = ArrayList(
            someLargeOddNumber
        )
        val directions: MutableList<ParcelablePoint3D> = ArrayList(directionsSize)
        val pressures: MutableList<ParcelablePressure> = ArrayList(pressuresSize)

        // Create some random test data.
        for (i in 0 until accelerationsSize) {
            accelerations.add(
                ParcelablePoint3D(
                    abs(random.nextLong()), random.nextFloat(), random.nextFloat(),
                    random.nextFloat()
                )
            )
        }
        for (i in 0 until someLargeOddNumber) {
            rotations.add(
                ParcelablePoint3D(
                    abs(random.nextLong()), random.nextFloat(), random.nextFloat(),
                    random.nextFloat()
                )
            )
        }
        for (i in 0 until directionsSize) {
            directions.add(
                ParcelablePoint3D(
                    abs(random.nextLong()), random.nextFloat(), random.nextFloat(),
                    random.nextFloat()
                )
            )
        }
        for (i in 0 until pressuresSize) {
            val validPressure = (250L + (Math.random() * 850).toLong()).toDouble()
            pressures.add(ParcelablePressure(abs(random.nextLong()), validPressure))
        }
        val data = CapturedData(accelerations, rotations, directions, pressures)
        val captor = ArgumentCaptor.forClass(
            CapturedData::class.java
        )

        // Hide call to actual Android message service methods.
        Mockito.doNothing().`when`(oocut)!!.informCaller(
            ArgumentMatchers.eq(MessageCodes.DATA_CAPTURED), ArgumentMatchers.any(
                CapturedData::class.java
            )
        )

        // Call test method.
        oocut!!.onDataCaptured(data)

        // 1247*2 / 800 = 3,1 --> 4
        // noinspection ConstantConditions
        val maxSensorSize = accelerationsSize.coerceAtLeast(
            someLargeOddNumber.coerceAtLeast(directionsSize.coerceAtLeast(pressuresSize))
        )
        var times =
            maxSensorSize / DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE
        val remainder =
            maxSensorSize % DataCapturingBackgroundService.MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE
        // noinspection ConstantConditions
        times = if (remainder > 0) ++times else times
        Mockito.verify(oocut, Mockito.times(times))!!
            .informCaller(ArgumentMatchers.eq(MessageCodes.DATA_CAPTURED), captor.capture())
        var receivedAccelerations = 0
        var receivedRotations = 0
        var receivedDirections = 0
        var receivedPressures = 0
        for (dataFromCall in captor.allValues) {
            receivedAccelerations += dataFromCall.accelerations.size
            receivedRotations += dataFromCall.rotations.size
            receivedDirections += dataFromCall.directions.size
            receivedPressures += dataFromCall.pressures.size
        }
        MatcherAssert.assertThat(
            receivedAccelerations,
            Matchers.`is`(Matchers.equalTo(accelerationsSize))
        )
        MatcherAssert.assertThat(
            receivedRotations, Matchers.`is`(
                Matchers.equalTo(
                    someLargeOddNumber
                )
            )
        )
        MatcherAssert.assertThat(
            receivedDirections,
            Matchers.`is`(Matchers.equalTo(directionsSize))
        )
        MatcherAssert.assertThat(receivedPressures, Matchers.`is`(Matchers.equalTo(pressuresSize)))
    }
}