/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.model;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TAG;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getIdentifierUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
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

import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.FileAccessLayer;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.NoSuchFileException;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.testutils.SharedTestUtils;
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
 * @version 5.5.2
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
    private PersistenceLayer<CapturingPersistenceBehaviour> oocut;
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
    private final static int TEST_LOCATION_COUNT = 1;
    private final static int TEST_DATA_COUNT = 3;

    /**
     * Initializes the test case as explained in the <a href=
     * "https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build">Android
     * documentation</a>.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Before
    public void setUp() throws CursorIsNullException {
        mockResolver = providerRule.getResolver();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        SharedTestUtils.clearPersistenceLayer(context, context.getContentResolver(), AUTHORITY);
        this.capturingBehaviour = new CapturingPersistenceBehaviour();
        oocut = new PersistenceLayer<>(context, mockResolver, AUTHORITY, capturingBehaviour);
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        oocut.restoreOrCreateDeviceId();
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    public void tearDown() {
        clearPersistenceLayer(context, mockResolver, AUTHORITY);
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     */
    @Test
    public void testCreateNewMeasurement() throws NoSuchMeasurementException, CursorIsNullException {

        // Create a measurement
        Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);
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

            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_MODALITY)),
                    is(equalTo(Modality.UNKNOWN.getDatabaseIdentifier())));
            assertThat(result.getString(result.getColumnIndex(MeasurementTable.COLUMN_STATUS)),
                    is(equalTo(MeasurementStatus.OPEN.getDatabaseIdentifier())));
            assertThat(result.getShort(result.getColumnIndex(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION)),
                    is(equalTo(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION)));
            assertThat(result.getDouble(result.getColumnIndex(MeasurementTable.COLUMN_DISTANCE)), is(equalTo(0.0)));

        } finally {
            if (result != null) {
                result.close();
            }
        }

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
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

            assertThat(finishingResult.getString(finishingResult.getColumnIndex(MeasurementTable.COLUMN_MODALITY)),
                    is(equalTo(Modality.UNKNOWN.getDatabaseIdentifier())));
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
    public void testStoreData() throws NoSuchFileException {
        // Manually trigger data capturing (new measurement with sensor data and a location)
        Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);

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

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
                measurement.getIdentifier());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        capturingBehaviour.storeLocation(testLocation(1L), measurement.getIdentifier());

        // Check if the captured data was persisted
        Cursor geoLocationsCursor = null;
        FileAccessLayer fileAccessLayer = new DefaultFileAccess();
        try {
            // GeoLocations
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null, null, null, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    geoLocationsCursor);
            assertThat(geoLocationsCursor.getCount(), is(equalTo(TEST_LOCATION_COUNT)));

            // Point3ds
            Point3dFile accelerationsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            Point3dFile rotationsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            Point3dFile directionsFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);

            List<Point3d> accelerations = deserialize(fileAccessLayer, accelerationsFile.getFile(), TEST_DATA_COUNT);
            List<Point3d> rotations = deserialize(fileAccessLayer, rotationsFile.getFile(), TEST_DATA_COUNT);
            List<Point3d> directions = deserialize(fileAccessLayer, directionsFile.getFile(), TEST_DATA_COUNT);

            assertThat(accelerations.size(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(rotations.size(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(directions.size(), is(equalTo(TEST_DATA_COUNT)));
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
        oocut.newMeasurement(Modality.UNKNOWN);
        Measurement measurement = oocut.newMeasurement(Modality.CAR);

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
        final int testEvents = 2;
        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), finishedCallback);

        oocut.storePersistenceFileFormatVersion(MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
                measurement.getIdentifier());

        capturingBehaviour.storeLocation(testLocation(1L), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // clear the test data
        int removedEntries = clearPersistenceLayer(context, mockResolver, AUTHORITY);
        // final int testIdentifierTableCount = 1; - currently not deleted at the end of tests because this breaks
        // the life-cycle DataCapturingServiceTests
        assertThat(removedEntries, is(equalTo(testMeasurementsWithPoint3dFiles * point3dFilesPerMeasurement
                + TEST_LOCATION_COUNT + testMeasurements /* + testIdentifierTableCount */ + testEvents)));

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
            assertThat(identifierCursor.getCount(), is(equalTo(1))); // because we don't clean it up currently
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
     */
    @Test
    public void testLoadMeasurements() throws CursorIsNullException {
        oocut.newMeasurement(Modality.UNKNOWN);
        oocut.newMeasurement(Modality.CAR);

        List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(2)));

        for (Measurement measurement : loadedMeasurements) {
            oocut.delete(measurement.getIdentifier());
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     */
    @Test
    public void testDeleteMeasurement() throws CursorIsNullException {

        // Arrange
        Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);
        final long measurementId = measurement.getIdentifier();

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

        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeData(testData(), measurementId, callback);
        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        // Act
        oocut.delete(measurement.getIdentifier());

        // Assert
        final File accelerationFile = oocut.getFileAccessLayer().getFilePath(context, measurementId,
                Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = oocut.getFileAccessLayer().getFilePath(context, measurementId,
                Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        final File directionFile = oocut.getFileAccessLayer().getFilePath(context, measurementId,
                Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
        assertThat(!accelerationFile.exists(), is(true));
        assertThat(!rotationFile.exists(), is(true));
        assertThat(!directionFile.exists(), is(true));

        Cursor geoLocationsCursor = null;
        try {
            geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null,
                    GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.",
                    geoLocationsCursor);

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
        }

        Cursor eventsCursor = null;
        try {
            eventsCursor = mockResolver.query(getEventUri(AUTHORITY), null, EventTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null);
            Validate.notNull("Test failed because it was unable to load data from the content provider.", eventsCursor);

            assertThat(eventsCursor.getCount(), is(equalTo(0)));
        } finally {
            if (eventsCursor != null) {
                eventsCursor.close();
            }
        }

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     * <p>
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseResumeStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);

        // Start event and 2 locations
        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getIdentifier());
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getIdentifier());

        // Pause event and a slightly late 3rd location
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, 3L);
        capturingBehaviour.storeLocation(testLocation(4L), measurement.getIdentifier());

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour.storeLocation(testLocation(5L), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_RESUME, measurement, 6L);
        capturingBehaviour.storeLocation(testLocation(6L), measurement.getIdentifier());

        // Stop event and a lightly late 2nd location
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, 7L);
        capturingBehaviour.storeLocation(testLocation(8L), measurement.getIdentifier());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(1).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().get(1).getTimestamp(), is(equalTo(2L)));
        assertThat(tracks.get(1).getGeoLocations().get(0).getTimestamp(), is(equalTo(6L)));
        assertThat(tracks.get(1).getGeoLocations().get(1).getTimestamp(), is(equalTo(8L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     * <p>
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseResumePauseStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);

        // Start event and 2 locations
        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getIdentifier());
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getIdentifier());

        // Pause event and a slightly late 3rd location
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, 3L);
        capturingBehaviour.storeLocation(testLocation(4L), measurement.getIdentifier());

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour.storeLocation(testLocation(5L), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_RESUME, measurement, 6L);
        // The first location may be capturing at the same millisecond (tried to reproduce MOV-676)
        capturingBehaviour.storeLocation(testLocation(6L), measurement.getIdentifier());

        // Pause event and a slightly late 2nd location
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, 7L);
        capturingBehaviour.storeLocation(testLocation(8L), measurement.getIdentifier());

        // Stop event and a lightly late location
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, 9L);
        capturingBehaviour.storeLocation(testLocation(10L), measurement.getIdentifier());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(1).getGeoLocations().size(), is(equalTo(3)));
        assertThat(tracks.get(1).getGeoLocations().get(0).getTimestamp(), is(equalTo(6L)));
        assertThat(tracks.get(1).getGeoLocations().get(1).getTimestamp(), is(equalTo(8L)));
        assertThat(tracks.get(1).getGeoLocations().get(2).getTimestamp(), is(equalTo(10L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);

        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getIdentifier());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, System.currentTimeMillis());
        // It's possible that GeoLocations arrive just after capturing was paused
        final long timestamp = System.currentTimeMillis();
        capturingBehaviour.storeLocation(testLocation(timestamp), measurement.getIdentifier());

        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(3)));
        assertThat(tracks.get(0).getGeoLocations().get(2).getTimestamp(), is(equalTo(timestamp)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);

        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis());
        // It's possible that GeoLocations arrive just after stop method was triggered
        final long timestamp = System.currentTimeMillis();
        capturingBehaviour.storeLocation(testLocation(timestamp), measurement.getIdentifier());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().get(1).getTimestamp(), is(equalTo(timestamp)));
    }

    /**
     * Tests whether loading a cleaned track of {@link GeoLocation}s returns the expected filtered locations.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadCleanedTrack() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(Modality.UNKNOWN);
        final long startTime = 1000000000L;
        final GeoLocation locationWithJustTooBadAccuracy = new GeoLocation(51.1, 13.1,
                startTime + 1, 5.0, 2000f);
        final GeoLocation locationWithJustTooLowSpeed = new GeoLocation(51.1, 13.1,
                startTime + 2, 1.0, 500f);
        final GeoLocation locationWithHighEnoughSpeed = new GeoLocation(51.1, 13.1,
                startTime + 3, 1.01, 500f);
        final GeoLocation locationWithGoodEnoughAccuracy = new GeoLocation(51.1, 13.1,
                startTime + 10, 5.0, 1999f);
        final GeoLocation locationWithJustTooHighSpeed = new GeoLocation(51.1, 13.1,
                startTime + 11, 100.0, 500f);

        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, startTime);
        capturingBehaviour.storeLocation(locationWithJustTooBadAccuracy, measurement.getIdentifier());
        capturingBehaviour.storeLocation(locationWithJustTooLowSpeed, measurement.getIdentifier());
        capturingBehaviour.storeLocation(locationWithHighEnoughSpeed, measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, startTime + 4);
        oocut.logEvent(Event.EventType.LIFECYCLE_RESUME, measurement, startTime + 9);
        capturingBehaviour.storeLocation(locationWithGoodEnoughAccuracy, measurement.getIdentifier());
        capturingBehaviour.storeLocation(locationWithJustTooHighSpeed, measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, startTime + 12);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> cleanedTracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier(),
                new DefaultLocationCleaningStrategy());

        // Assert
        assertThat(cleanedTracks.size(), is(equalTo(2)));
        assertThat(cleanedTracks.get(0).getGeoLocations().size(), is(equalTo(1)));
        assertThat(cleanedTracks.get(0).getGeoLocations().get(0), is(equalTo(locationWithHighEnoughSpeed)));
        assertThat(cleanedTracks.get(1).getGeoLocations().size(), is(equalTo(1)));
        assertThat(cleanedTracks.get(1).getGeoLocations().get(0), is(equalTo(locationWithGoodEnoughAccuracy)));
    }

    @Test
    public void testProvokeAnr() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!oocut.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut.newMeasurement(Modality.BICYCLE);
                    }
                    if (!oocut.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut.newMeasurement(Modality.BICYCLE);
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
     * @param timestamp The timestamp in milliseconds since 1970 to use for the {@link GeoLocation}
     * @return An initialized {@code GeoLocation} object with garbage data for testing.
     */
    private GeoLocation testLocation(final long timestamp) {
        return new GeoLocation(1.0, 1.0, timestamp, 1.0, 1);
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
