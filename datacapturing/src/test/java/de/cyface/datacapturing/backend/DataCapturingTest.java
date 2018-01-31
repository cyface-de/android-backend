/*
 * Created at 10:20:45 on 09.02.2015
 */
package de.cyface.datacapturing.backend;

import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import de.cyface.datacapturing.model.CapturedData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p> Tests the correct workings of the data capturing functionality. </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class DataCapturingTest {

    @Mock
    private SensorManager mockedSensorService;
    @Mock
    private LocationManager mockedLocationManager;
    @Mock
    private Location location;
    @Mock
    private CapturingProcessListener listener;
    private GeoLocationDeviceStatusHandler gpsStatusHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        gpsStatusHandler = new GeoLocationDeviceStatusHandler() {
            @Override
            void shutdown() {

            }
        };
    }

    @Test
    public void testSuccessfulDataCapturing() throws Exception {
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(51.03624633);
        when(location.getLongitude()).thenReturn(13.78828128);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GPSCapturingProcess(mockedLocationManager, mockedSensorService, gpsStatusHandler);){
            dataCapturing.addCapturingProcessListener(listener);
            gpsStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener).onPointCaptured(new CapturedData(51.03624633, 13.78828128, 0L, 0.0, 0, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
        }
    }

    /**
     * <p> Tests whether a point captured event is successfully issued after two gps points and one
     * satellite status event are received and the two gps points occured in short succession.
     * Usually below 2 seconds. </p>
     *
     * @throws Exception If anything went wrong.
     */
    @Test
    public void testDataCapturingInterval() throws Exception {
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(1.0);
        when(location.getLongitude()).thenReturn(1.0);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GPSCapturingProcess(mockedLocationManager, mockedSensorService, gpsStatusHandler);){
            dataCapturing.addCapturingProcessListener(listener);
            dataCapturing.onLocationChanged(location);
            dataCapturing.onLocationChanged(location);
            gpsStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener, times(1)).onPointCaptured(any(CapturedData.class));
        }
    }
}