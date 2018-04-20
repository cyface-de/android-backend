/*
 * Created on 12.08.15 at 16:31
 */
package de.cyface.datacapturing.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.ProviderTestCase2;
import android.util.Log;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * Tests whether captured data is correctly saved to the underlying content provider. This test uses
 * <code>ProviderTestCase2</code> to get a mocked content provider. Implementation details are explained in the
 * <a href="https://developer.android.com/training/testing/integration-testing/content-provider-testing.html">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @version 2.1.0
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CapturedDataWriterTest extends ProviderTestCase2<MeasuringPointsContentProvider> {

    /**
     * The tag used to identify log messages send via Logcat.
     */
    private final static String TAG = "de.cyface.test";

    /**
     * The object of the class under test.
     */
    private MeasurementPersistence oocut;

    /**
     * The Constructor which needs to be overwritten for classes inheriting from <code>ProviderTestCase2</code>.
     */
    public CapturedDataWriterTest() {
        super(MeasuringPointsContentProvider.class, de.cyface.persistence.BuildConfig.provider);
    }

    /**
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     *
     * @throws Exception For further details see the <a href=
     *             "https://developer.android.com/reference/android/test/ProviderTestCase2.html">documentation</a> of
     *             the parent class <code>ProviderTestCase2</code>.
     */
    @Before
    public void setUp() throws Exception {
        // WARNING: Never change the order of the following two lines, even though the Google documentation tells you
        // something different!
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();

        oocut = new MeasurementPersistence(getMockContentResolver());
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     *
     * @throws Exception Fur further details see the <a href=
     *             "https://developer.android.com/reference/android/test/ProviderTestCase2.html">documentation</a> of
     *             the parent class <code>ProviderTestCase2</code>.
     */
    @After
    public void tearDown() throws Exception {
        List<Measurement> measurements = oocut.loadMeasurements();
        for (Measurement measurement : measurements) {
            oocut.delete(measurement);
        }
        super.tearDown();
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     */
    @Test
    public void testCreateNewMeasurement() {
        long identifier = oocut.newMeasurement(Vehicle.UNKOWN);
        assertThat(identifier >= 0L, is(equalTo(true)));
        String identifierString = Long.valueOf(identifier).toString();
        Log.d(TAG, identifierString);

        Cursor result = null;
        try {
            result = getMockContentResolver().query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    BaseColumns._ID + "=?", new String[] {identifierString}, null);
            if (result == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKOWN.getDatabaseIdentifier())));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_FINISHED)), is(equalTo(0)));

        } finally {
            if (result != null) {
                result.close();
            }
        }

        int numberOfClosedMeasurements = oocut.closeRecentMeasurement();
        assertThat(numberOfClosedMeasurements > 0, is(equalTo(true)));
        Cursor closingResult = null;
        try {
            closingResult = getMockContentResolver().query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    BaseColumns._ID + "=?", new String[] {identifierString}, null);
            if (closingResult == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(closingResult.getCount(), is(equalTo(1)));
            assertThat(closingResult.moveToFirst(), is(equalTo(true)));

            assertThat(closingResult.getString(closingResult.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKOWN.getDatabaseIdentifier())));
            assertThat(closingResult.getInt(closingResult.getColumnIndex(MeasurementTable.COLUMN_FINISHED)),
                    is(equalTo(1)));
        } finally {
            if (closingResult != null) {
                closingResult.close();
            }
        }
    }

    /**
     * Tests whether data is stored correctly via the <code>MeasurementPersistence</code>.
     */
    @Test
    public void testStoreData() {
        long measurementIdentifier = oocut.newMeasurement(Vehicle.UNKOWN);

        oocut.storeData(testData(), measurementIdentifier);
        oocut.storeLocation(testLocation(), measurementIdentifier);

        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;

        try {
            geoLocationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.GPS_POINTS_URI, null,
                    null, null, null);
            accelerationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null,
                    null, null, null);
            directionsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                    null, null, null, null);
            rotationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null,
                    null, null, null);
            if (geoLocationsCursor == null || accelerationsCursor == null || directionsCursor == null
                    || rotationsCursor == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from the content provider.");
            }

            assertThat(geoLocationsCursor.getCount(), is(equalTo(1)));
            assertThat(accelerationsCursor.getCount(), is(equalTo(3)));
            assertThat(directionsCursor.getCount(), is(equalTo(3)));
            assertThat(rotationsCursor.getCount(), is(equalTo(3)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (accelerationsCursor != null) {
                accelerationsCursor.close();
            }
            if (directionsCursor != null) {
                directionsCursor.close();
            }
            if (rotationsCursor != null) {
                rotationsCursor.close();
            }
        }
    }

    /**
     * Tests whether cascading deletion of measurements together with all data is working correctly.
     */
    @Test
    public void testCascadingClearMeasurements() {
        // Insert some test data
        oocut.newMeasurement(Vehicle.UNKOWN);
        long measurementIdnetifier = oocut.newMeasurement(Vehicle.CAR);
        oocut.storeData(testData(), measurementIdnetifier);
        oocut.storeLocation(testLocation(), measurementIdnetifier);
        // clear the test data
        oocut.clear();

        // make sure nothing is left.
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;
        Cursor measurementsCursor = null;

        try {
            geoLocationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.GPS_POINTS_URI, null,
                    null, null, null);
            accelerationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null,
                    null, null, null);
            directionsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                    null, null, null, null);
            rotationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null,
                    null, null, null);
            measurementsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    null, null, null);
            if (geoLocationsCursor == null || accelerationsCursor == null || directionsCursor == null
                    || rotationsCursor == null || measurementsCursor == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from the content provider.");
            }

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
            assertThat(accelerationsCursor.getCount(), is(equalTo(0)));
            assertThat(directionsCursor.getCount(), is(equalTo(0)));
            assertThat(rotationsCursor.getCount(), is(equalTo(0)));
            assertThat(measurementsCursor.getCount(), is(equalTo(0)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (accelerationsCursor != null) {
                accelerationsCursor.close();
            }
            if (directionsCursor != null) {
                directionsCursor.close();
            }
            if (rotationsCursor != null) {
                rotationsCursor.close();
            }
            if (measurementsCursor != null) {
                measurementsCursor.close();
            }
        }
    }

    /**
     * Tests whether loading measurements from the data storage via <code>MeasurementPersistence</code> is working as
     * expected.
     */
    @Test
    public void testLoadMeasurements() {
        oocut.newMeasurement(Vehicle.UNKOWN);
        oocut.newMeasurement(Vehicle.CAR);

        List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(2)));

        for (Measurement measurement : loadedMeasurements) {
            oocut.delete(measurement);
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     */
    @Test
    public void testDeleteMeasurement() {
        long measurementIdentifier = oocut.newMeasurement(Vehicle.UNKOWN);
        oocut.storeData(testData(), measurementIdentifier);
        oocut.storeLocation(testLocation(), measurementIdentifier);
        Measurement measurement = new Measurement(measurementIdentifier);
        oocut.delete(measurement);

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));

        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;

        try {
            geoLocationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.GPS_POINTS_URI, null,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            accelerationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null,
                    SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            directionsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                    null, MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            rotationsCursor = getMockContentResolver().query(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null,
                    RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
            if (geoLocationsCursor == null || accelerationsCursor == null || directionsCursor == null
                    || rotationsCursor == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from the content provider.");
            }

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
            assertThat(accelerationsCursor.getCount(), is(equalTo(0)));
            assertThat(directionsCursor.getCount(), is(equalTo(0)));
            assertThat(rotationsCursor.getCount(), is(equalTo(0)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (accelerationsCursor != null) {
                accelerationsCursor.close();
            }
            if (directionsCursor != null) {
                directionsCursor.close();
            }
            if (rotationsCursor != null) {
                rotationsCursor.close();
            }
        }
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link MeasurementPersistence} object.
     */
    @Test
    public void testLoadTrack() {
        long measurementIdentifier = oocut.newMeasurement(Vehicle.UNKOWN);
        oocut.storeLocation(testLocation(), measurementIdentifier);
        List<Measurement> measurements = oocut.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));
        for (Measurement measurement : measurements) {
            assertThat(oocut.loadTrack(measurement).size(), is(equalTo(1)));
        }
    }

    @Test
    public void testProvokeAnr() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!oocut.hasOpenMeasurement()) {
                        oocut.newMeasurement(Vehicle.BICYCLE);
                    }
                    if (!oocut.hasOpenMeasurement()) {
                        oocut.newMeasurement(Vehicle.BICYCLE);
                    }

                    if (oocut.hasOpenMeasurement()) {
                        oocut.closeRecentMeasurement();
                    }
                } catch (DataCapturingException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * @return An initialized {@link GeoLocation} object with garbage data for testing.
     */
    private GeoLocation testLocation() {
        return new GeoLocation(1.0, 1.0, 1L, 1.0, 1);
    }

    /**
     * @return An initialized {@link CapturedData} object with garbage data for testing.
     */
    private CapturedData testData() {
        List<Point3D> accelerations = new ArrayList<>();
        accelerations.add(new Point3D(1.0f, 1.0f, 1.0f, 1L));
        accelerations.add(new Point3D(2.0f, 2.0f, 2.0f, 2L));
        accelerations.add(new Point3D(3.0f, 3.0f, 3.0f, 3L));
        List<Point3D> directions = new ArrayList<>();
        directions.add(new Point3D(4.0f, 4.0f, 4.0f, 4L));
        directions.add(new Point3D(5.0f, 5.0f, 5.0f, 5L));
        directions.add(new Point3D(6.0f, 6.0f, 6.0f, 6L));
        List<Point3D> rotations = new ArrayList<>();
        rotations.add(new Point3D(7.0f, 7.0f, 7.0f, 7L));
        rotations.add(new Point3D(8.0f, 8.0f, 8.0f, 8L));
        rotations.add(new Point3D(9.0f, 9.0f, 9.0f, 9L));
        return new CapturedData(accelerations, rotations, directions);
    }
}