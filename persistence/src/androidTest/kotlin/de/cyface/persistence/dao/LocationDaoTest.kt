/*
 * Copyright 2023 Cyface GmbH
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

import android.database.sqlite.SQLiteConstraintException
import de.cyface.persistence.Database
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Measurement
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the CRUD operations of the [LocationDao].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
class LocationDaoTest {
    private lateinit var database: Database
    private lateinit var measurementDao: MeasurementDao
    private lateinit var locationDao: LocationDao
    private var measurementId: Long? = null

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        locationDao = database.locationDao()
        measurementDao = database.measurementDao()
        // Insert a default measurement as each location needs a measurement in the database
        measurementId = createMeasurement().id
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testInsert() {
        // Arrange
        // Act
        createLocation(measurementId!!)

        // Assert
        assertThat(locationDao.getAll().size, equalTo(1))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun testInsert_withoutMeasurement() {
        // Arrange
        // Act
        createLocation(745674356783483465L)
        // Assert
    }

    @Test
    fun testInsertAll() {
        // Arrange
        val location1 = TestUtils.locationFixture(measurementId!!)
        val location2 = TestUtils.locationFixture(measurementId!!)

        // Act
        locationDao.insertAll(location1, location2)

        // Assert
        assertThat(locationDao.getAll().size, equalTo(2))
    }

    @Test
    fun testGetAll() {
        // Arrange
        val location1 = createLocation(measurementId!!)
        val location2 = createLocation(measurementId!!)

        // Act
        val locations = locationDao.getAll()

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testLoadAllByMeasurementId() {
        // Arrange
        val location1 = createLocation(measurementId!!)
        val location2 = createLocation(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        createLocation(otherMeasurementId)

        // Act
        val locations = locationDao.loadAllByMeasurementId(measurementId!!)

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testLoadAllByMeasurementIdAndSpeedGtAndAccuracyLtAndSpeedLt() {
        // Arrange
        val goodMeasurementId = measurementId!!
        val badMeasurementId = createMeasurement().id
        val goodSpeed = 1.01
        val lowSpeed = 1.0
        val highSpeed = 100.0
        val goodAccuracy = 5.0
        val badAccuracy = 20.0
        val location = createLocation(goodMeasurementId, goodSpeed, goodAccuracy)
        createLocation(badMeasurementId, goodSpeed, goodAccuracy)
        createLocation(goodMeasurementId, lowSpeed, goodAccuracy)
        createLocation(goodMeasurementId, highSpeed, goodAccuracy)
        createLocation(goodMeasurementId, goodSpeed, badAccuracy)

        // Act
        val locations = locationDao.loadAllByMeasurementIdAndSpeedGtAndAccuracyLtAndSpeedLt(
            measurementId!!,
            1.0,
            20.0,
            100.0
        )

        // Assert
        assertThat(locations.size, equalTo(1))
        assertThat(locations, equalTo(listOf(location)))
    }

    @Test
    fun testDeleteItemByMeasurementId() {
        // Arrange
        createLocation(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        val keep = createLocation(otherMeasurementId)

        // Act
        val deleted = locationDao.deleteItemByMeasurementId(measurementId!!)

        // Assert
        val kept = locationDao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
    }

    @Test
    fun testDeleteAll() {
        // Arrange
        for (i in 0..1) {
            createLocation(measurementId!!)
        }

        // Act
        val deleted = locationDao.deleteAll()

        // Assert
        assertThat(deleted, equalTo(2))
        assertThat(locationDao.getAll().size, equalTo(0))
    }

    /**
     * Creates an [Event] in the test database.
     *
     * @return The created object.
     */
    private fun createLocation(
        measurementId: Long,
        speed: Double = 1.01,
        accuracy: Double = 5.0
    ): GeoLocation {
        val location = TestUtils.locationFixture(measurementId, speed, accuracy)
        location.id = locationDao.insert(location)
        return location
    }

    /**
     * Creates a [Measurement] in the test database.
     *
     * @return The created object.
     */
    private fun createMeasurement(): Measurement {
        val measurement = TestUtils.measurementFixture()
        measurement.id = measurementDao.insert(measurement)
        return measurement
    }
}