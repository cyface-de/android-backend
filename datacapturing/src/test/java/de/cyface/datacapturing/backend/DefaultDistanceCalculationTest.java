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
import org.robolectric.annotation.Config;

import android.location.Location;
import android.os.Build;

import de.cyface.persistence.strategies.DefaultDistanceCalculation;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.Measurement;

/**
 * Tests that the {@link Measurement#getDistance()} is calculated as expected.
 * <p>
 * This has to be an integration test as we use Android's {@link Location} class for distance calculation.
 *
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 3.2.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.O_MR1) // To be able to execute tests with Java 8 (instead of 9)
public class DefaultDistanceCalculationTest {

    /**
     * The object of the class under test
     */
    private DefaultDistanceCalculation distanceCalculationStrategy;

    @Before
    public void setUp() {
        distanceCalculationStrategy = new DefaultDistanceCalculation();
    }

    /**
     * Tests if the distance between two {@link ParcelableGeoLocation}s is calculated as expected.
     */
    @Test
    public void testCalculateDistance() {
        // Arrange
        final int base = 0;
        final int expectedDistance = 2;
        final ParcelableGeoLocation previousLocation = generateGeoLocation(base);
        final ParcelableGeoLocation nextLocation = generateGeoLocation(base + expectedDistance);
        // Mock - nothing to do

        // Act
        final double distance = distanceCalculationStrategy.calculateDistance(previousLocation, nextLocation);

        // Assert
        assertEquals(expectedDistance, distance, 0.010);
    }
}
