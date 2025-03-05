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
package de.cyface.synchronization

import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.strategy.DefaultDistanceCalculation
import de.cyface.persistence.strategy.DistanceCalculationStrategy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests the internal workings of [ExampleLocationAnonymization].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.14.0
 */
class ExampleLocationAnonymizationTest {

    private lateinit var distanceCalculationStrategy: DistanceCalculationStrategy
    private lateinit var anonymization: ExampleLocationAnonymization

    @Before
    fun setUp() {
        distanceCalculationStrategy = DefaultDistanceCalculation()
        val radius = { 15.0 + Math.random() * 1.0 } // Set radius to 15-16 meters
        anonymization = ExampleLocationAnonymization(distanceCalculationStrategy, radius)
    }

    @Test
    fun testAnonymize_RemovesStartAndEndWithinRadius() {
        val track = Track(
            geoLocations = mutableListOf(
                // Should be removed
                GeoLocation(0L, 1L, 51.0001, 13.0001, null, 1.0, null, null, 1L),
                GeoLocation(0L, 1L, 51.0002, 13.0002, null, 1.0, null, null, 1L),

                // Should not be removed
                GeoLocation(0L, 1L, 51.0003, 13.0003, null, 1.0, null, null, 1L),

                // Should be removed
                GeoLocation(0L, 1L, 51.0004, 13.0004, null, 1.0, null, null, 1L),
                GeoLocation(0L, 1L, 51.0005, 13.0005, null, 1.0, null, null, 1L),
            ),
            pressures = mutableListOf()
        )

        val trimmedTrack = anonymization.anonymize(track)

        assertEquals(1, trimmedTrack.geoLocations.size)
        assertEquals(
            GeoLocation(0L, 1L, 51.0003, 13.0003, null, 1.0, null, null, 1L),
            trimmedTrack.geoLocations[0]
        )
    }
}
