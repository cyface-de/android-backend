/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.persistence

import android.hardware.SensorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Pressure
import de.cyface.persistence.model.Track
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

/**
 * Tests the inner workings of the [DefaultPersistenceLayer].
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 6.3.0
 */
@RunWith(AndroidJUnit4::class)
class PersistenceLayerTest {
    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private var oocut: DefaultPersistenceLayer<DefaultPersistenceBehaviour>? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        oocut = DefaultPersistenceLayer(context, DefaultPersistenceBehaviour())
    }

    @After
    fun tearDown() {
        oocut!!.shutdown()
    }

    /**
     * Tests the formula to calculate pressure at a specific altitude used in this test.
     */
    @Test
    fun testPressure() {
        // Arrange
        val p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
        // Act
        val pressureAtSeaLevel = pressure(0.0, p0)
        val pressureAboveSeaLevel = pressure(10000.0, p0)
        val pressureBelowSeaLevel = pressure(-500.0, p0)
        // Assert
        MatcherAssert.assertThat(pressureAtSeaLevel, CoreMatchers.`is`(CoreMatchers.equalTo(p0)))
        MatcherAssert.assertThat(
            pressureAboveSeaLevel,
            CoreMatchers.`is`(CoreMatchers.equalTo(264.41486f))
        )
        MatcherAssert.assertThat(
            pressureBelowSeaLevel,
            CoreMatchers.`is`(CoreMatchers.equalTo(1074.7656f))
        )
    }

    @Test
    fun testAverages() {
        // Arrange
        val values: List<Double> = mutableListOf(0.0, 1.0, 0.0, 0.0, 0.0, 4.0, 1.0)
        // Act
        val averages = oocut!!.averages(values, 5)
        // Assert
        MatcherAssert.assertThat(
            averages,
            CoreMatchers.`is`(CoreMatchers.equalTo(mutableListOf(.2, 1.0, 1.0)))
        )
    }

    @Test
    fun testLoadAscendFromPressures() {
        // Arrange
        val p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
        val track = Track()
        // noise around 0 (+-1)
        track.addPressure(Pressure(0, 1L, pressure(0.0, p0).toDouble(), 1L))
        track.addPressure(Pressure(0, 2L, pressure(1.0, p0).toDouble(), 1L))
        track.addPressure(Pressure(0, 3L, pressure(-1.0, p0).toDouble(), 1L))
        track.addPressure(Pressure(0, 4L, pressure(0.0, p0).toDouble(), 1L))
        track.addPressure(Pressure(0, 5L, pressure(1.0, p0).toDouble(), 1L))
        // ascend 1 => +3
        // 3.03 as pressure to altitude calculation is not 100% accurate and would
        // fail because the ascend would be slightly below the threshold
        track.addPressure(Pressure(0, 6L, pressure(3.01, p0).toDouble(), 1L))
        // descend => lastAltitude -= 2
        track.addPressure(Pressure(0, 7L, pressure(1.0, p0).toDouble(), 1L))
        // ascend 2 => +2
        track.addPressure(Pressure(0, 8L, pressure(3.01, p0).toDouble(), 1L))
        // Track without ascend but with data should return 0.0 not null
        val track2 = Track()
        track2.addPressure(Pressure(0, 1L, pressure(0.0, p0).toDouble(), 1L))
        track2.addPressure(Pressure(0, 2L, pressure(1.0, p0).toDouble(), 1L))
        track2.addPressure(Pressure(0, 3L, pressure(-1.0, p0).toDouble(), 1L))

        // Act
        val altitudes = oocut!!.altitudesFromPressures(listOf(track), 1)
        val altitudes2 = oocut!!.altitudesFromPressures(listOf(track2), 1)
        val ascend = oocut!!.totalAscend(altitudes)
        val ascend2 = oocut!!.totalAscend(altitudes2)

        // Assert
        MatcherAssert.assertThat(ascend, CoreMatchers.`is`(Matchers.closeTo(5.0, 0.02)))
        MatcherAssert.assertThat(ascend2, CoreMatchers.`is`(Matchers.closeTo(0.0, 0.02)))
    }

    @Test
    fun testLoadAscendFromGnss() {
        // Arrange
        val track = Track()
        // noise around 0 (+-1)
        track.addLocation(GeoLocation(0, 1L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        track.addLocation(GeoLocation(0, 2L, 0.0, 0.0, 1.0, 1.0, 5.0, 5.0, 1L))
        track.addLocation(GeoLocation(0, 3L, 0.0, 0.0, -1.0, 1.0, 5.0, 5.0, 1L))
        track.addLocation(GeoLocation(0, 4L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        track.addLocation(GeoLocation(0, 5L, 0.0, 0.0, 1.0, 1.0, 5.0, 5.0, 1L))
        // ascend 1 => +3
        track.addLocation(GeoLocation(0, 6L, 0.0, 0.0, 3.0, 1.0, 5.0, 5.0, 1L))
        // descend => lastAltitude -= 2
        track.addLocation(GeoLocation(0, 7L, 0.0, 0.0, 1.0, 1.0, 5.0, 5.0, 1L))
        // ascend 2 => +2
        track.addLocation(GeoLocation(0, 8L, 0.0, 0.0, 3.0, 1.0, 5.0, 5.0, 1L))
        // Track without ascend but with data should return 0.0 not null
        val track2 = Track()
        track2.addLocation(GeoLocation(0, 1L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        track2.addLocation(GeoLocation(0, 2L, 0.0, 0.0, 1.0, 1.0, 5.0, 5.0, 1L))
        track2.addLocation(GeoLocation(0, 3L, 0.0, 0.0, -1.0, 1.0, 5.0, 5.0, 1L))

        // Act
        val altitudes = oocut!!.altitudesFromGNSS(listOf(track))
        val altitudes2 = oocut!!.altitudesFromGNSS(listOf(track2))
        val ascend = oocut!!.totalAscend(altitudes)
        val ascend2 = oocut!!.totalAscend(altitudes2)

        // Assert
        MatcherAssert.assertThat(ascend, CoreMatchers.`is`(Matchers.closeTo(5.0, 0.01)))
        MatcherAssert.assertThat(ascend2, CoreMatchers.`is`(Matchers.closeTo(0.0, 0.01)))
    }

    @Test
    fun testCollectNextSubTrack() {
        // Arrange
        val locations = ArrayList<GeoLocation?>()
        locations.add(GeoLocation(0, 1L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        locations.add(GeoLocation(0, 2L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        locations.add(GeoLocation(0, 10L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        locations.add(GeoLocation(0, 11L, 0.0, 0.0, 0.0, 1.0, 5.0, 5.0, 1L))
        val pressures = ArrayList<Pressure?>()
        val p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
        pressures.add(Pressure(0, 1L, pressure(0.0, p0).toDouble(), 1L))
        pressures.add(Pressure(0, 2L, pressure(0.0, p0).toDouble(), 1L))
        pressures.add(Pressure(0, 10L, pressure(0.0, p0).toDouble(), 1L))
        pressures.add(Pressure(0, 11L, pressure(0.0, p0).toDouble(), 1L))
        val pauseEventTime = 3L

        // Act
        val subTrack = oocut!!.collectNextSubTrack(locations, pressures, pauseEventTime)

        // Assert
        MatcherAssert.assertThat(
            subTrack.geoLocations.size,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
        MatcherAssert.assertThat(
            subTrack.pressures.size,
            CoreMatchers.`is`(CoreMatchers.equalTo(2))
        )
        MatcherAssert.assertThat(locations.size, CoreMatchers.`is`(CoreMatchers.equalTo(2)))
        MatcherAssert.assertThat(pressures.size, CoreMatchers.`is`(CoreMatchers.equalTo(2)))
    }

    /**
     * Calculates the pressure expected for a specific altitude and weather condition.
     *
     * Based on the formula from `android.hardware.SensorManager#getAltitude(float, float)`.
     *
     * @param altitude The altitude to calculate the pressure for in meters above sea level.
     * @param p0 The pressure at sea level for the specific weather condition.
     * @return The atmospheric pressure in hPa.
     */
    private fun pressure(altitude: Double, @Suppress("SameParameterValue") p0: Float): Float {
        return p0 * (1.0f - altitude / 44330.0f).pow(5.255).toFloat()
    }
}
