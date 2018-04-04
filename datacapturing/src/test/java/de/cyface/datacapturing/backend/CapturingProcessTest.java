package de.cyface.datacapturing.backend;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLocationManager;
import org.robolectric.shadows.ShadowSensorManager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;

@RunWith(RobolectricTestRunner.class)
public class CapturingProcessTest {

    private CapturingProcess oocut;

    private ShadowSensorManager shadowSensorManager;

    private ShadowLocationManager shadowLocationManager;

    private TestCapturingProcessListener testListener;

    @Before
    public void setUp() {
        LocationManager locationManager = (LocationManager)RuntimeEnvironment.application
                .getSystemService(Context.LOCATION_SERVICE);
        SensorManager sensorManager = (SensorManager)RuntimeEnvironment.application
                .getSystemService(Context.SENSOR_SERVICE);
        shadowSensorManager = shadowOf(sensorManager);
        Sensor accelerometer = Shadow.newInstanceOf(Sensor.class);
        shadowSensorManager.addSensor(Sensor.TYPE_ACCELEROMETER, accelerometer);
        Sensor gyroscope = Shadow.newInstanceOf(Sensor.class);
        shadowSensorManager.addSensor(Sensor.TYPE_GYROSCOPE, gyroscope);
        Sensor magnetometer = Shadow.newInstanceOf(Sensor.class);
        shadowSensorManager.addSensor(Sensor.TYPE_MAGNETIC_FIELD, magnetometer);
        shadowLocationManager = shadowOf(locationManager);
        oocut = new GPSCapturingProcess(locationManager, sensorManager,
                new GeoLocationDeviceStatusHandler(locationManager) {
                    @Override
                    void shutdown() {

                    }

                    @Override
                    boolean hasGpsFix() {
                        return true;
                    }
                });
        testListener = new TestCapturingProcessListener();
        oocut.addCapturingProcessListener(testListener);
    }

    @Test
    public void testCaptureSensorDataAlongWithGeoLocation() throws NoSuchFieldException {

        for (int i = 1; i <= 400; i++) {
            long currentTimestamp = Integer.valueOf(i).longValue() * 5L;
            SensorEvent sensorEvent = createSensorEvent(shadowSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    0.1F, 0.1F, 0.1F, currentTimestamp);
            oocut.onSensorChanged(sensorEvent);
            if ((i % 200) == 0) {
                Location location = new Location("");
                location.setTime(currentTimestamp);
                oocut.onLocationChanged(location);
            }
        }

        assertThat(testListener.getCapturedData(), hasSize(2));
        assertThat(testListener.getCapturedData().get(0).getAccelerations().size()
                + testListener.getCapturedData().get(1).getAccelerations().size(), is(equalTo(400)));
        assertThat(testListener.getCapturedLocations(), hasSize(2));
    }

    private SensorEvent createSensorEvent(final @NonNull Sensor sensor, final float x, final float y, final float z,
            final long timestamp) {
        try {
            SensorEvent sensorEvent = shadowSensorManager.createSensorEvent();
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

    private static class TestCapturingProcessListener implements CapturingProcessListener {

        private List<GeoLocation> capturedLocations = new ArrayList<>();

        private List<CapturedData> capturedData = new ArrayList<>();

        @Override
        public void onLocationCaptured(GeoLocation location) {
            capturedLocations.add(location);
        }

        @Override
        public void onDataCaptured(CapturedData data) {
            capturedData.add(data);
        }

        @Override
        public void onLocationFix() {

        }

        @Override
        public void onLocationFixLost() {

        }

        public List<GeoLocation> getCapturedLocations() {
            return Collections.unmodifiableList(capturedLocations);
        }

        public List<CapturedData> getCapturedData() {
            return Collections.unmodifiableList(capturedData);
        }
    }
}
