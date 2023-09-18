/*
 * Copyright 2023 Cyface GmbH
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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * Tests the inner workings of the [DataCapturingBackgroundService].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.7.2
 */
class DataCapturingBackgroundServiceTest {

    @Test
    fun testIsCachedLocation() {
        // Arrange
        val startupTime = 1684825647148L

        // Act
        val validLocation = DataCapturingBackgroundService.isCachedLocation(startupTime + 1, startupTime)
        val cachedLocation = DataCapturingBackgroundService.isCachedLocation(startupTime - 1, startupTime)
        // See "week-rollover-GPS-bug" [STAD-515]
        val rollOverValid = DataCapturingBackgroundService.isCachedLocation(startupTime - 619315200000L + 1, startupTime)
        val rollOverInvalid = DataCapturingBackgroundService.isCachedLocation(startupTime - 619315200000L - 1, startupTime)

        // Assert
        assertThat(validLocation, equalTo(false))
        assertThat(cachedLocation, equalTo(true))
        assertThat(rollOverValid, equalTo(false))
        assertThat(rollOverInvalid, equalTo(true))
    }
}