package de.cyface.datacapturing.model;

import static android.content.ContentValues.TAG;
import static de.cyface.datacapturing.ServiceTestUtils.AUTHORITY;
import static de.cyface.datacapturing.ServiceTestUtils.getAccelerationsUri;
import static de.cyface.datacapturing.ServiceTestUtils.getDirectionsUri;
import static de.cyface.datacapturing.ServiceTestUtils.getGeoLocationsUri;
import static de.cyface.datacapturing.ServiceTestUtils.getMeasurementUri;
import static de.cyface.datacapturing.ServiceTestUtils.getRotationsUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;

/**
 * Tests whether captured data is correctly saved to the underlying content provider. This test uses
 * <code>ProviderTestRule</code> to get a mocked content provider. Implementation details are explained in the
 * <a href="https://developer.android.com/reference/android/support/test/rule/provider/ProviderTestRule">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @version 4.0.3
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CapturedDataWriterTest {
    /**
     * Test rule that provides a mock connection to a <code>ContentProvider</code> to test against.
     */
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(MeasuringPointsContentProvider.class, AUTHORITY)
            .build();
    /**
     * The object of the class under test.
     */
    private MeasurementPersistence oocut;
    /**
     * An Android <code>ContentResolver</code> provided for executing tests.
     */
    private ContentResolver mockResolver;

    /**
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     */
    @Before
    public void setUp() {
        mockResolver = providerRule.getResolver();

        oocut = new MeasurementPersistence(mockResolver, AUTHORITY);
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    public void tearDown() {
        oocut.clear();
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     */
    @Test
    public void testCreateNewMeasurement() {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(measurement.getIdentifier() >= 0L, is(equalTo(true)));
        String identifierString = Long.valueOf(measurement.getIdentifier()).toString();
        Log.d(TAG, identifierString);

        Cursor result = null;
        try {
            result = mockResolver.query(getMeasurementUri(), null, BaseColumns._ID + "=?",
                    new String[] {identifierString}, null);
            if (result == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKNOWN.getDatabaseIdentifier())));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_FINISHED)), is(equalTo(0)));

        } finally {
            if (result != null) {
                result.close();
            }
        }

        oocut.closeRecentMeasurement();
        Cursor closingResult = null;
        try {
            closingResult = mockResolver.query(getMeasurementUri(), null, BaseColumns._ID + "=?",
                    new String[] {identifierString}, null);
            if (closingResult == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(closingResult.getCount(), is(equalTo(1)));
            assertThat(closingResult.moveToFirst(), is(equalTo(true)));

            assertThat(closingResult.getString(closingResult.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKNOWN.getDatabaseIdentifier())));
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
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback callback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };

        oocut.storeData(testData(), measurement.getIdentifier(), callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement.getIdentifier());

        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;

        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(), null, null, null, null);
            accelerationsCursor = mockResolver.query(getAccelerationsUri(), null, null, null, null);
            directionsCursor = mockResolver.query(getDirectionsUri(), null, null, null, null);
            rotationsCursor = mockResolver.query(getRotationsUri(), null, null, null, null);
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
        oocut.newMeasurement(Vehicle.UNKNOWN);
        Measurement measurement = oocut.newMeasurement(Vehicle.CAR);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback finishedCallback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };

        oocut.storeData(testData(), measurement.getIdentifier(), finishedCallback);
        oocut.storeLocation(testLocation(), measurement.getIdentifier());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // clear the test data
        int removedRows = oocut.clear();
        assertThat(removedRows, is(equalTo(12)));

        // make sure nothing is left.
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;
        Cursor measurementsCursor = null;

        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(), null,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.toString(measurement.getIdentifier())}, null);
            accelerationsCursor = mockResolver.query(getAccelerationsUri(), null,
                    AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.toString(measurement.getIdentifier())}, null);
            directionsCursor = mockResolver.query(getDirectionsUri(), null,
                    DirectionPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.toString(measurement.getIdentifier())}, null);
            rotationsCursor = mockResolver.query(getRotationsUri(), null,
                    RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.toString(measurement.getIdentifier())}, null);
            measurementsCursor = mockResolver.query(getMeasurementUri(), null, null, null, null);
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
     *
     * @throws NoSuchMeasurementException If the test measurement was null for some reason. This should only happen if
     *             there was a very serious database error.
     */
    @Test
    public void testLoadMeasurements() throws NoSuchMeasurementException {
        oocut.newMeasurement(Vehicle.UNKNOWN);
        oocut.newMeasurement(Vehicle.CAR);

        List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(2)));

        for (Measurement measurement : loadedMeasurements) {
            oocut.delete(measurement);
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     *
     * @throws NoSuchMeasurementException If the test measurement was null for some reason. This should only happen if
     *             there was a very serious database error.
     */
    @Test
    public void testDeleteMeasurement() throws NoSuchMeasurementException {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();

        WritingDataCompletedCallback callback = new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                lock.lock();
                try {
                    condition.signal();
                } finally {
                    lock.unlock();
                }
            }
        };
        oocut.storeData(testData(), measurement.getIdentifier(), callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        oocut.storeLocation(testLocation(), measurement.getIdentifier());
        oocut.delete(measurement);

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));

        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor directionsCursor = null;
        Cursor rotationsCursor = null;

        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(), null,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            accelerationsCursor = mockResolver.query(getAccelerationsUri(), null,
                    AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            directionsCursor = mockResolver.query(getDirectionsUri(), null,
                    DirectionPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            rotationsCursor = mockResolver.query(getRotationsUri(), null,
                    RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
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
     *
     * @throws NoSuchMeasurementException if the created measurement is null for some unexpected reason.
     */
    @Test
    public void testLoadTrack() throws NoSuchMeasurementException {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        oocut.storeLocation(testLocation(), measurement.getIdentifier());
        List<Measurement> measurements = oocut.loadMeasurements();
        assertThat(measurements.size(), is(equalTo(1)));
        for (Measurement loadedMeasurement : measurements) {
            assertThat(oocut.loadTrack(loadedMeasurement).size(), is(equalTo(1)));
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
