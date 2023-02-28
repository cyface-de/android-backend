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
package de.cyface.persistence.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.cyface.persistence.Database
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the CRUD operations of the [MeasurementDao].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@RunWith(AndroidJUnit4::class)
class MeasurementDaoTest {
    private lateinit var database: Database
    private lateinit var measurementDao: MeasurementDao
    private lateinit var eventDao: EventDao
    private lateinit var locationDao: GeoLocationDao
    private lateinit var pressureDao: PressureDao

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        measurementDao = database.measurementDao()
        eventDao = database.eventDao()
        locationDao = database.geoLocationDao()
        pressureDao = database.pressureDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testInsert() {
        // Arrange
        // Act
        createMeasurement(MeasurementStatus.OPEN, true)

        // Assert
        assertThat(measurementDao.getAll().size, equalTo(1))
        assertThat(eventDao.getAll().size, equalTo(1))
        assertThat(locationDao.getAll().size, equalTo(1))
        assertThat(pressureDao.getAll().size, equalTo(1))
    }

    @Test
    fun testGetAll() {
        // Arrange
        val measurement1 = createMeasurement()
        val measurement2 = createMeasurement()

        // Act
        val measurements = measurementDao.getAll()

        // Assert
        assertThat(measurements.size, equalTo(2))
        assertThat(measurements, equalTo(listOf(measurement1, measurement2)))
    }

    @Test
    fun testLoadById() {
        // Arrange
        val measurement = createMeasurement()
        // Act
        val loadedMeasurement = measurementDao.loadById(measurement.id)

        // Assert
        assertThat(loadedMeasurement, equalTo(measurement))
    }

    @Test
    fun testLoadAllByStatus() {
        // Arrange
        val openMeasurement = createMeasurement(MeasurementStatus.OPEN)
        createMeasurement(MeasurementStatus.FINISHED)

        // Act
        val measurements = measurementDao.loadAllByStatus(MeasurementStatus.OPEN)

        // Assert
        assertThat(measurements.size, equalTo(1))
        assertThat(measurements, equalTo(listOf(openMeasurement)))
    }

    @Test
    fun testUpdateFileFormatVersion() {
        // Arrange
        val measurement = createMeasurement()

        // Act
        val updates = measurementDao.updateFileFormatVersion(measurement.id, 123)

        // Assert
        val updatedMeasurement = measurementDao.loadById(measurement.id)
        assertThat(updates, equalTo(1))
        assertThat(updatedMeasurement!!.fileFormatVersion, equalTo(123))
    }

    @Test
    fun testUpdate_withStatus() {
        // Arrange
        val measurement = createMeasurement(MeasurementStatus.OPEN)

        // Act
        val updates = measurementDao.update(measurement.id, MeasurementStatus.PAUSED)

        // Assert
        val updatedMeasurement = measurementDao.loadById(measurement.id)
        assertThat(updates, equalTo(1))
        assertThat(updatedMeasurement!!.status, equalTo(MeasurementStatus.PAUSED))
    }

    @Test
    fun testUpdateDistance() {
        // Arrange
        val measurement = createMeasurement()

        // Act
        val updates = measurementDao.updateDistance(measurement.id, 123.0)

        // Assert
        val updatedMeasurement = measurementDao.loadById(measurement.id)
        assertThat(updates, equalTo(1))
        assertThat(updatedMeasurement!!.distance, equalTo(123.0))
    }

    @Test
    fun testDeleteItemById() {
        // Arrange
        val measurement = createMeasurement(MeasurementStatus.OPEN, true)
        val keep = createMeasurement()

        // Act
        val deleted = measurementDao.deleteItemById(measurement.id)

        // Assert
        val kept = measurementDao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
        assertThat(eventDao.getAll().size, equalTo(0))
        assertThat(locationDao.getAll().size, equalTo(0))
        assertThat(pressureDao.getAll().size, equalTo(0))
    }

    @Test
    fun testDeleteAll() {
        // Arrange
        for (i in 0..1) {
            createMeasurement(MeasurementStatus.OPEN, true)
        }

        // Act
        val deleted = measurementDao.deleteAll()

        // Assert
        assertThat(deleted, equalTo(2))
        assertThat(measurementDao.getAll().size, equalTo(0))
        assertThat(eventDao.getAll().size, equalTo(0))
        assertThat(locationDao.getAll().size, equalTo(0))
        assertThat(pressureDao.getAll().size, equalTo(0))
    }

    /**
     * Creates a [Measurement] in the test database.
     *
     * @param status The [MeasurementStatus] to set.
     * @param withData `True` if a sample event, location and pressure entry should be created for the measurement.
     * @return The created object.
     */
    private fun createMeasurement(
        status: MeasurementStatus = MeasurementStatus.OPEN,
        withData: Boolean = false
    ): Measurement {
        val measurement = TestUtils.measurementFixture(status)
        measurement.id = measurementDao.insert(measurement)
        if (withData) {
            val eventFixture = TestUtils.eventFixture(measurement.id)
            val locationFixture = TestUtils.locationFixture(measurement.id)
            val pressureFixture = TestUtils.pressureFixtures(measurement.id)
            eventDao.insert(eventFixture)
            locationDao.insertAll(locationFixture)
            pressureDao.insertAll(pressureFixture)
        }
        return measurement
    }
}