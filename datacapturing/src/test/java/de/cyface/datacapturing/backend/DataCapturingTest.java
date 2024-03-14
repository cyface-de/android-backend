/*
 * Copyright 2017-2023 Cyface GmbH
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.HandlerThread;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.GeoLocationV6;

/**
 * Tests the correct workings of the data capturing functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.6
 * @since 1.0.0
 */
public class DataCapturingTest {

    /**
     * A mocked Android <code>SensorManager</code>.
     */
    @Mock
    private SensorManager mockedSensorService;
    /**
     * A mocked Android <code>LocationManager</code>.
     */
    @Mock
    private LocationManager mockedLocationManager;
    /**
     * A mocked test location.
     */
    @Mock
    private Location location;
    /**
     * A mocked listener for capturing events.
     */
    @Mock
    private CapturingProcessListener listener;
    /**
     * A mocked <code>HandlerThread</code> for events occurring on new locations.
     */
    @Mock
    private HandlerThread locationEventHandler;
    /**
     * A mocked <code>HandlerThread</code> for events occurring on new sensor values.
     */
    @Mock
    private HandlerThread sensorEventHandler;
    /**
     * A mocked {@link BuildVersionProvider} for version check in {@link CapturingProcess}.
     */
    @Mock
    private BuildVersionProvider mockedBuildVersionProvider;
    /**
     * The status handler for the geo location device.
     */
    private GeoLocationDeviceStatusHandler locationStatusHandler;

    /**
     * Initializes mocks and the status handler.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        locationStatusHandler = new GeoLocationDeviceStatusHandler(mockedLocationManager) {
            @Override
            void shutdown() {

            }
        };
    }

    /**
     * Tests if <code>CapturingProcessListener</code> is correctly informed about new geo locations.
     */
    @Test
    public void testSuccessfulDataCapturing() {
        when(mockedBuildVersionProvider.isOreoAndAbove()).thenReturn(true);
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(51.03624633);
        when(location.getLongitude()).thenReturn(13.78828128);
        when(location.hasAltitude()).thenReturn(true);
        when(location.getAltitude()).thenReturn(400.123);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        when(location.hasVerticalAccuracy()).thenReturn(true);
        when(location.getVerticalAccuracyMeters()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GeoLocationCapturingProcess(mockedLocationManager,
                mockedSensorService, locationStatusHandler, locationEventHandler, sensorEventHandler, 100);) {
            dataCapturing.setBuildVersionProvider(mockedBuildVersionProvider);
            dataCapturing.addCapturingProcessListener(listener);
            locationStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener).onLocationCaptured(new GeoLocation(51.03624633, 13.78828128, 0L, 0.0, 0.0f),
                    new GeoLocationV6(0L, 51.03624633, 13.78828128, 400.123, 0.0, 0.0f, 0.0));
        }
    }

    /**
     * Tests whether a point captured event is successfully issued after two location points and one
     * satellite status event are received and the two location points occurred in short succession.
     * Usually below 2 seconds.
     */
    @Test
    public void testDataCapturingInterval() {
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(1.0);
        when(location.getLongitude()).thenReturn(1.0);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GeoLocationCapturingProcess(mockedLocationManager,
                mockedSensorService, locationStatusHandler, locationEventHandler, sensorEventHandler, 100);) {
            dataCapturing.addCapturingProcessListener(listener);
            dataCapturing.onLocationChanged(location);
            dataCapturing.onLocationChanged(location);
            locationStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener, times(1)).onLocationCaptured(any(GeoLocation.class), any(GeoLocationV6.class));
        }
    }
}