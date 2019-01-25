package de.cyface.datacapturing.model;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TAG;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getIdentifierUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.testutils.SharedTestUtils.clear;
import static de.cyface.testutils.SharedTestUtils.deserialize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.provider.ProviderTestRule;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests whether captured data is correctly saved to the underlying content provider. This test uses
 * <code>ProviderTestRule</code> to get a mocked content provider. Implementation details are explained in the
 * <a href="https://developer.android.com/reference/android/support/test/rule/provider/ProviderTestRule">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.3.0
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
    private PersistenceLayer oocut;
    /**
     * An Android <code>ContentResolver</code> provided for executing tests.
     */
    private ContentResolver mockResolver;
    /**
     * The {@link Context} required to access the persistence layer.
     */
    private Context context;
    /**
     * This {@link PersistenceBehaviour} is used to capture a {@link Measurement}s with when a {@link PersistenceLayer}.
     */
    private CapturingPersistenceBehaviour capturingBehaviour;
    private int testLocationCount = 1;
    private int testDataCount = 3;

    /**
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     */
    @Before
    public void setUp() throws CursorIsNullException {
        mockResolver = providerRule.getResolver();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        this.capturingBehaviour = new CapturingPersistenceBehaviour();
        oocut = new PersistenceLayer(context, mockResolver, AUTHORITY, capturingBehaviour);
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        oocut.restoreOrCreateDeviceId();
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    public void tearDown() {
        clear(context, mockResolver, AUTHORITY);
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     */
    @Test
    public void testCreateNewMeasurement() throws NoSuchMeasurementException, CursorIsNullException {

        // Create a measurement
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        assertThat(measurement.getIdentifier() > 0L, is(equalTo(true)));

        // Try to load the created measurement and check its properties
        String identifierString = Long.valueOf(measurement.getIdentifier()).toString();
        Log.d(TAG, identifierString);
        Cursor result = null;
        try {
            result = mockResolver.query(getMeasurementUri(AUTHORITY), null, BaseColumns._ID + "=?",
                    new String[] {identifierString}, null);
            if (result == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKNOWN.getDatabaseIdentifier())));
            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_STATUS)),
                    is(equalTo(MeasurementStatus.OPEN.getDatabaseIdentifier())));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_ACCELERATIONS)), is(equalTo(0)));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_ROTATIONS)), is(equalTo(0)));
            assertThat(result.getInt(result.getColumnIndex(MeasurementTable.COLUMN_DIRECTIONS)), is(equalTo(0)));
            assertThat(result.getShort(result.getColumnIndex(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION)),
                    is(equalTo(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION)));

        } finally {
            if (result != null) {
                result.close();
            }
        }

        // Store PointMetaData
        oocut.storePointMetaData(new PointMetaData(0, 0, 0, MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION),
                measurement.getIdentifier());

        // Finish the measurement
        capturingBehaviour.updateRecentMeasurement(FINISHED);

        // Load the finished measurement
        Cursor finishingResult = null;
        try {
            finishingResult = mockResolver.query(getMeasurementUri(AUTHORITY), null, BaseColumns._ID + "=?",
                    new String[] {identifierString}, null);
            if (finishingResult == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(finishingResult.getCount(), is(equalTo(1)));
            assertThat(finishingResult.moveToFirst(), is(equalTo(true)));

            assertThat(finishingResult.getString(finishingResult.getColumnIndex(MeasurementTable.COLUMN_VEHICLE)),
                    is(equalTo(Vehicle.UNKNOWN.getDatabaseIdentifier())));
            assertThat(finishingResult.getString(finishingResult.getColumnIndex(MeasurementTable.COLUMN_STATUS)),
                    is(equalTo(FINISHED.getDatabaseIdentifier())));
        } finally {
            if (finishingResult != null) {
                finishingResult.close();
            }
        }
    }

    /**
     * Tests whether data is stored correctly via the <code>PersistenceLayer</code>.
     */
    @Test
    public void testStoreData() {
        // Manually trigger data capturing (new measurement with sensor data and a location)
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

        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), callback);

        // Store PointMetaData
        oocut.storePointMetaData(new PointMetaData(testDataCount, testDataCount, testDataCount,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION), measurement.getIdentifier());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        capturingBehaviour.storeLocation(testLocation(), measurement.getIdentifier());

        // Check if the captured data was persisted
        Cursor geoLocationsCursor = null;
        FileAccessLayer fileAccessLayer = new DefaultFileAccess();
        try {
            // GeoLocations
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null, null, null, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    geoLocationsCursor);
            assertThat(geoLocationsCursor.getCount(), is(equalTo(testLocationCount)));

            // Point3ds
            Point3dFile accelerationsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            Point3dFile rotationsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            Point3dFile directionsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);

            List<Point3d> accelerations = deserialize(fileAccessLayer, accelerationsFile.getFile(), testDataCount);
            List<Point3d> rotations = deserialize(fileAccessLayer, rotationsFile.getFile(), testDataCount);
            List<Point3d> directions = deserialize(fileAccessLayer, directionsFile.getFile(), testDataCount);

            assertThat(accelerations.size(), is(equalTo(testDataCount)));
            assertThat(rotations.size(), is(equalTo(testDataCount)));
            assertThat(directions.size(), is(equalTo(testDataCount)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }
    }

    /**
     * Tests whether cascading deletion of measurements together with all data is working correctly.
     */
    @Test
    public void testCascadingClearMeasurements() {

        // Insert test measurements
        final int testMeasurements = 2;
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

        final int testMeasurementsWithPoint3dFiles = 1;
        final int point3dFilesPerMeasurement = 3;
        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), finishedCallback);

        // Store PointMetaData
        oocut.storePointMetaData(new PointMetaData(testDataCount, testDataCount, testDataCount,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION), measurement.getIdentifier());

        capturingBehaviour.storeLocation(testLocation(), measurement.getIdentifier());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // clear the test data
        int removedEntries = clear(context, mockResolver, AUTHORITY);
        final int testIdentifierTableCount = 1;
        assertThat(removedEntries, is(equalTo(testMeasurementsWithPoint3dFiles * point3dFilesPerMeasurement
                + testLocationCount + testMeasurements + testIdentifierTableCount)));

        // make sure nothing is left in the database
        Cursor geoLocationsCursor = null;
        Cursor measurementsCursor = null;
        Cursor identifierCursor = null;
        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null,
                    GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.toString(measurement.getIdentifier())}, null);
            measurementsCursor = mockResolver.query(getMeasurementUri(AUTHORITY), null, null, null, null);
            identifierCursor = mockResolver.query(getIdentifierUri(AUTHORITY), null, null, null, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    geoLocationsCursor);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    measurementsCursor);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    identifierCursor);

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
            assertThat(measurementsCursor.getCount(), is(equalTo(0)));
            assertThat(identifierCursor.getCount(), is(equalTo(0)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (measurementsCursor != null) {
                measurementsCursor.close();
            }
            if (identifierCursor != null) {
                identifierCursor.close();
            }
        }

        // Make sure nothing is left of the Point3dFiles
        final File accelerationsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3dFile.DIRECTIONS_FOLDER_NAME);
        assertThat(accelerationsFolder.exists(), is(equalTo(false)));
        assertThat(rotationsFolder.exists(), is(equalTo(false)));
        assertThat(directionsFolder.exists(), is(equalTo(false)));
    }

    /**
     * Tests whether loading {@link Measurement}s from the data storage via <code>PersistenceLayer</code> is
     * working as expected.
     *
     * @throws NoSuchMeasurementException If the test measurement was null for some reason. This should only happen if
     *             there was a very serious database error.
     */
    @Test
    public void testLoadMeasurements() throws NoSuchMeasurementException, CursorIsNullException {
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
    public void testDeleteMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
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
        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), callback);

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        capturingBehaviour.storeLocation(testLocation(), measurement.getIdentifier());
        oocut.delete(measurement);

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));

        Cursor geoLocationsCursor = null;
        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null,
                    GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    geoLocationsCursor);

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
            // FIXME: load and assert sensor data count?
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     *
     * @throws NoSuchMeasurementException if the created measurement is null for some unexpected reason.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack() throws NoSuchMeasurementException, DataCapturingException, CursorIsNullException {
        Measurement measurement = oocut.newMeasurement(Vehicle.UNKNOWN);
        capturingBehaviour.storeLocation(testLocation(), measurement.getIdentifier());
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
                    if (!oocut.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut.newMeasurement(Vehicle.BICYCLE);
                    }
                    if (!oocut.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut.newMeasurement(Vehicle.BICYCLE);
                    }

                    if (oocut.hasMeasurement(MeasurementStatus.OPEN)) {
                        capturingBehaviour.updateRecentMeasurement(FINISHED);
                    }
                } catch (final NoSuchMeasurementException | CursorIsNullException e) {
                    throw new IllegalStateException(e);
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
        List<Point3d> accelerations = new ArrayList<>();
        accelerations.add(new Point3d(1.0f, 1.0f, 1.0f, 1L));
        accelerations.add(new Point3d(2.0f, 2.0f, 2.0f, 2L));
        accelerations.add(new Point3d(3.0f, 3.0f, 3.0f, 3L));
        List<Point3d> directions = new ArrayList<>();
        directions.add(new Point3d(4.0f, 4.0f, 4.0f, 4L));
        directions.add(new Point3d(5.0f, 5.0f, 5.0f, 5L));
        directions.add(new Point3d(6.0f, 6.0f, 6.0f, 6L));
        List<Point3d> rotations = new ArrayList<>();
        rotations.add(new Point3d(7.0f, 7.0f, 7.0f, 7L));
        rotations.add(new Point3d(8.0f, 8.0f, 8.0f, 8L));
        rotations.add(new Point3d(9.0f, 9.0f, 9.0f, 9L));
        return new CapturedData(accelerations, rotations, directions);
    }
}
