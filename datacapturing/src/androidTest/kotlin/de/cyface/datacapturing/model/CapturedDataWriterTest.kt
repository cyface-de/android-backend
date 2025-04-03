/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.datacapturing.model

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.protobuf.InvalidProtocolBufferException
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.io.DefaultFileIOHandler
import de.cyface.persistence.io.FileIOHandler
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.ParcelablePoint3D
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.persistence.serialization.NoSuchFileException
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.serialization.Point3DFile.Companion.loadFile
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.serializer.model.Point3DType
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.deserialize
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.core.Is
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests whether captured data is correctly saved to the underlying content provider.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.6.6
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class CapturedDataWriterTest {
    /**
     * The object of the class under test.
     */
    private var oocut: PersistenceLayer<PersistenceBehaviour>? = null

    /**
     * The [Context] required to access the persistence layer.
     */
    private var context: Context? = null

    /**
     * This [PersistenceBehaviour] is used to capture a [de.cyface.persistence.model.Measurement]s with when a
     * [DefaultPersistenceLayer].
     */
    private var capturingBehaviour: CapturingPersistenceBehaviour? = null

    /**
     * Initializes the test case as explained in the
     * [Android documentation](https://developer.android.com/training/testing/integration-testing/content-provider-testing.html#build)
     */
    @Before
    fun setUp(): Unit = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        capturingBehaviour = CapturingPersistenceBehaviour()
        oocut = DefaultPersistenceLayer(context!!, capturingBehaviour!!)
        clearPersistenceLayer(context!!, oocut!!)
        // This is normally called in the <code>DataCapturingService#Constructor</code>
        oocut!!.restoreOrCreateDeviceId()
    }

    /**
     * Deletes all content from the database to make sure it is empty for the next test.
     */
    @After
    fun tearDown(): Unit = runBlocking {
        clearPersistenceLayer(context!!, oocut!!)
    }

    /**
     * Tests whether creating and closing a measurement works as expected.
     *
     * @throws NoSuchMeasurementException When there was no currently captured `Measurement`.
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testCreateNewMeasurement(): Unit = runBlocking {
        // Create a measurement
        val (id) = oocut!!.newMeasurement(Modality.UNKNOWN)
        MatcherAssert.assertThat(id > 0L, Is.`is`(IsEqual.equalTo(true)))

        // Try to load the created measurement and check its properties
        val created = oocut!!.measurementRepository!!.loadById(id)

        MatcherAssert.assertThat(created!!.modality, Is.`is`(IsEqual.equalTo(Modality.UNKNOWN)))
        MatcherAssert.assertThat(
            created.status,
            Is.`is`(IsEqual.equalTo(MeasurementStatus.OPEN))
        )
        MatcherAssert.assertThat(
            created.fileFormatVersion, Is.`is`(
                IsEqual.equalTo(
                    DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION
                )
            )
        )
        MatcherAssert.assertThat(created.distance, Is.`is`(IsEqual.equalTo(0.0)))

        // Store persistenceFileFormatVersion
        oocut!!.storePersistenceFileFormatVersion(
            DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION,
            id
        )

        // Finish the measurement
        capturingBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)

        // Load the finished measurement
        val finished = oocut!!.measurementRepository!!.loadById(id)
        MatcherAssert.assertThat(
            finished!!.modality,
            Is.`is`(IsEqual.equalTo(Modality.UNKNOWN))
        )
        MatcherAssert.assertThat(
            finished.status,
            Is.`is`(IsEqual.equalTo(MeasurementStatus.FINISHED))
        )
    }

    /**
     * Tests whether data is stored correctly via the `PersistenceLayer`.
     */
    @Test
    @Throws(NoSuchFileException::class, InvalidProtocolBufferException::class)
    fun testStoreData(): Unit = runBlocking {
        // Manually trigger data capturing (new measurement with sensor data and a location)
        val (id) = oocut!!.newMeasurement(Modality.UNKNOWN)
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val callback = WritingDataCompletedCallback {
            lock.lock()
            try {
                condition.signal()
            } finally {
                lock.unlock()
            }
        }
        capturingBehaviour!!.storeData(testData(), id, callback)

        // Store persistenceFileFormatVersion
        oocut!!.storePersistenceFileFormatVersion(
            DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION,
            id
        )
        lock.lock()
        try {
            @Suppress("BlockingMethodInNonBlockingContext")
            condition.await(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock.unlock()
        }
        capturingBehaviour!!.storeLocation(testLocation(1L), id)

        // Check if the captured data was persisted
        val fileIOHandler: FileIOHandler = DefaultFileIOHandler()
        val locations = oocut!!.locationDao!!.getAll()
        MatcherAssert.assertThat(locations.size, Is.`is`(IsEqual.equalTo(TEST_LOCATION_COUNT)))

        // Point3Ds
        val accelerationsFile = loadFile(context!!, fileIOHandler, id, Point3DType.ACCELERATION)
        val rotationsFile = loadFile(context!!, fileIOHandler, id, Point3DType.ROTATION)
        val directionsFile = loadFile(context!!, fileIOHandler, id, Point3DType.DIRECTION)
        val accelerations = deserialize(fileIOHandler, accelerationsFile.file, Point3DType.ACCELERATION)
        val rotations = deserialize(fileIOHandler, rotationsFile.file, Point3DType.ROTATION)
        val directions = deserialize(fileIOHandler, directionsFile.file, Point3DType.DIRECTION)
        val accelerationBatch = accelerations.accelerationsBinary.getAccelerations(0)
        MatcherAssert.assertThat(
            accelerationBatch.timestampCount, Is.`is`(
                IsEqual.equalTo(
                    TEST_DATA_COUNT
                )
            )
        )
        MatcherAssert.assertThat(
            accelerationBatch.xCount,
            Is.`is`(IsEqual.equalTo(TEST_DATA_COUNT))
        )
        MatcherAssert.assertThat(
            accelerationBatch.yCount,
            Is.`is`(IsEqual.equalTo(TEST_DATA_COUNT))
        )
        MatcherAssert.assertThat(
            accelerationBatch.zCount,
            Is.`is`(IsEqual.equalTo(TEST_DATA_COUNT))
        )
        MatcherAssert.assertThat(
            rotations.rotationsBinary.getRotations(0).timestampCount,
            Is.`is`(IsEqual.equalTo(TEST_DATA_COUNT))
        )
        MatcherAssert.assertThat(
            directions.directionsBinary.getDirections(0).timestampCount,
            Is.`is`(IsEqual.equalTo(TEST_DATA_COUNT))
        )
    }

    /**
     * Tests whether cascading deletion of measurements together with all data is working correctly.
     */
    @Test
    @Ignore("Flaky, removedEntries sometimes removes 11 instead of 8 entries")
    fun testCascadingClearMeasurements(): Unit = runBlocking {
        // Insert test measurements
        val testMeasurements = 2
        oocut!!.newMeasurement(Modality.UNKNOWN)
        val measurement = oocut!!.newMeasurement(Modality.CAR)
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val finishedCallback = WritingDataCompletedCallback {
            lock.lock()
            try {
                condition.signal()
            } finally {
                lock.unlock()
            }
        }
        val testMeasurementsWithPoint3DFiles = 1
        val point3DFilesPerMeasurement = 3
        val testEvents = 2
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, System.currentTimeMillis())
        capturingBehaviour!!.storeData(testData(), measurement.id, finishedCallback)
        oocut!!.storePersistenceFileFormatVersion(
            DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION,
            measurement.id
        )
        capturingBehaviour!!.storeLocation(testLocation(1L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis())
        lock.lock()
        try {
            condition.await(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock.unlock()
        }

        // clear the test data
        val removedEntries = clearPersistenceLayer(context!!, oocut!!)
        // final int testIdentifierTableCount = 1; - currently not deleted at the end of tests because this breaks
        // the life-cycle DataCapturingServiceTests
        MatcherAssert.assertThat(
            removedEntries,
            Is.`is`(
                IsEqual.equalTo(
                    testMeasurementsWithPoint3DFiles * point3DFilesPerMeasurement +
                            TEST_LOCATION_COUNT + testMeasurements /* + testIdentifierTableCount */ +
                            testEvents
                )
            )
        )

        // make sure nothing is left in the database
        val locations = oocut!!.locationDao!!.loadAllByMeasurementId(measurement.id)
        val measurements = oocut!!.measurementRepository!!.getAll()
        val identifiers = oocut!!.identifierDao!!.getAll()
        MatcherAssert.assertThat(locations.size, Is.`is`(IsEqual.equalTo(0)))
        MatcherAssert.assertThat(measurements.size, Is.`is`(IsEqual.equalTo(0)))
        MatcherAssert.assertThat(
            identifiers.size,
            Is.`is`(IsEqual.equalTo(1))
        ) // because we don't clean it up currently

        // Make sure nothing is left of the Point3DFiles
        val accelerationsFolder = oocut!!.fileIOHandler.getFolderPath(
            context!!,
            Point3DFile.ACCELERATIONS_FOLDER_NAME
        )
        val rotationsFolder = oocut!!.fileIOHandler.getFolderPath(
            context!!,
            Point3DFile.ROTATIONS_FOLDER_NAME
        )
        val directionsFolder = oocut!!.fileIOHandler.getFolderPath(
            context!!,
            Point3DFile.DIRECTIONS_FOLDER_NAME
        )
        MatcherAssert.assertThat(accelerationsFolder.exists(), Is.`is`(IsEqual.equalTo(false)))
        MatcherAssert.assertThat(rotationsFolder.exists(), Is.`is`(IsEqual.equalTo(false)))
        MatcherAssert.assertThat(directionsFolder.exists(), Is.`is`(IsEqual.equalTo(false)))
    }

    /**
     * Tests whether loading [de.cyface.persistence.model.Measurement]s from the data storage via `PersistenceLayer` is
     * working as expected.
     */
    @Test
    fun testLoadMeasurements(): Unit = runBlocking {
        oocut!!.newMeasurement(Modality.UNKNOWN)
        oocut!!.newMeasurement(Modality.CAR)
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(2)))
        for (measurement in loadedMeasurements) {
            oocut!!.delete(measurement.id)
        }
    }

    /**
     * Tests whether deleting a measurement actually remove that measurement together with all corresponding data.
     */
    @Test
    fun testDeleteMeasurement(): Unit = runBlocking {

        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)
        val measurementId = measurement.id
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()
        val callback = WritingDataCompletedCallback {
            lock.lock()
            try {
                condition.signal()
            } finally {
                lock.unlock()
            }
        }
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, System.currentTimeMillis())
        capturingBehaviour!!.storeData(testData(), measurementId, callback)
        lock.lock()
        try {
            @Suppress("BlockingMethodInNonBlockingContext")
            condition.await(2, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock.unlock()
        }
        capturingBehaviour!!.storeLocation(testLocation(1L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis())

        // Act
        oocut!!.delete(measurement.id)

        // Assert
        val accelerationFile = oocut!!.fileIOHandler.getFilePath(
            context!!, measurementId,
            Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION
        )
        val rotationFile = oocut!!.fileIOHandler.getFilePath(
            context!!, measurementId,
            Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION
        )
        val directionFile = oocut!!.fileIOHandler.getFilePath(
            context!!, measurementId,
            Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION
        )
        MatcherAssert.assertThat(!accelerationFile.exists(), Is.`is`(true))
        MatcherAssert.assertThat(!rotationFile.exists(), Is.`is`(true))
        MatcherAssert.assertThat(!directionFile.exists(), Is.`is`(true))
        val locations = oocut!!.locationDao!!.loadAllByMeasurementId(measurementId)
        MatcherAssert.assertThat(locations.size, Is.`is`(IsEqual.equalTo(0)))
        val events = oocut!!.eventRepository!!.loadAllByMeasurementId(measurementId)
        MatcherAssert.assertThat(events!!.size, Is.`is`(IsEqual.equalTo(0)))
        MatcherAssert.assertThat(oocut!!.loadMeasurements().size, Is.`is`(IsEqual.equalTo(0)))
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     *
     *
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     */
    @Test
    fun testLoadTrack_startPauseResumeStop(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)

        // Start event and 2 locations
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, 1L)
        capturingBehaviour!!.storeLocation(testLocation(1L), measurement.id)
        capturingBehaviour!!.storeLocation(testLocation(2L), measurement.id)

        // Pause event and a slightly late 3rd location
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, 3L)
        capturingBehaviour!!.storeLocation(testLocation(4L), measurement.id)

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour!!.storeLocation(testLocation(5L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_RESUME, measurement, 6L)
        capturingBehaviour!!.storeLocation(testLocation(6L), measurement.id)

        // Stop event and a lightly late 2nd location
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, 7L)
        capturingBehaviour!!.storeLocation(testLocation(8L), measurement.id)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(tracks[1].geoLocations.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(
            tracks[0].geoLocations[1].timestamp,
            Is.`is`(IsEqual.equalTo(2L))
        )
        MatcherAssert.assertThat(
            tracks[1].geoLocations[0].timestamp,
            Is.`is`(IsEqual.equalTo(6L))
        )
        MatcherAssert.assertThat(
            tracks[1].geoLocations[1].timestamp,
            Is.`is`(IsEqual.equalTo(8L))
        )
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     *
     *
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     *
     *
     * This test reproduced STAD-171 where the loadTracks() method did not check the return value of
     * moveToNext() when searching for the next GeoLocation while iterating through the points between pause and resume.
     */
    @Test
    fun testLoadTrack_startPauseResumeStop_withGeoLocationAfterStartAndAfterPause_withoutGeoLocationsAfterResume(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)

        // Start event and 2 locations
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, 1L)
        capturingBehaviour!!.storeLocation(testLocation(1L), measurement.id)
        capturingBehaviour!!.storeLocation(testLocation(2L), measurement.id)

        // Pause event and a slightly late 3rd location
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, 3L)
        capturingBehaviour!!.storeLocation(testLocation(4L), measurement.id)

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour!!.storeLocation(testLocation(5L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_RESUME, measurement, 6L)

        // Stop event and a lightly late 2nd location
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, 7L)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(1)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(
            tracks[0].geoLocations[1].timestamp,
            Is.`is`(IsEqual.equalTo(2L))
        )
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     *
     *
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     *
     *
     * This test reproduced VIC-78 which occurred after refactoring loadTracks() in commit
     * #0673fb3fc81f00438d063114273f17f7ed17298f where we forgot to check the result of moveToNext() in
     * collectNextSubTrack().
     */
    @Test
    fun testLoadTrack_startPauseResumeStop_withGeoLocationAfterStart_withoutGeoLocationsAfterPauseAndAfterResume(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)

        // Start event and at least one location between start and pause
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, 1L)
        capturingBehaviour!!.storeLocation(testLocation(2L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, 3L)
        oocut!!.logEvent(EventType.LIFECYCLE_RESUME, measurement, 4L)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, 5L)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(1)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(1)))
        MatcherAssert.assertThat(
            tracks[0].geoLocations[0].timestamp,
            Is.`is`(IsEqual.equalTo(2L))
        )
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     *
     *
     * This test uses predefined timestamps or else it will be flaky, e.g. when storing a location is faster than
     * storing an event when the test assumes otherwise.
     */
    @Test
    fun testLoadTrack_startPauseResumePauseStop(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)

        // Start event and 2 locations
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, 1L)
        capturingBehaviour!!.storeLocation(testLocation(1L), measurement.id)
        capturingBehaviour!!.storeLocation(testLocation(2L), measurement.id)

        // Pause event and a slightly late 3rd location
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, 3L)
        capturingBehaviour!!.storeLocation(testLocation(4L), measurement.id)

        // Resume event with a cached, older location (STAD-140) and a location with the same timestamp
        capturingBehaviour!!.storeLocation(testLocation(5L), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_RESUME, measurement, 6L)
        // The first location may be capturing at the same millisecond (tried to reproduce MOV-676)
        capturingBehaviour!!.storeLocation(testLocation(6L), measurement.id)

        // Pause event and a slightly late 2nd location
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, 7L)
        capturingBehaviour!!.storeLocation(testLocation(8L), measurement.id)

        // Stop event and a lightly late location
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, 9L)
        capturingBehaviour!!.storeLocation(testLocation(10L), measurement.id)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(tracks[1].geoLocations.size, Is.`is`(IsEqual.equalTo(3)))
        MatcherAssert.assertThat(
            tracks[1].geoLocations[0].timestamp,
            Is.`is`(IsEqual.equalTo(6L))
        )
        MatcherAssert.assertThat(
            tracks[1].geoLocations[1].timestamp,
            Is.`is`(IsEqual.equalTo(8L))
        )
        MatcherAssert.assertThat(
            tracks[1].geoLocations[2].timestamp,
            Is.`is`(IsEqual.equalTo(10L))
        )
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     */
    @Test
    fun testLoadTrack_startPauseStop(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, System.currentTimeMillis())
        capturingBehaviour!!.storeLocation(testLocation(System.currentTimeMillis()), measurement.id)
        capturingBehaviour!!.storeLocation(testLocation(System.currentTimeMillis()), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, System.currentTimeMillis())
        // It's possible that GeoLocations arrive just after capturing was paused
        val timestamp = System.currentTimeMillis()
        capturingBehaviour!!.storeLocation(testLocation(timestamp), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis())

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(1)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(3)))
        MatcherAssert.assertThat(
            tracks[0].geoLocations[2].timestamp,
            Is.`is`(IsEqual.equalTo(timestamp))
        )
    }

    /**
     * Tests whether loading a track of geo locations is possible via the [DefaultPersistenceLayer] object.
     */
    @Test
    fun testLoadTrack_startStop(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, System.currentTimeMillis())
        capturingBehaviour!!.storeLocation(testLocation(System.currentTimeMillis()), measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, System.currentTimeMillis())
        // It's possible that GeoLocations arrive just after stop method was triggered
        val timestamp = System.currentTimeMillis()
        capturingBehaviour!!.storeLocation(testLocation(timestamp), measurement.id)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val tracks = oocut!!.loadTracks(
            loadedMeasurements[0].id
        )

        // Assert
        MatcherAssert.assertThat(tracks.size, Is.`is`(IsEqual.equalTo(1)))
        MatcherAssert.assertThat(tracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(
            tracks[0].geoLocations[1].timestamp,
            Is.`is`(IsEqual.equalTo(timestamp))
        )
    }

    /**
     * Tests whether loading a cleaned track of [ParcelableGeoLocation]s returns the expected filtered locations.
     */
    @Test
    fun testLoadCleanedTrack(): Unit = runBlocking {
        // Arrange
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)
        val startTime = 1000000000L
        val locationWithJustTooBadAccuracy = ParcelableGeoLocation(
            startTime + 1, 51.1,
            13.1, 400.0,
            5.0, 20.0, 20.0
        )
        val locationWithJustTooLowSpeed = ParcelableGeoLocation(
            startTime + 2, 51.1, 13.1,
            400.0,
            1.0, 5.0, 20.0
        )
        val locationWithHighEnoughSpeed = ParcelableGeoLocation(
            startTime + 3, 51.1, 13.1,
            400.0,
            1.01, 5.0, 20.0
        )
        val locationWithGoodEnoughAccuracy = ParcelableGeoLocation(
            startTime + 10, 51.1,
            13.1, 400.0,
            5.0, 19.99, 20.0
        )
        val locationWithJustTooHighSpeed = ParcelableGeoLocation(
            startTime + 11, 51.1, 13.1,
            400.0,
            100.0, 5.0, 20.0
        )
        oocut!!.logEvent(EventType.LIFECYCLE_START, measurement, startTime)
        capturingBehaviour!!.storeLocation(locationWithJustTooBadAccuracy, measurement.id)
        capturingBehaviour!!.storeLocation(locationWithJustTooLowSpeed, measurement.id)
        capturingBehaviour!!.storeLocation(locationWithHighEnoughSpeed, measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_PAUSE, measurement, startTime + 4)
        oocut!!.logEvent(EventType.LIFECYCLE_RESUME, measurement, startTime + 9)
        capturingBehaviour!!.storeLocation(locationWithGoodEnoughAccuracy, measurement.id)
        capturingBehaviour!!.storeLocation(locationWithJustTooHighSpeed, measurement.id)
        oocut!!.logEvent(EventType.LIFECYCLE_STOP, measurement, startTime + 12)

        // Act
        val loadedMeasurements = oocut!!.loadMeasurements()
        MatcherAssert.assertThat(loadedMeasurements.size, Is.`is`(IsEqual.equalTo(1)))
        val cleanedTracks = oocut!!.loadTracks(
            loadedMeasurements[0].id,
            DefaultLocationCleaning()
        )

        // Assert
        MatcherAssert.assertThat(cleanedTracks.size, Is.`is`(IsEqual.equalTo(2)))
        MatcherAssert.assertThat(cleanedTracks[0].geoLocations.size, Is.`is`(IsEqual.equalTo(1)))
        val location1 = cleanedTracks[0].geoLocations[0]
        location1.id = 0 // We don't care about the database id being different after loading
        MatcherAssert.assertThat(
            location1,
            Is.`is`(IsEqual.equalTo(GeoLocation(locationWithHighEnoughSpeed, measurement.id)))
        )
        MatcherAssert.assertThat(cleanedTracks[1].geoLocations.size, Is.`is`(IsEqual.equalTo(1)))
        val location2 = cleanedTracks[1].geoLocations[0]
        location2.id = 0 // We don't care about the database id being different after loading
        MatcherAssert.assertThat(
            location2,
            Is.`is`(IsEqual.equalTo(GeoLocation(locationWithGoodEnoughAccuracy, measurement.id)))
        )
    }

    @Test
    fun testProvokeAnr(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                runBlocking {
                    if (!oocut!!.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut!!.newMeasurement(Modality.BICYCLE)
                    }
                    if (!oocut!!.hasMeasurement(MeasurementStatus.OPEN)) {
                        oocut!!.newMeasurement(Modality.BICYCLE)
                    }
                    if (oocut!!.hasMeasurement(MeasurementStatus.OPEN)) {
                        capturingBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
                    }
                }
            } catch (e: NoSuchMeasurementException) {
                throw IllegalStateException(e)
            }
        }
    }

    /**
     * @param timestamp The timestamp in milliseconds since 1970 to use for the [ParcelableGeoLocation]
     * @return An initialized `GeoLocation` object with garbage data for testing.
     */
    private fun testLocation(timestamp: Long): ParcelableGeoLocation {
        return ParcelableGeoLocation(timestamp, 1.0, 1.0, 1.0, 1.0, 5.0, 13.0)
    }

    /**
     * @return An initialized [CapturedData] object with garbage data for testing.
     */
    private fun testData(): CapturedData {
        val accelerations = mutableListOf<ParcelablePoint3D>()
        accelerations.add(ParcelablePoint3D(1L, 1.0f, 1.0f, 1.0f))
        accelerations.add(ParcelablePoint3D(2L, 2.0f, 2.0f, 2.0f))
        accelerations.add(ParcelablePoint3D(3L, 3.0f, 3.0f, 3.0f))
        val directions = mutableListOf<ParcelablePoint3D>()
        directions.add(ParcelablePoint3D(4L, 4.0f, 4.0f, 4.0f))
        directions.add(ParcelablePoint3D(5L, 5.0f, 5.0f, 5.0f))
        directions.add(ParcelablePoint3D(6L, 6.0f, 6.0f, 6.0f))
        val rotations = mutableListOf<ParcelablePoint3D>()
        rotations.add(ParcelablePoint3D(7L, 7.0f, 7.0f, 7.0f))
        rotations.add(ParcelablePoint3D(8L, 8.0f, 8.0f, 8.0f))
        rotations.add(ParcelablePoint3D(9L, 9.0f, 9.0f, 9.0f))
        val pressures = mutableListOf<ParcelablePressure>()
        pressures.add(ParcelablePressure(10L, 1013.10))
        pressures.add(ParcelablePressure(11L, 1013.11))
        pressures.add(ParcelablePressure(12L, 1013.12))
        return CapturedData(accelerations, rotations, directions, pressures)
    }

    companion object {
        private const val TEST_LOCATION_COUNT = 1
        private const val TEST_DATA_COUNT = 3
    }
}