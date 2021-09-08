/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.serialization.proto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests the inner workings of the {@link Offsetter}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class OffsetterTest {

    private Offsetter oocut;

    @Before
    public void setUp() {
        oocut = new Offsetter();
    }

    @Test
    public void testOffset_forFirstNumber() {
        // Arrange

        // Act
        final long result = oocut.offset(1234567890123L);

        // Assert
        assertThat(result, is(equalTo(1234567890123L)));
    }

    @Test
    public void testOffset_forSubsequentNumbers() {
        // Arrange
        final long[] numbers = new long[] {1234567890123L, 1234567890223L, 1234567890123L};

        // Act
        final long result1 = oocut.offset(numbers[0]);
        final long result2 = oocut.offset(numbers[1]);
        final long result3 = oocut.offset(numbers[2]);

        // Assert
        assertThat(result1, is(equalTo(numbers[0])));
        assertThat(result2, is(equalTo(numbers[1] - numbers[0])));
        assertThat(result3, is(equalTo(numbers[2] - numbers[1])));
    }
}