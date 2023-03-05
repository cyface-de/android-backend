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
package de.cyface.datacapturing.model;

import static de.cyface.datacapturing.TestUtils.AUTHORITY;
import static de.cyface.persistence.DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.model.EventType.LIFECYCLE_PAUSE;
import static de.cyface.persistence.model.EventType.LIFECYCLE_RESUME;
import static de.cyface.persistence.model.EventType.LIFECYCLE_START;
import static de.cyface.persistence.model.EventType.LIFECYCLE_STOP;
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
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.protobuf.InvalidProtocolBufferException;

import android.content.ContentProvider;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.DefaultPersistenceLayer;
import de.cyface.persistence.dao.DefaultFileDao;
import de.cyface.persistence.dao.FileDao;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.model.ParcelablePoint3D;
import de.cyface.persistence.model.ParcelablePressure;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.NoSuchFileException;
import de.cyface.persistence.serialization.Point3DFile;
import de.cyface.persistence.strategy.DefaultLocationCleaning;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.CursorIsNullException;

/**
 * Tests whether captured data is correctly saved to the underlying content provider.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.6.2
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CapturedDataWriterTest {

    /**
     * The object of the class under test.
     */
    private DefaultPersistenceLayer<CapturingPersistenceBehaviour> oocut;
    /**
     * The {@link Context} required to access the persistence layer.
     */
    private Context context;
    /**
     * This {@link PersistenceBehaviour} is used to capture a {@link Measurement}s with when a {@link DefaultPersistenceLayer}.
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
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        this.capturingBehaviour = new CapturingPersistenceBehaviour();
        oocut = new DefaultPersistenceLayer<>(context, AUTHORITY, capturingBehaviour);
        SharedTestUtils.clearPersistenceLayer(context, oocut.getDatabase());
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        oocut.restoreOrCreateDeviceId();
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    public void tearDown() {
        clearPersistenceLayer(context, oocut.getDatabase());
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
        assertThat(measurement.getId() > 0L, is(equalTo(true)));

        // Try to load the created measurement and check its properties
        final var measurementDao = oocut.getDatabase().measurementDao();
        final var created = measurementDao.loadById(measurement.getId());
        assertThat(created.getModality(), is(equalTo(UNKNOWN)));
        assertThat(created.getStatus(), is(equalTo(MeasurementStatus.OPEN)));
        assertThat(created.getFileFormatVersion(), is(equalTo(PERSISTENCE_FILE_FORMAT_VERSION)));
        assertThat(created.getDistance(), is(equalTo(0.0)));

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getId());

        // Finish the measurement
        capturingBehaviour.updateRecentMeasurement(FINISHED);

        // Load the finished measurement
        final var finished = measurementDao.loadById(measurement.getId());
        assertThat(finished.getModality(), is(equalTo(UNKNOWN)));
        assertThat(finished.getStatus(), is(equalTo(FINISHED)));
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

        capturingBehaviour.storeData(testData(), measurement.getId(), callback);

        // Store persistenceFileFormatVersion
        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getId());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());

        // Check if the captured data was persisted
        FileDao fileDao = new DefaultFileDao();
        final var locationDao = oocut.getDatabase().geoLocationDao();
        final var locations = locationDao.getAll();
        assertThat(locations.size(), is(equalTo(TEST_LOCATION_COUNT)));

        // Point3Ds
        final var accelerationsFile = Point3DFile.loadFile(context, fileDao, measurement.getId(), ACCELERATION);
        final var rotationsFile = Point3DFile.loadFile(context, fileDao, measurement.getId(), ROTATION);
        final var directionsFile = Point3DFile.loadFile(context, fileDao, measurement.getId(), DIRECTION);

        final var accelerations = deserialize(fileDao, accelerationsFile.getFile(), ACCELERATION);
        final var rotations = deserialize(fileDao, rotationsFile.getFile(), ROTATION);
        final var directions = deserialize(fileDao, directionsFile.getFile(), DIRECTION);

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
        oocut.logEvent(LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeData(testData(), measurement.getId(), finishedCallback);

        oocut.storePersistenceFileFormatVersion(PERSISTENCE_FILE_FORMAT_VERSION, measurement.getId());

        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());
        oocut.logEvent(LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }

        // clear the test data
        int removedEntries = clearPersistenceLayer(context, oocut.getDatabase());
        // final int testIdentifierTableCount = 1; - currently not deleted at the end of tests because this breaks
        // the life-cycle DataCapturingServiceTests
        assertThat(removedEntries, is(equalTo(testMeasurementsWithPoint3DFiles * point3DFilesPerMeasurement
                + TEST_LOCATION_COUNT + testMeasurements /* + testIdentifierTableCount */ + testEvents)));

        // make sure nothing is left in the database
        final var locationsDao = oocut.getDatabase().geoLocationDao();
        final var measurementsDao = oocut.getDatabase().measurementDao();
        final var identifierDao = oocut.getDatabase().identifierDao();
        final var locations = locationsDao.loadAllByMeasurementId(measurement.getId());
        final var measurements = measurementsDao.getAll();
        final var identifiers = identifierDao.getAll();
        assertThat(locations.size(), is(equalTo(0)));
        assertThat(measurements.size(), is(equalTo(0)));
        assertThat(identifiers.size(), is(equalTo(1))); // because we don't clean it up currently

        // Make sure nothing is left of the Point3DFiles
        final File accelerationsFolder = oocut.getFileDao().getFolderPath(context,
                Point3DFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationsFolder = oocut.getFileDao().getFolderPath(context,
                Point3DFile.ROTATIONS_FOLDER_NAME);
        final File directionsFolder = oocut.getFileDao().getFolderPath(context,
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
            oocut.delete(measurement.getId());
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     */
    @Test
    public void testDeleteMeasurement() throws CursorIsNullException {

        // Arrange
        Measurement measurement = oocut.newMeasurement(UNKNOWN);
        final long measurementId = measurement.getId();

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

        oocut.logEvent(LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeData(testData(), measurementId, callback);
        lock.lock();
        try {
            condition.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());
        oocut.logEvent(LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        // Act
        oocut.delete(measurement.getId());

        // Assert
        final File accelerationFile = oocut.getFileDao().getFilePath(context, measurementId,
                Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION);
        final File rotationFile = oocut.getFileDao().getFilePath(context, measurementId,
                Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION);
        final File directionFile = oocut.getFileDao().getFilePath(context, measurementId,
                Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION);
        assertThat(!accelerationFile.exists(), is(true));
        assertThat(!rotationFile.exists(), is(true));
        assertThat(!directionFile.exists(), is(true));

        final var locationsDao = oocut.getDatabase().geoLocationDao();
        final var locations = locationsDao.loadAllByMeasurementId(measurementId);
        assertThat(locations.size(), is(equalTo(0)));

        final var eventsDao = oocut.getDatabase().eventDao();
        final var events = eventsDao.loadAllByMeasurementId(measurementId);
        assertThat(events.size(), is(equalTo(0)));

        assertThat(oocut.loadMeasurements().size(), is(equalTo(0)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
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
        oocut.logEvent(LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getId());

        // Pause event and a slightly late 3rd location
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, 3L);
        capturingBehaviour.storeLocation(testLocation(4L), measurement.getId());

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour.storeLocation(testLocation(5L), measurement.getId());
        oocut.logEvent(LIFECYCLE_RESUME, measurement, 6L);
        capturingBehaviour.storeLocation(testLocation(6L), measurement.getId());

        // Stop event and a lightly late 2nd location
        oocut.logEvent(LIFECYCLE_STOP, measurement, 7L);
        capturingBehaviour.storeLocation(testLocation(8L), measurement.getId());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

        // Assert
        assertThat(tracks.size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(1).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().get(1).getTimestamp(), is(equalTo(2L)));
        assertThat(tracks.get(1).getGeoLocations().get(0).getTimestamp(), is(equalTo(6L)));
        assertThat(tracks.get(1).getGeoLocations().get(1).getTimestamp(), is(equalTo(8L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
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
        oocut.logEvent(LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getId());

        // Pause event and a slightly late 3rd location
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, 3L);
        capturingBehaviour.storeLocation(testLocation(4L), measurement.getId());

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour.storeLocation(testLocation(5L), measurement.getId());
        oocut.logEvent(LIFECYCLE_RESUME, measurement, 6L);

        // Stop event and a lightly late 2nd location
        oocut.logEvent(LIFECYCLE_STOP, measurement, 7L);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().get(1).getTimestamp(), is(equalTo(2L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
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
        oocut.logEvent(LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getId());
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, 3L);
        oocut.logEvent(LIFECYCLE_RESUME, measurement, 4L);
        oocut.logEvent(LIFECYCLE_STOP, measurement, 5L);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().get(0).getTimestamp(), is(equalTo(2L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
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
        oocut.logEvent(LIFECYCLE_START, measurement, 1L);
        capturingBehaviour.storeLocation(testLocation(1L), measurement.getId());
        capturingBehaviour.storeLocation(testLocation(2L), measurement.getId());

        // Pause event and a slightly late 3rd location
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, 3L);
        capturingBehaviour.storeLocation(testLocation(4L), measurement.getId());

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour.storeLocation(testLocation(5L), measurement.getId());
        oocut.logEvent(LIFECYCLE_RESUME, measurement, 6L);
        // The first location may be capturing at the same millisecond (tried to reproduce MOV-676)
        capturingBehaviour.storeLocation(testLocation(6L), measurement.getId());

        // Pause event and a slightly late 2nd location
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, 7L);
        capturingBehaviour.storeLocation(testLocation(8L), measurement.getId());

        // Stop event and a lightly late location
        oocut.logEvent(LIFECYCLE_STOP, measurement, 9L);
        capturingBehaviour.storeLocation(testLocation(10L), measurement.getId());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

        // Assert
        assertThat(tracks.size(), is(equalTo(2)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(2)));
        assertThat(tracks.get(1).getGeoLocations().size(), is(equalTo(3)));
        assertThat(tracks.get(1).getGeoLocations().get(0).getTimestamp(), is(equalTo(6L)));
        assertThat(tracks.get(1).getGeoLocations().get(1).getTimestamp(), is(equalTo(8L)));
        assertThat(tracks.get(1).getGeoLocations().get(2).getTimestamp(), is(equalTo(10L)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startPauseStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

        oocut.logEvent(LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getId());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getId());
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, System.currentTimeMillis());
        // It's possible that GeoLocations arrive just after capturing was paused
        final long timestamp = System.currentTimeMillis();
        capturingBehaviour.storeLocation(testLocation(timestamp), measurement.getId());

        oocut.logEvent(LIFECYCLE_STOP, measurement, System.currentTimeMillis());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

        // Assert
        assertThat(tracks.size(), is(equalTo(1)));
        assertThat(tracks.get(0).getGeoLocations().size(), is(equalTo(3)));
        assertThat(tracks.get(0).getGeoLocations().get(2).getTimestamp(), is(equalTo(timestamp)));
    }

    /**
     * Tests whether loading a track of geo locations is possible via the {@link DefaultPersistenceLayer} object.
     *
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Test
    public void testLoadTrack_startStop() throws CursorIsNullException {

        // Arrange
        final Measurement measurement = oocut.newMeasurement(UNKNOWN);

        oocut.logEvent(LIFECYCLE_START, measurement, System.currentTimeMillis());
        capturingBehaviour.storeLocation(testLocation(System.currentTimeMillis()), measurement.getId());
        oocut.logEvent(LIFECYCLE_STOP, measurement, System.currentTimeMillis());
        // It's possible that GeoLocations arrive just after stop method was triggered
        final long timestamp = System.currentTimeMillis();
        capturingBehaviour.storeLocation(testLocation(timestamp), measurement.getId());

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> tracks = oocut.loadTracks(loadedMeasurements.get(0).getId());

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
        final ParcelableGeoLocation locationWithJustTooBadAccuracy = new ParcelableGeoLocation(startTime + 1, 51.1,
                13.1, 400.,
                5.0, 20.0, 20.);
        final ParcelableGeoLocation locationWithJustTooLowSpeed = new ParcelableGeoLocation(startTime + 2, 51.1, 13.1,
                400.,
                1.0, 5., 20.);
        final ParcelableGeoLocation locationWithHighEnoughSpeed = new ParcelableGeoLocation(startTime + 3, 51.1, 13.1,
                400.,
                1.01, 5., 20.);
        final ParcelableGeoLocation locationWithGoodEnoughAccuracy = new ParcelableGeoLocation(startTime + 10, 51.1,
                13.1, 400.,
                5.0, 19.99, 20.);
        final ParcelableGeoLocation locationWithJustTooHighSpeed = new ParcelableGeoLocation(startTime + 11, 51.1, 13.1,
                400.,
                100.0, 5., 20.);

        oocut.logEvent(LIFECYCLE_START, measurement, startTime);
        capturingBehaviour.storeLocation(locationWithJustTooBadAccuracy, measurement.getId());
        capturingBehaviour.storeLocation(locationWithJustTooLowSpeed, measurement.getId());
        capturingBehaviour.storeLocation(locationWithHighEnoughSpeed, measurement.getId());
        oocut.logEvent(LIFECYCLE_PAUSE, measurement, startTime + 4);
        oocut.logEvent(LIFECYCLE_RESUME, measurement, startTime + 9);
        capturingBehaviour.storeLocation(locationWithGoodEnoughAccuracy, measurement.getId());
        capturingBehaviour.storeLocation(locationWithJustTooHighSpeed, measurement.getId());
        oocut.logEvent(LIFECYCLE_STOP, measurement, startTime + 12);

        // Act
        final List<Measurement> loadedMeasurements = oocut.loadMeasurements();
        assertThat(loadedMeasurements.size(), is(equalTo(1)));
        List<Track> cleanedTracks = oocut.loadTracks(loadedMeasurements.get(0).getId(),
                new DefaultLocationCleaning());

        // Assert
        assertThat(cleanedTracks.size(), is(equalTo(2)));
        assertThat(cleanedTracks.get(0).getGeoLocations().size(), is(equalTo(1)));
        assertThat(cleanedTracks.get(0).getGeoLocations().get(0), is(equalTo(new GeoLocation(locationWithHighEnoughSpeed, measurement.getId()))));
        assertThat(cleanedTracks.get(1).getGeoLocations().size(), is(equalTo(1)));
        assertThat(cleanedTracks.get(1).getGeoLocations().get(0), is(equalTo(new GeoLocation(locationWithGoodEnoughAccuracy, measurement.getId()))));
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
        return new ParcelableGeoLocation(timestamp, 1.0, 1.0, 1.0, 1.0, 5.0, 13.0);
    }

    /**
     * @return An initialized {@link CapturedData} object with garbage data for testing.
     */
    private CapturedData testData() {
        var accelerations = new ArrayList<ParcelablePoint3D>();
        accelerations.add(new ParcelablePoint3D(1L, 1.0f, 1.0f, 1.0f));
        accelerations.add(new ParcelablePoint3D(2L, 2.0f, 2.0f, 2.0f));
        accelerations.add(new ParcelablePoint3D(3L, 3.0f, 3.0f, 3.0f));
        var directions = new ArrayList<ParcelablePoint3D>();
        directions.add(new ParcelablePoint3D(4L, 4.0f, 4.0f, 4.0f));
        directions.add(new ParcelablePoint3D(5L, 5.0f, 5.0f, 5.0f));
        directions.add(new ParcelablePoint3D(6L, 6.0f, 6.0f, 6.0f));
        var rotations = new ArrayList<ParcelablePoint3D>();
        rotations.add(new ParcelablePoint3D(7L, 7.0f, 7.0f, 7.0f));
        rotations.add(new ParcelablePoint3D(8L, 8.0f, 8.0f, 8.0f));
        rotations.add(new ParcelablePoint3D(9L, 9.0f, 9.0f, 9.0f));
        var pressures = new ArrayList<ParcelablePressure>();
        pressures.add(new ParcelablePressure(10L, 1013.10f));
        pressures.add(new ParcelablePressure(11L, 1013.11f));
        pressures.add(new ParcelablePressure(12L, 1013.12f));
        return new CapturedData(accelerations, rotations, directions, pressures);
    }
}
