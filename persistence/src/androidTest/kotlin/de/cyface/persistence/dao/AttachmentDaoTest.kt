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
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.Measurement
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the CRUD operations of the [AttachmentDao].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
class AttachmentDaoTest {
    private lateinit var database: Database
    private lateinit var measurementDao: MeasurementDao
    private lateinit var dao: AttachmentDao
    private var measurementId: Long? = null

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        dao = database.attachmentDao()
        measurementDao = database.measurementDao()
        // Insert a default measurement as each pressure needs a measurement in the database
        measurementId = createMeasurement().id
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testInsert() = runBlocking {
        // Arrange
        // Act
        createEntry(measurementId!!)

        // Assert
        assertThat(dao.getAll().size, equalTo(1))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun testInsert_withoutMeasurement(): Unit = runBlocking {
        // Arrange
        // Act
        createEntry(745674356783483465L)
        // Assert
    }

    @Test
    fun testInsertAll() = runBlocking {
        // Arrange
        val entry1 = TestUtils.attachmentFixtures(measurementId!!)
        val entry2 = TestUtils.attachmentFixtures(measurementId!!)

        // Act
        dao.insertAll(entry1, entry2)

        // Assert
        assertThat(dao.getAll().size, equalTo(2))
    }

    @Test
    fun testGetAll() = runBlocking {
        // Arrange
        val location1 = createEntry(measurementId!!)
        val location2 = createEntry(measurementId!!)

        // Act
        val locations = dao.getAll()

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testLoadAllByMeasurementId() = runBlocking {
        // Arrange
        val location1 = createEntry(measurementId!!)
        val location2 = createEntry(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        createEntry(otherMeasurementId)

        // Act
        val locations = dao.loadAllByMeasurementId(measurementId!!)

        // Assert
        assertThat(locations.size, equalTo(2))
        assertThat(locations, equalTo(listOf(location1, location2)))
    }

    @Test
    fun testDeleteItemByMeasurementId() = runBlocking {
        // Arrange
        createEntry(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        val keep = createEntry(otherMeasurementId)

        // Act
        val deleted = dao.deleteItemByMeasurementId(measurementId!!)

        // Assert
        val kept = dao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
    }

    @Test
    fun testDeleteAll() = runBlocking {
        // Arrange
        for (i in 0..1) {
            createEntry(measurementId!!)
        }

        // Act
        val deleted = dao.deleteAll()

        // Assert
        assertThat(deleted, equalTo(2))
        assertThat(dao.getAll().size, equalTo(0))
    }

    /**
     * Creates an entry in the test database.
     *
     * @return The created object.
     */
    private suspend fun createEntry(measurementId: Long): Attachment {
        val entry = TestUtils.attachmentFixtures(measurementId)
        entry.id = dao.insert(entry)
        return entry
    }

    /**
     * Creates a [Measurement] in the test database.
     *
     * @return The created object.
     */
    private fun createMeasurement(): Measurement = runBlocking {
        val measurement = TestUtils.measurementFixture()
        measurement.id = measurementDao.insert(measurement)
        return@runBlocking measurement
    }
}