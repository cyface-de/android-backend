/*
 * Copyright 2018-2025 Cyface GmbH
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
package de.cyface.datacapturing.persistence

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.testutils.SharedTestUtils.clearPersistenceLayer
import de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the correct workings of the `PersistenceLayer` class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.5.9
 * @since 2.0.3
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class PersistenceLayerTest {
    /**
     * An object of the class under test. It is setup prior to each test execution.
     */
    private var oocut: DefaultPersistenceLayer<CapturingPersistenceBehaviour?>? = null

    /**
     * [Context] used to access the persistence layer
     */
    private var context: Context? = null

    /**
     * This `PersistenceBehaviour` is used to capture a `Measurement`s with when a
     * [DefaultPersistenceLayer].
     */
    private var capturingBehaviour: CapturingPersistenceBehaviour? = null

    /**
     * Initializes the `oocut` with the Android persistence stack.
     */
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        capturingBehaviour = CapturingPersistenceBehaviour()
        oocut = DefaultPersistenceLayer(context!!, capturingBehaviour)
    }

    /**
     * Deletes all content from the content provider, to leave the next test with a clean test environment.
     */
    @After
    fun tearDown() {
        runBlocking { clearPersistenceLayer(context!!, oocut!!) }
        oocut!!.shutdown()
    }

    /**
     * Inserts two measurements into the database; one finished and one still running and checks, that the
     * `loadFinishedMeasurements` method returns a list of size 1.
     *
     * @throws NoSuchMeasurementException When there was no currently captured `Measurement`.
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testLoadFinishedMeasurements_oneFinishedOneRunning() = runBlocking {
        oocut!!.newMeasurement(Modality.UNKNOWN)
        assertThat(oocut!!.hasMeasurement(MeasurementStatus.OPEN), equalTo(true))
        capturingBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
        assertThat(oocut!!.hasMeasurement(MeasurementStatus.OPEN), equalTo(false))
        oocut!!.newMeasurement(Modality.UNKNOWN)
        assertThat(oocut!!.hasMeasurement(MeasurementStatus.OPEN), equalTo(true))
        assertThat(oocut!!.loadMeasurements(MeasurementStatus.FINISHED).size, equalTo(1))
    }

    /**
     * Checks that calling [DefaultPersistenceLayer.loadMeasurements] on an empty database
     * returns an empty list.
     *
     */
    @Test
    fun testLoadFinishedMeasurements_noMeasurements() = runBlocking {
        assertThat(oocut!!.loadMeasurements(MeasurementStatus.FINISHED).isEmpty(), equalTo(true))
    }

    /**
     * Test that loading a [MeasurementStatus.FINISHED] `Measurement` works as expected.
     *
     * We don't create a FINISHED measurement because this will never happen like this in the code.
     * As a consequence we create an [MeasurementStatus.OPEN] as it would happen in the code,
     * then finish this measurement and then load it as FINISHED measurement as we usually do to synchronize them.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testLoadMeasurementSuccessfully() = runBlocking {
        val measurement = oocut!!.newMeasurement(Modality.UNKNOWN)
        val loadedOpenMeasurement = oocut!!.loadMeasurement(measurement.id)
        assertThat(
            loadedOpenMeasurement,
            CoreMatchers.`is`(equalTo(measurement))
        )
        capturingBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
        val finishedMeasurements = oocut!!.loadMeasurements(MeasurementStatus.FINISHED)
        assertThat(
            finishedMeasurements.size,
            CoreMatchers.`is`(equalTo(1))
        )
        assertThat(
            finishedMeasurements[0].id,
            CoreMatchers.`is`(equalTo(measurement.id))
        )
    }

    /**
     * Test that marking a measurement as synced works as expected.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testMarkMeasurementAsSynced() = runBlocking {
        val (id) = oocut!!.newMeasurement(Modality.UNKNOWN)
        capturingBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
        oocut!!.markFinishedAs(MeasurementStatus.SYNCED, id)

        // Check that measurement was marked as synced
        val syncedMeasurements = oocut!!.loadMeasurements(MeasurementStatus.SYNCED)
        assertThat(
            syncedMeasurements.size,
            CoreMatchers.`is`(equalTo(1))
        )
        assertThat(
            syncedMeasurements[0].id, CoreMatchers.`is`(
                equalTo(
                    id
                )
            )
        )

        // Check that sensor data was deleted
        val accelerationFile = oocut!!.fileIOHandler.getFilePath(
            context!!, id,
            Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION
        )
        require(!accelerationFile.exists())
        val rotationFile = oocut!!.fileIOHandler.getFilePath(
            context!!, id,
            Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION
        )
        require(!rotationFile.exists())
        val directionFile = oocut!!.fileIOHandler.getFilePath(
            context!!, id,
            Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION
        )
        require(!directionFile.exists())
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testGetSyncableMeasurement() = runBlocking {

        // Create a synchronized measurement
        insertSampleMeasurementWithData(context!!, MeasurementStatus.SYNCED, oocut!!, 1, 1)

        // Create a finished measurement
        val (id) = insertSampleMeasurementWithData(
            context!!, MeasurementStatus.FINISHED, oocut!!, 1,
            1
        )

        // Create an open measurement - must be created at last (life-cycle checks in PersistenceLayer.setStatus)
        insertSampleMeasurementWithData(context!!, MeasurementStatus.OPEN, oocut!!, 1, 1)

        // Check that syncable measurements = finishedMeasurement
        val loadedMeasurements = oocut!!.loadMeasurements(MeasurementStatus.FINISHED)
        assertThat(loadedMeasurements.size, CoreMatchers.`is`(1))
        assertThat(
            loadedMeasurements[0].id, CoreMatchers.`is`(
                equalTo(
                    id
                )
            )
        )
    }

    /**
     * Test that updating the distance in the [DefaultPersistenceLayer] during capturing works as expected.
     *
     * @throws NoSuchMeasurementException – if there was no measurement with the id .
     */
    @Test
    @Throws(NoSuchMeasurementException::class)
    fun testUpdateDistanceDuringCapturing() = runBlocking {
        // Arrange
        val (id) = oocut!!.newMeasurement(Modality.UNKNOWN)

        // Act
        oocut!!.setDistance(id, 2.0)

        // Assert
        var loadedMeasurement = oocut!!.loadCurrentlyCapturedMeasurement()
        assertThat(
            loadedMeasurement.distance,
            CoreMatchers.`is`(equalTo(2.0))
        )

        // Ensure a second distance update works as well
        oocut!!.setDistance(id, 4.0)
        loadedMeasurement = oocut!!.loadCurrentlyCapturedMeasurement()
        assertThat(
            loadedMeasurement.distance,
            CoreMatchers.`is`(equalTo(4.0))
        )
    }
}