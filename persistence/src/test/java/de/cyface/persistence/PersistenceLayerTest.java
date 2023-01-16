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
package de.cyface.persistence;

import static de.cyface.persistence.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.SensorManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.model.PersistedGeoLocation;
import de.cyface.persistence.model.PersistedPressure;
import de.cyface.persistence.model.TrackV6;

/**
 * Tests the inner workings of the {@link PersistenceLayer}.
 *
 * FIXME: Add tests with multiple sub-tracks
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@RunWith(AndroidJUnit4.class)
public class PersistenceLayerTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private PersistenceLayer<DefaultPersistenceBehaviour> oocut;
    /**
     * A mock content resolver provided by the Android test environment to work on a simulated content provider.
     */
    @Mock
    private ContentResolver mockResolver;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        oocut = new PersistenceLayer<>(context, mockResolver, AUTHORITY, new DefaultPersistenceBehaviour());
    }

    @After
    public void tearDown() {
        oocut.shutdown();
    }

    /**
     * Tests the formula to calculate pressure at a specific altitude used in this test.
     */
    @Test
    public void testPressure() {
        // Arrange
        final float p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
        // Act
        final float pressureAtSeaLevel = pressure(0., p0);
        final float pressureAboveSeaLevel = pressure(10_000., p0);
        final float pressureBelowSeaLevel = pressure(-500., p0);
        // Assert
        assertThat(pressureAtSeaLevel, is(equalTo(p0)));
        assertThat(pressureAboveSeaLevel, is(equalTo(264.41486f)));
        assertThat(pressureBelowSeaLevel, is(equalTo(1074.7656f)));
    }

    @Test
    public void testAverages() {
        // Arrange
        final List<Double> values = Arrays.asList(0., 1., 0., 0., 0., 4., 1.);
        // Act
        final List<Double> averages = oocut.averages(values, 5);
        // Assert
        assertThat(averages, is(equalTo(Arrays.asList(.2, 1., 1.))));
    }

    @Test
    public void testLoadAscendFromPressures() {

        // Arrange
        final float p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
        final TrackV6 trackV6 = new TrackV6();
        // noise around 0 (+-1)
        trackV6.addPressure(new PersistedPressure(1L, pressure(0., p0), 1L));
        trackV6.addPressure(new PersistedPressure(2L, pressure(1., p0), 1L));
        trackV6.addPressure(new PersistedPressure(3L, pressure(-1., p0), 1L));
        trackV6.addPressure(new PersistedPressure(4L, pressure(0., p0), 1L));
        trackV6.addPressure(new PersistedPressure(5L, pressure(1., p0), 1L));
        // ascend 1 => +3
        // 3.03 as pressure to altitude calculation is not 100% accurate and would
        // fail because the ascend would be slightly below the threshold
        trackV6.addPressure(new PersistedPressure(6L, pressure(3.01, p0), 1L));
        // descend => lastAltitude -= 2
        trackV6.addPressure(new PersistedPressure(7L, pressure(1., p0), 1L));
        // ascend 2 => +2
        trackV6.addPressure(new PersistedPressure(8L, pressure(3.01, p0), 1L));
        // Track without ascend but with data should return 0.0 not null
        final TrackV6 track2 = new TrackV6();
        track2.addPressure(new PersistedPressure(1L, pressure(0., p0), 1L));
        track2.addPressure(new PersistedPressure(2L, pressure(1., p0), 1L));
        track2.addPressure(new PersistedPressure(3L, pressure(-1., p0), 1L));

        // Act
        final Double ascend = oocut.ascendFromPressures(Collections.singletonList(trackV6));
        final Double ascend2 = oocut.ascendFromPressures(Collections.singletonList(track2));

        // Assert
        assertThat(ascend, is(closeTo(5., 0.02)));
        assertThat(ascend2, is(closeTo(0., 0.02)));
    }

    @Test
    public void testLoadAscendFromGnss() {

        // Arrange
        final TrackV6 trackV6 = new TrackV6();
        // noise around 0 (+-1)
        trackV6.addLocation(new PersistedGeoLocation(1L, 0., 0., 0., 1., 5., 5., 1L));
        trackV6.addLocation(new PersistedGeoLocation(2L, 0., 0., 1., 1., 5., 5., 1L));
        trackV6.addLocation(new PersistedGeoLocation(3L, 0., 0., -1., 1., 5., 5., 1L));
        trackV6.addLocation(new PersistedGeoLocation(4L, 0., 0., 0., 1., 5., 5., 1L));
        trackV6.addLocation(new PersistedGeoLocation(5L, 0., 0., 1., 1., 5., 5., 1L));
        // ascend 1 => +3
        trackV6.addLocation(new PersistedGeoLocation(6L, 0., 0., 3., 1., 5., 5., 1L));
        // descend => lastAltitude -= 2
        trackV6.addLocation(new PersistedGeoLocation(7L, 0., 0., 1., 1., 5., 5., 1L));
        // ascend 2 => +2
        trackV6.addLocation(new PersistedGeoLocation(8L, 0., 0., 3., 1., 5., 5., 1L));
        // Track without ascend but with data should return 0.0 not null
        final TrackV6 track2 = new TrackV6();
        track2.addLocation(new PersistedGeoLocation(1L, 0., 0., 0., 1., 5., 5., 1L));
        track2.addLocation(new PersistedGeoLocation(2L, 0., 0., 1., 1., 5., 5., 1L));
        track2.addLocation(new PersistedGeoLocation(3L, 0., 0., -1., 1., 5., 5., 1L));

        // Act
        final Double ascend = oocut.ascendFromGNSS(Collections.singletonList(trackV6));
        final Double ascend2 = oocut.ascendFromGNSS(Collections.singletonList(track2));

        // Assert
        assertThat(ascend, is(closeTo(5., 0.01)));
        assertThat(ascend2, is(closeTo(0., 0.01)));
    }

    /**
     * Calculates the pressure expected for a specific altitude and weather condition.
     * <p>
     * Based on the formula from {@code android.hardware.SensorManager#getAltitude(float, float)}.
     *
     * @param altitude The altitude to calculate the pressure for in meters above sea level.
     * @param p0 The pressure at sea level for the specific weather condition.
     * @return The atmospheric pressure in hPa.
     */
    @SuppressWarnings("SameParameterValue")
    private float pressure(double altitude, float p0) {
        return p0 * (float)Math.pow(1.0f - altitude / 44330.0f, 5.255f);
    }
}
