/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import static de.cyface.testutils.SharedTestUtils.generateGeoLocation;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.location.Location;

import de.cyface.persistence.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.model.GeoLocation;

/**
 * Tests that the {@code Measurement#distance} is calculated as expected.
 * <p>
 * This has to be an integration test as we use Android's {@link Location} class for distance calculation.
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 3.2.0
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultDistanceCalculationStrategyTest {

    /**
     * The object of the class under test
     */
    private DefaultDistanceCalculationStrategy distanceCalculationStrategy;

    @Before
    public void setUp() {
        distanceCalculationStrategy = new DefaultDistanceCalculationStrategy();
    }

    /**
     * Tests if the distance between two {@link GeoLocation}s is calculated as expected.
     */
    @Test
    public void testCalculateDistance() {
        // Arrange
        final int base = 0;
        final int expectedDistance = 2;
        final GeoLocation previousLocation = generateGeoLocation(base);
        final GeoLocation nextLocation = generateGeoLocation(base + expectedDistance);
        // Mock - nothing to do

        // Act
        final double distance = distanceCalculationStrategy.calculateDistance(previousLocation, nextLocation);

        // Assert
        assertEquals(expectedDistance, distance, 0.010);
    }
}
