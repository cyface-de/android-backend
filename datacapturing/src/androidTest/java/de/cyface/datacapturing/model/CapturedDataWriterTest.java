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
package de.cyface.datacapturing.model;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.datacapturing.TestUtils.TAG;
import static de.cyface.persistence.PersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.Utils.getEventUri;
import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getIdentifierUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.Modality.UNKNOWN;
import static de.cyface.serializer.model.Point3DType.ACCELERATION;
import static de.cyface.serializer.model.Point3DType.DIRECTION;
import static de.cyface.serializer.model.Point3DType.ROTATION;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.protobuf.InvalidProtocolBufferException;

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
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.ParcelablePoint3D;
import de.cyface.persistence.model.Pressure;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.NoSuchFileException;
import de.cyface.persistence.serialization.Point3DFile;
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
 * @version 5.6.1
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
        Measurement measurement = oocut.newMeasurement(UNKNOWN);
        assertThat(measurement.getIdentifier() > 0L, is(equalTo(true)));

        // Try to load the created measurement and check its properties
        String identifierString = Long.valueOf(measurement.getIdentifier()).toString();
        Log.d(TAG, identifierString);
        try (Cursor result = mockResolver.query(getMeasurementUri(AUTHORITY), null, BaseColumns._ID + "=?",
                new String[] {identifierString}, null)) {
            if (result == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(result.getCount(), is(equalTo(1)));
            assertThat(result.moveToFirst(), is(equalTo(true)));

            assertThat(result.getString(result.getColumnIndexOrThrow(MeasurementTable.COLUMN_MODALITY)),
                    is(equalTo(UNKNOWN.getDatabaseIdentifier())));
            assertThat(result.getString(result.getColumnIndexOrThrow(MeasurementTable.COLUMN_STATUS)),
                    is(equalTo(MeasurementStatus.OPEN.getDatabaseIdentifier())));
            assertThat(
                    result.getShort(
                            result.getColumnIndexOrThrow(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION)),
                    is(equalTo(PERSISTENCE_FILE_FORMAT_VERSION)));
            assertThat(result.getDouble(result.getColumnIndexOrThrow(MeasurementTable.COLUMN_DISTANCE)),
                    is(equalTo(0.0)));

        }

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getIdentifier());

        // Finish the measurement
        capturingBehaviour.updateRecentMeasurement(FINISHED);

        // Load the finished measurement
        try (Cursor finishingResult = mockResolver.query(getMeasurementUri(AUTHORITY), null, BaseColumns._ID + "=?",
                new String[] {identifierString}, null)) {
            if (finishingResult == null) {
                throw new IllegalStateException(
                        "Test failed because it was unable to load data from content provider.");
            }

            assertThat(finishingResult.getCount(), is(equalTo(1)));
            assertThat(finishingResult.moveToFirst(), is(equalTo(true)));

            assertThat(
                    finishingResult.getString(finishingResult.getColumnIndexOrThrow(MeasurementTable.COLUMN_MODALITY)),
                    is(equalTo(UNKNOWN.getDatabaseIdentifier())));
            assertThat(finishingResult.getString(finishingResult.getColumnIndexOrThrow(MeasurementTable.COLUMN_STATUS)),
                    is(equalTo(FINISHED.getDatabaseIdentifier())));
        }
    }

    /**
     * Tests whether data is stored correctly via the <code>PersistenceLayer</code>.
     */
    @Test
    public void testStoreData() throws NoSuchFileException, InvalidProtocolBufferException {
        // Manually trigger data capturing (new measurement with sensor data and a location)
        Measurement measurement = oocut.newMeasurement(UNKNOWN);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback callback = () -> {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        };

        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), callback);

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getIdentifier());

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
        FileAccessLayer fileAccessLayer = new DefaultFileAccess();
        try (Cursor geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null, null, null, null)) {
            // GeoLocations
            Validate.notNull(geoLocationsCursor,
                    "Test failed because it was unable to load data from the content provider.");
            assertThat(geoLocationsCursor.getCount(), is(equalTo(TEST_LOCATION_COUNT)));

            // Point3Ds
            final var accelerationsFile = Point3DFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    ACCELERATION);
            final var rotationsFile = Point3DFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    ROTATION);
            final var directionsFile = Point3DFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    DIRECTION);

            final var accelerations = deserialize(fileAccessLayer, accelerationsFile.getFile(), ACCELERATION);
            final var rotations = deserialize(fileAccessLayer, rotationsFile.getFile(), ROTATION);
            final var directions = deserialize(fileAccessLayer, directionsFile.getFile(), DIRECTION);

            final var accelerationBatch = accelerations.getAccelerationsBinary().getAccelerations(0);
            assertThat(accelerationBatch.getTimestampCount(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(accelerationBatch.getXCount(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(accelerationBatch.getYCount(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(accelerationBatch.getZCount(), is(equalTo(TEST_DATA_COUNT)));
            assertThat(rotations.getRotationsBinary().getRotations(0).getTimestampCount(),
                    is(equalTo(TEST_DATA_COUNT)));
            assertThat(directions.getDirectionsBinary().getDirections(0).getTimestampCount(),
                    is(equalTo(TEST_DATA_COUNT)));
        }
    }

    /**
     * Tests whether cascading deletion of measurements together with all data is working correctly.
     */
    @Test
    @Ignore("Flaky, removedEntries sometimes removes 11 instead of 8 entries")
    public void testCascadingClearMeasurements() {

        // Insert test measurements
        final int testMeasurements = 2;
        oocut.newMeasurement(UNKNOWN);
        Measurement measurement = oocut.newMeasurement(Modality.CAR);

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback finishedCallback = () -> {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        };

        final int testMeasurementsWithPoint3DFiles = 1;
        final int point3DFilesPerMeasurement = 3;
        final int testEvents = 2;
        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeData(testData(), measurement.getIdentifier(), finishedCallback);

        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getIdentifier());

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
        assertThat(removedEntries, is(equalTo(testMeasurementsWithPoint3DFiles * point3DFilesPerMeasurement
                + TEST_LOCATION_COUNT + testMeasurements /* + testIdentifierTableCount */ + testEvents)));

        // make sure nothing is left in the database
        try (Cursor geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null,
                GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.toString(measurement.getIdentifier())}, null);
                Cursor measurementsCursor = mockResolver.query(getMeasurementUri(AUTHORITY), null, null, null, null);
                Cursor identifierCursor = mockResolver.query(getIdentifierUri(AUTHORITY), null, null, null, null)) {
            Validate.notNull(geoLocationsCursor,
                    "Test failed because it was unable to load data from the content provider.");
            Validate.notNull(measurementsCursor,
                    "Test failed because it was unable to load data from the content provider.");
            Validate.notNull(identifierCursor,
                    "Test failed because it was unable to load data from the content provider.");

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
            assertThat(measurementsCursor.getCount(), is(equalTo(0)));
            assertThat(identifierCursor.getCount(), is(equalTo(1))); // because we don't clean it up currently
        }

        // Make sure nothing is left of the Point3DFiles
        final File accelerationsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3DFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3DFile.ROTATIONS_FOLDER_NAME);
        final File directionsFolder = oocut.getFileAccessLayer().getFolderPath(context,
                Point3DFile.DIRECTIONS_FOLDER_NAME);
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
        oocut.newMeasurement(UNKNOWN);
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
        Measurement measurement = oocut.newMeasurement(UNKNOWN);
        final long measurementId = measurement.getIdentifier();

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        WritingDataCompletedCallback callback = () -> {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
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
                Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = oocut.getFileAccessLayer().getFilePath(context, measurementId,
                Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION);
        final File directionFile = oocut.getFileAccessLayer().getFilePath(context, measurementId,
                Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION);
        assertThat(!accelerationFile.exists(), is(true));
        assertThat(!rotationFile.exists(), is(true));
        assertThat(!directionFile.exists(), is(true));

        try (Cursor geoLocationsCursor = mockResolver.query(getGeoLocationsUri(AUTHORITY), null,
                GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null)) {
            Validate.notNull(geoLocationsCursor,
                    "Test failed because it was unable to load data from the content provider.");

            assertThat(geoLocationsCursor.getCount(), is(equalTo(0)));
        }

        try (Cursor eventsCursor = mockResolver.query(getEventUri(AUTHORITY), null,
                EventTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurement.getIdentifier()).toString()}, null)) {
            Validate.notNull(eventsCursor, "Test failed because it was unable to load data from the content provider.");

            assertThat(eventsCursor.getCount(), is(equalTo(0)));
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
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

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
     * <p>
     * This test reproduced STAD-171 where the loadTracks() method did not check the return value of
     * moveToNext() when searching for the next GeoLocation while iterating through the points between pause and resume.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseResumeStop_withGeoLocationAfterStartAndAfterPause_withoutGeoLocationsAfterResume()
            throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

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

        // Stop event and a lightly late 2nd location
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, 7L);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().get(1).getTimestamp(), is(equalTo(2L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link PersistenceLayer} object.
     * <p>
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     * <p>
     * This test reproduced VIC-78 which occurred after refactoring loadTracks() in commit
     * #0673fb3fc81f00438d063114273f17f7ed17298f where we forgot to check the result of moveToNext() in
     * collectNextSubTrack().
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseResumeStop_withGeoLocationAfterStart_withoutGeoLocationsAfterPauseAndAfterResume()
            throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

        // Start event and at least one location between start and pause
        oocut.logEvent(Event.EventType.LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getIdentifier());
        oocut.logEvent(Event.EventType.LIFECYCLE_PAUSE, measurement, 3L);
        oocut.logEvent(Event.EventType.LIFECYCLE_RESUME, measurement, 4L);
        oocut.logEvent(Event.EventType.LIFECYCLE_STOP, measurement, 5L);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getIdentifier());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().get(0).getTimestamp(), is(equalTo(2L)));
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
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

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
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

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
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

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
     * Tests whether loading a cleaned track of {@link ParcelableGeoLocation}s returns the expected filtered locations.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadCleanedTrack() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);
        final long startTime = 1000000000L;
        final ParcelableGeoLocation locationWithJustTooBadAccuracy = new ParcelableGeoLocation(51.1, 13.1,
                startTime + 1, 5.0, 20.0);
        final ParcelableGeoLocation locationWithJustTooLowSpeed = new ParcelableGeoLocation(51.1, 13.1,
                startTime + 2, 1.0, 5);
        final ParcelableGeoLocation locationWithHighEnoughSpeed = new ParcelableGeoLocation(51.1, 13.1,
                startTime + 3, 1.01, 5);
        final ParcelableGeoLocation locationWithGoodEnoughAccuracy = new ParcelableGeoLocation(51.1, 13.1,
                startTime + 10, 5.0, 19.99);
        final ParcelableGeoLocation locationWithJustTooHighSpeed = new ParcelableGeoLocation(51.1, 13.1,
                startTime + 11, 100.0, 5);

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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
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
        });
    }

    /**
     * @param timestamp The timestamp in milliseconds since 1970 to use for the {@link ParcelableGeoLocation}
     * @return An initialized {@code GeoLocation} object with garbage data for testing.
     */
    private ParcelableGeoLocation testLocation(final long timestamp) {
        return new ParcelableGeoLocation(1.0, 1.0, timestamp, 1.0, 1.0f);
    }

    /**
     * @return An initialized {@link CapturedData} object with garbage data for testing.
     */
    private CapturedData testData() {
        List<ParcelablePoint3D> accelerations = new ArrayList<>();
        accelerations.add(new ParcelablePoint3D(1.0f, 1.0f, 1.0f, 1L));
        accelerations.add(new ParcelablePoint3D(2.0f, 2.0f, 2.0f, 2L));
        accelerations.add(new ParcelablePoint3D(3.0f, 3.0f, 3.0f, 3L));
        List<ParcelablePoint3D> directions = new ArrayList<>();
        directions.add(new ParcelablePoint3D(4.0f, 4.0f, 4.0f, 4L));
        directions.add(new ParcelablePoint3D(5.0f, 5.0f, 5.0f, 5L));
        directions.add(new ParcelablePoint3D(6.0f, 6.0f, 6.0f, 6L));
        List<ParcelablePoint3D> rotations = new ArrayList<>();
        rotations.add(new ParcelablePoint3D(7.0f, 7.0f, 7.0f, 7L));
        rotations.add(new ParcelablePoint3D(8.0f, 8.0f, 8.0f, 8L));
        rotations.add(new ParcelablePoint3D(9.0f, 9.0f, 9.0f, 9L));
        List<Pressure> pressures = new ArrayList<>();
        pressures.add(new Pressure(10L, 1013.10f));
        pressures.add(new Pressure(11L, 1013.11f));
        pressures.add(new Pressure(12L, 1013.12f));
        return new CapturedData(accelerations, rotations, directions, pressures);
    }
}
