/*
 * Copyright 2017-2021 Cyface GmbH
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.utils.Validate;

/**
 * Test cases to test the correct working of the data capturing process.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 2.0.0
 */
public class CapturingProcessTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    /**
     * An object of the class under test.
     */
    private CapturingProcess oocut;
    /**
     * A mock <code>SensorManager</code> for a real Android <code>SensorManager</code>.
     */
    @Mock
    private SensorManager sensorManager;
    /**
     * A mock <code>LocationManager</code> for a real Android <code>LocationManager</code>.
     */
    @Mock
    private LocationManager locationManager;
    /**
     * A mock for the thread handling occurrence of new geo locations.
     */
    @Mock
    private HandlerThread geoLocationEventHandlerThread;
    /**
     * A mock for the thread handling the occurrence of new sensor values.
     */
    @Mock
    private HandlerThread sensorEventHandlerThread;
    /**
     * A listener for the capturing process used to receive test events and assert against those events.
     */
    private TestCapturingProcessListener testListener;

    /**
     * Initializes all required properties and adds the <code>testListener</code> to the <code>CapturingProcess</code>.
     */
    @Before
    public void setUp() {
        oocut = new GeoLocationCapturingProcess(locationManager, sensorManager,
                new GeoLocationDeviceStatusHandler(locationManager) {
                    @Override
                    void shutdown() {

                    }

                    @Override
                    boolean hasLocationFix() {
                        return true;
                    }
                }, geoLocationEventHandlerThread, sensorEventHandlerThread, 100);
        testListener = new TestCapturingProcessListener();
        oocut.addCapturingProcessListener(testListener);
        final Sensor accelerometer = initSensor("accelerometer");
        when(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(accelerometer);
    }

    /**
     * Tests the happy path of capturing accelerometer data with 200 Hz and geo locations with 1 Hz.
     */
    @Test
    public void testCaptureSensorDataAlongWithGeoLocation() {

        Random random = new Random(System.currentTimeMillis());
        for (int i = 1; i <= 400; i++) {
            long currentTimestamp = Integer.valueOf(i).longValue() * 5L;
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            SensorEvent sensorEvent = createSensorEvent(accelerometer, random.nextFloat(), random.nextFloat(),
                    random.nextFloat(), currentTimestamp * 1_000_000L);
            oocut.onSensorChanged(sensorEvent);
            if ((i % 200) == 0) {
                Location location = mock(Location.class);
                when(location.getTime()).thenReturn(currentTimestamp);
                oocut.onLocationChanged(location);
            }
        }

        assertThat(testListener.getCapturedData(), Matchers.hasSize(2));
        assertThat(
                testListener.getCapturedData().get(0).getAccelerations().size()
                        + testListener.getCapturedData().get(1).getAccelerations().size(),
                Matchers.is(Matchers.equalTo(400)));
        assertThat(testListener.getCapturedLocations(), Matchers.hasSize(2));
    }

    /**
     * Tests that the correct `eventTimeOffset` is calculated for known `event.time` implementations.
     * <p>
     * Flaky: This test seems to be occasionally flaky on the Github CI (2022-03-17)
     */
    @Test
    public void testEventTimeOffset() {

        // Arrange
        final long eventDelayNanos = 9_000_000;
        final long elapsedRealTimeMillis = SystemClock.elapsedRealtime();
        final long currentTimeMillis = System.currentTimeMillis();
        final long eventTimeDefaultImplementation = elapsedRealTimeMillis - eventDelayNanos;
        final long eventTimeBasedOnCurrentTime = currentTimeMillis * 1_000_000 - eventDelayNanos;

        // Act
        final long eventTimeOffsetDefault = oocut.eventTimeOffset(eventTimeDefaultImplementation);
        final long eventTimeOffsetCurrentTime = oocut.eventTimeOffset(eventTimeBasedOnCurrentTime);

        // Arrange
        final long expectedEventTimeOffset = currentTimeMillis - elapsedRealTimeMillis; // bootTime
        final long expectedEventTimeOffsetCurrent = 0; // event.time equals currentTime of event
        assertThat(eventTimeOffsetDefault, is(equalTo(expectedEventTimeOffset)));
        assertThat(eventTimeOffsetCurrentTime, is(equalTo(expectedEventTimeOffsetCurrent)));
    }

    /**
     * A convenience method to ease the creation of new Android <code>SensorEvent</code> objects.
     *
     * @param sensor The sensor to create a new <code>SensorEvent</code> for. Refer for example to
     *            {@code SensorManager#getDefaultSensor(int)} to get a feeling for how to retrieve such an object from
     *            the Android API.
     * @param x The x coordinate for the new <code>SensorEvent</code>.
     * @param y The y coordinate for the new <code>SensorEvent</code>.
     * @param z The z coordinate for the new <code>SensorEvent</code>.
     * @param timestamp The timestamp of the new <code>SensorEvent</code> in nanoseconds.
     * @return The newly created and completely initialized <code>SensorEvent</code>.
     */
    private SensorEvent createSensorEvent(final @NonNull Sensor sensor, final float x, final float y, final float z,
            final long timestamp) {
        try {
            SensorEvent sensorEvent = Mockito.mock(SensorEvent.class);
            sensorEvent.sensor = sensor;

            Field valuesField = sensorEvent.getClass().getField("values");
            valuesField.setAccessible(true);
            float[] values = new float[] {x, y, z};
            valuesField.set(sensorEvent, values);

            Field timestampField = sensorEvent.getClass().getField("timestamp");
            timestampField.setAccessible(true);
            timestampField.set(sensorEvent, timestamp);

            return sensorEvent;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Initializes a sensor with the provided type and name.
     *
     * @param name The name of the sensor
     * @return The newly initialized <code>Sensor</code>.
     */
    @SuppressWarnings("SameParameterValue") // we probably want to implement this for other sensors too
    @NonNull
    private Sensor initSensor(@NonNull final String name) {
        Validate.notEmpty(name);

        Sensor sensor = Mockito.mock(Sensor.class);
        when(sensor.getName()).thenReturn(name);
        when(sensor.getVendor()).thenReturn("Cyface");
        return sensor;
    }

    /**
     * Listener reacting to events from the <code>CapturingProcess</code>. This listener provides accessors to check
     * whether the test did run correctly.
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 2.0.0
     * @since 2.0.0
     */
    private static class TestCapturingProcessListener implements CapturingProcessListener {

        /**
         * <code>GeoLocation</code> instances this listener was informed about.
         */
        private final List<ParcelableGeoLocation> capturedLocations = new ArrayList<>();

        /**
         * Captured sensor data this listener was informed about.
         */
        private final List<CapturedData> capturedData = new ArrayList<>();

        @Override
        public void onLocationCaptured(@NonNull ParcelableGeoLocation location) {
            capturedLocations.add(location);
        }

        @Override
        public void onDataCaptured(@NonNull CapturedData data) {
            capturedData.add(data);
        }

        @Override
        public void onLocationFix() {
            // nothing to do
        }

        @Override
        public void onLocationFixLost() {
            // nothing to do
        }

        /**
         * @return <code>GeoLocation</code> instances this listener was informed about.
         */
        List<ParcelableGeoLocation> getCapturedLocations() {
            return Collections.unmodifiableList(capturedLocations);
        }

        /**
         * @return Captured sensor data this listener was informed about.
         */
        List<CapturedData> getCapturedData() {
            return Collections.unmodifiableList(capturedData);
        }
    }
}
