package de.cyface.datacapturing.backend;

import static org.mockito.Matchers.any;
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

/**
 * Tests the correct workings of the data capturing functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.2
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
     * The status handler for the geo location device.
     */
    private GeoLocationDeviceStatusHandler gpsStatusHandler;

    /**
     * Initializes mocks and the status handler.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        gpsStatusHandler = new GeoLocationDeviceStatusHandler(mockedLocationManager) {
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
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(51.03624633);
        when(location.getLongitude()).thenReturn(13.78828128);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GPSCapturingProcess(mockedLocationManager, mockedSensorService,
                gpsStatusHandler, locationEventHandler, sensorEventHandler);) {
            dataCapturing.addCapturingProcessListener(listener);
            gpsStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener).onLocationCaptured(new GeoLocation(51.03624633, 13.78828128, 0L, 0.0, 0.0f));
        }
    }

    /**
     * Tests whether a point captured event is successfully issued after two gps points and one
     * satellite status event are received and the two gps points occured in short succession.
     * Usually below 2 seconds.
     */
    @Test
    public void testDataCapturingInterval() {
        when(location.getTime()).thenReturn(0L);
        when(location.getLatitude()).thenReturn(1.0);
        when(location.getLongitude()).thenReturn(1.0);
        when(location.getSpeed()).thenReturn(0.0f);
        when(location.getAccuracy()).thenReturn(0.0f);
        try (CapturingProcess dataCapturing = new GPSCapturingProcess(mockedLocationManager, mockedSensorService,
                gpsStatusHandler, locationEventHandler, sensorEventHandler);) {
            dataCapturing.addCapturingProcessListener(listener);
            dataCapturing.onLocationChanged(location);
            dataCapturing.onLocationChanged(location);
            gpsStatusHandler.handleFirstFix();
            dataCapturing.onLocationChanged(location);
            verify(listener, times(1)).onLocationCaptured(any(GeoLocation.class));
        }
    }
}