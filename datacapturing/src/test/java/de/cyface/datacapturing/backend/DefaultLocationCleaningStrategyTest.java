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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.model.GeoLocation;

/**
 * Tests that the {@link GeoLocation}s are filtered as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 4.0.0
 */
public class DefaultLocationCleaningStrategyTest {

    /**
     * The object of the class under test
     */
    private DefaultLocationCleaningStrategy locationCleaningStrategy;

    @Before
    public void setUp() {
        locationCleaningStrategy = new DefaultLocationCleaningStrategy();
    }

    /**
     * Tests if the accuracy filter for {@link GeoLocation}s works as expected.
     */
    @Test
    public void testIsClean_accuracyFilter() {

        // Arrange
        final GeoLocation locationWithGoodEnoughAccuracy = new GeoLocation(51.1, 13.1,
                1000000000L, 5.0, 1999f);
        final GeoLocation locationWithJustTooBadAccuracy = new GeoLocation(51.1, 13.1,
                1000000000L, 5.0, 2000f);
        // Mock - nothing to do

        // Act
        final boolean isGood = locationCleaningStrategy.isClean(locationWithGoodEnoughAccuracy);
        final boolean isBad = locationCleaningStrategy.isClean(locationWithJustTooBadAccuracy);

        // Assert
        Assert.assertThat(isGood, is(equalTo(true)));
        Assert.assertThat(isBad, is(equalTo(false)));
    }

    /**
     * Tests if the speed filter for {@link GeoLocation}s works as expected.
     */
    @Test
    public void testIsClean_speedFilter() {

        // Arrange
        final GeoLocation locationWithHighEnoughSpeed = new GeoLocation(51.1, 13.1,
                1000000000L, 1.0, 1999f);
        final GeoLocation locationWithJustTooLowSpeed = new GeoLocation(51.1, 13.1,
                1000000000L, 0.99, 1999f);
        final GeoLocation locationWithJustTooHighSpeed = new GeoLocation(51.1, 13.1,
                1000000000L, 100.01, 1999f);
        // Mock - nothing to do

        // Act
        final boolean isHighEnough = locationCleaningStrategy.isClean(locationWithHighEnoughSpeed);
        final boolean isTooLow = locationCleaningStrategy.isClean(locationWithJustTooLowSpeed);
        final boolean isTooHigh = locationCleaningStrategy.isClean(locationWithJustTooHighSpeed);

        // Assert
        Assert.assertThat(isHighEnough, is(equalTo(true)));
        Assert.assertThat(isTooLow, is(equalTo(false)));
        Assert.assertThat(isTooHigh, is(equalTo(false)));
    }
}
