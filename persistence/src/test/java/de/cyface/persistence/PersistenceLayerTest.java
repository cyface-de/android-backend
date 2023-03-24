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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

import static de.cyface.persistence.TestUtils.AUTHORITY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.hardware.SensorManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.ParcelablePressure;
import de.cyface.persistence.model.Pressure;
import de.cyface.persistence.model.Track;

/**
 * Tests the inner workings of the {@link DefaultPersistenceLayer}.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 6.3.0
 */
@RunWith(AndroidJUnit4.class)
public class PersistenceLayerTest {

    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private DefaultPersistenceLayer<DefaultPersistenceBehaviour> oocut;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        oocut = new DefaultPersistenceLayer<>(context, AUTHORITY, new DefaultPersistenceBehaviour());
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
        final var p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
        final var track = new Track();
        // noise around 0 (+-1)
        track.addPressure(new Pressure(1L, pressure(0., p0), 1L));
        track.addPressure(new Pressure(2L, pressure(1., p0), 1L));
        track.addPressure(new Pressure(3L, pressure(-1., p0), 1L));
        track.addPressure(new Pressure(4L, pressure(0., p0), 1L));
        track.addPressure(new Pressure(5L, pressure(1., p0), 1L));
        // ascend 1 => +3
        // 3.03 as pressure to altitude calculation is not 100% accurate and would
        // fail because the ascend would be slightly below the threshold
        track.addPressure(new Pressure(6L, pressure(3.01, p0), 1L));
        // descend => lastAltitude -= 2
        track.addPressure(new Pressure(7L, pressure(1., p0), 1L));
        // ascend 2 => +2
        track.addPressure(new Pressure(8L, pressure(3.01, p0), 1L));
        // Track without ascend but with data should return 0.0 not null
        final var track2 = new Track();
        track2.addPressure(new Pressure(1L, pressure(0., p0), 1L));
        track2.addPressure(new Pressure(2L, pressure(1., p0), 1L));
        track2.addPressure(new Pressure(3L, pressure(-1., p0), 1L));

        // Act
        final Double ascend = oocut.ascendFromPressures(Collections.singletonList(track), 1);
        final Double ascend2 = oocut.ascendFromPressures(Collections.singletonList(track2), 1);

        // Assert
        assertThat(ascend, is(closeTo(5., 0.02)));
        assertThat(ascend2, is(closeTo(0., 0.02)));
    }

    @Test
    public void testLoadAscendFromGnss() {

        // Arrange
        final var track = new Track();
        // noise around 0 (+-1)
        track.addLocation(new GeoLocation(1L, 0., 0., 0., 1., 5., 5., 1L));
        track.addLocation(new GeoLocation(2L, 0., 0., 1., 1., 5., 5., 1L));
        track.addLocation(new GeoLocation(3L, 0., 0., -1., 1., 5., 5., 1L));
        track.addLocation(new GeoLocation(4L, 0., 0., 0., 1., 5., 5., 1L));
        track.addLocation(new GeoLocation(5L, 0., 0., 1., 1., 5., 5., 1L));
        // ascend 1 => +3
        track.addLocation(new GeoLocation(6L, 0., 0., 3., 1., 5., 5., 1L));
        // descend => lastAltitude -= 2
        track.addLocation(new GeoLocation(7L, 0., 0., 1., 1., 5., 5., 1L));
        // ascend 2 => +2
        track.addLocation(new GeoLocation(8L, 0., 0., 3., 1., 5., 5., 1L));
        // Track without ascend but with data should return 0.0 not null
        final var track2 = new Track();
        track2.addLocation(new GeoLocation(1L, 0., 0., 0., 1., 5., 5., 1L));
        track2.addLocation(new GeoLocation(2L, 0., 0., 1., 1., 5., 5., 1L));
        track2.addLocation(new GeoLocation(3L, 0., 0., -1., 1., 5., 5., 1L));

        // Act
        final Double ascend = oocut.ascendFromGNSS(Collections.singletonList(track));
        final Double ascend2 = oocut.ascendFromGNSS(Collections.singletonList(track2));

        // Assert
        assertThat(ascend, is(closeTo(5., 0.01)));
        assertThat(ascend2, is(closeTo(0., 0.01)));
    }

    @Test
    public void testCollectNextSubTrack() {

        // Arrange
        final var locations = new ArrayList<ParcelableGeoLocation>();
        locations.add(new ParcelableGeoLocation(1L, 0., 0., 0., 1., 5., 5.));
        locations.add(new ParcelableGeoLocation(2L, 0., 0., 0., 1., 5., 5.));
        locations.add(new ParcelableGeoLocation(10L, 0., 0., 0., 1., 5., 5.));
        locations.add(new ParcelableGeoLocation(11L, 0., 0., 0., 1., 5., 5.));
        final var pressures = new ArrayList<ParcelablePressure>();
        final var p0 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
        pressures.add(new ParcelablePressure(1L, pressure(0., p0)));
        pressures.add(new ParcelablePressure(2L, pressure(0., p0)));
        pressures.add(new ParcelablePressure(10L, pressure(0., p0)));
        pressures.add(new ParcelablePressure(11L, pressure(0., p0)));
        final var pauseEventTime = 3L;

        // Act
        final var subTrack = oocut.collectNextSubTrack(locations, pressures, pauseEventTime);

        // Assert
        assertThat(subTrack.getGeoLocations().size(), is(equalTo(2)));
        assertThat(subTrack.getPressures().size(), is(equalTo(2)));
        assertThat(locations.size(), is(equalTo(2)));
        assertThat(pressures.size(), is(equalTo(2)));
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
