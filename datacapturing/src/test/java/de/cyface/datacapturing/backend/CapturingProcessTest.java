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
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
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

/**
 * Test cases to test the correct working of the data capturing process.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=17) // Test currently only works for SDK 17 and below, due to bugs in Robolectric
public class CapturingProcessTest {
    /**
     * An object of the class under test.
     */
    private CapturingProcess oocut;
    /**
     * A Robolectric shadow for a real Android <code>SensorManager</code>.
     */
    private ShadowSensorManager shadowSensorManager;
    /**
     * A Robolectric shadow for a real Android <code>LocationManager</code>.
     */
    private ShadowLocationManager shadowLocationManager;
    /**
     * A listener for the capturing process used to receive test events and assert against those events.
     */
    private TestCapturingProcessListener testListener;

    /**
     * Initializes all required properties and adds the <code>testListener</code> to the <code>CapturingProcess</code>.
     */
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        LocationManager locationManager = (LocationManager)RuntimeEnvironment.application
                .getSystemService(Context.LOCATION_SERVICE);
        SensorManager sensorManager = (SensorManager)RuntimeEnvironment.application
                .getSystemService(Context.SENSOR_SERVICE);
        shadowSensorManager = shadowOf(sensorManager);

        initSensor(shadowSensorManager, Sensor.TYPE_ACCELEROMETER, "accelerometer");
        initSensor(shadowSensorManager, Sensor.TYPE_MAGNETIC_FIELD, "magnetometer");
        initSensor(shadowSensorManager, Sensor.TYPE_GYROSCOPE, "gyroscope");

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

    /**
     * Tests the happy path of capturing accelerometer data with 200 Hz and geo locations with 1 Hz.
     */
    @Test
    public void testCaptureSensorDataAlongWithGeoLocation() {

        Random random = new Random(System.currentTimeMillis());
        for (int i = 1; i <= 400; i++) {
            long currentTimestamp = Integer.valueOf(i).longValue() * 5L;
            SensorEvent sensorEvent = createSensorEvent(shadowSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    random.nextFloat(), random.nextFloat(), random.nextFloat(), currentTimestamp * 1_000_000L);
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

    private void initSensor(final @NonNull ShadowSensorManager shadowManager, final int sensorType, final @NonNull String name) throws NoSuchFieldException, IllegalAccessException {
        Sensor sensor = Shadow.newInstanceOf(Sensor.class);
        Field nameField = Sensor.class.getDeclaredField("mName");
        nameField.setAccessible(true);
        Field vendorField = Sensor.class.getDeclaredField("mVendor");
        vendorField.setAccessible(true);
        nameField.set(sensor, name);
        vendorField.set(sensor, "Cyface");
        shadowSensorManager.addSensor(sensorType, sensor);
    }

    /**
     * Listener reacting to events from the <code>CapturingProcess</code>. This listener provides accessors to check
     * whether the test did run correctly.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class TestCapturingProcessListener implements CapturingProcessListener {

        /**
         * <code>GeoLocation</code> instances this listener was informed about.
         */
        private List<GeoLocation> capturedLocations = new ArrayList<>();

        /**
         * Captured sensor data this listener was informed about.
         */
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

        /**
         * @return <code>GeoLocation</code> instances this listener was informed about.
         */
        public List<GeoLocation> getCapturedLocations() {
            return Collections.unmodifiableList(capturedLocations);
        }

        /**
         * @return Captured sensor data this listener was informed about.
         */
        public List<CapturedData> getCapturedData() {
            return Collections.unmodifiableList(capturedData);
        }
    }
}
