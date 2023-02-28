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
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Pressure
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the CRUD operations of the [GeoLocationDao].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
class PressureDaoTest {
    private lateinit var database: Database
    private lateinit var measurementDao: MeasurementDao
    private lateinit var pressureDao: PressureDao
    private var measurementId: Long? = null

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        pressureDao = database.pressureDao()
        measurementDao = database.measurementDao()
        // Insert a default measurement as each pressure needs a measurement in the database
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
        createPressure(measurementId!!)

        // Assert
        assertThat(pressureDao.getAll().size, equalTo(1))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun testInsert_withoutMeasurement() {
        // Arrange
        // Act
        createPressure(745674356783483465L)
        // Assert
    }

    @Test
    fun testInsertAll() {
        // Arrange
        val pressure1 = TestUtils.pressureFixtures(measurementId!!)
        val pressure2 = TestUtils.pressureFixtures(measurementId!!)

        // Act
        pressureDao.insertAll(pressure1, pressure2)

        // Assert
        assertThat(pressureDao.getAll().size, equalTo(2))
    }

    @Test
    fun testGetAll() {
        // Arrange
        val location1 = createPressure(measurementId!!)
        val location2 = createPressure(measurementId!!)

        // Act
        val locations = pressureDao.getAll()

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testLoadAllByMeasurementId() {
        // Arrange
        val location1 = createPressure(measurementId!!)
        val location2 = createPressure(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        createPressure(otherMeasurementId)

        // Act
        val locations = pressureDao.loadAllByMeasurementId(measurementId!!)

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testDeleteItemByMeasurementId() {
        // Arrange
        createPressure(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        val keep = createPressure(otherMeasurementId)

        // Act
        val deleted = pressureDao.deleteItemByMeasurementId(measurementId!!)

        // Assert
        val kept = pressureDao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
    }

    @Test
    fun testDeleteAll() {
        // Arrange
        for (i in 0..1) {
            createPressure(measurementId!!)
        }

        // Act
        val deleted = pressureDao.deleteAll()

        // Assert
        assertThat(deleted, equalTo(2))
        assertThat(pressureDao.getAll().size, equalTo(0))
    }

    /**
     * Creates an [Event] in the test database.
     *
     * @return The created object.
     */
    private fun createPressure(measurementId: Long): Pressure {
        val pressure = TestUtils.pressureFixtures(measurementId)
        pressure.id = pressureDao.insert(pressure)
        return pressure
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