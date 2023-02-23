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

import de.cyface.persistence.DefaultLocationCleaning;
import de.cyface.persistence.model.ParcelableGeoLocation;

/**
 * Tests that the {@link ParcelableGeoLocation}s are filtered as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 4.0.0
 */
public class DefaultLocationCleaningTest {

    /**
     * The object of the class under test
     */
    private DefaultLocationCleaning locationCleaningStrategy;

    @Before
    public void setUp() {
        locationCleaningStrategy = new DefaultLocationCleaning();
    }

    /**
     * Tests if the accuracy filter for {@link ParcelableGeoLocation}s works as expected.
     */
    @Test
    public void testIsClean_accuracyFilter() {

        // Arrange
        final ParcelableGeoLocation locationWithGoodEnoughAccuracy = new ParcelableGeoLocation(1000000000L, 51.1, 13.1,
                400., 2., 19.99, 20.123);
        final ParcelableGeoLocation locationWithJustTooBadAccuracy = new ParcelableGeoLocation(1000000000L, 51.1, 13.1,
                400., 2., 20., 20.123);
        // Mock - nothing to do

        // Act
        final boolean isGood = locationCleaningStrategy.isClean(locationWithGoodEnoughAccuracy);
        final boolean isBad = locationCleaningStrategy.isClean(locationWithJustTooBadAccuracy);

        // Assert
        Assert.assertThat(isGood, is(equalTo(true)));
        Assert.assertThat(isBad, is(equalTo(false)));
    }

    /**
     * Tests if the speed filter for {@link ParcelableGeoLocation}s works as expected.
     */
    @Test
    public void testIsClean_speedFilter() {

        // Arrange
        final var locationWithHighEnoughSpeed = new ParcelableGeoLocation(1000000000L, 51.1, 13.1,
                400., 1.01, 5., 20.);
        final var locationWithJustTooLowSpeed = new ParcelableGeoLocation(1000000000L, 51.1, 13.1,
                400., 1., 5., 20.);
        final var locationWithJustTooHighSpeed = new ParcelableGeoLocation(1000000000L, 51.1, 13.1,
                400., 100., 5., 20.);
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
