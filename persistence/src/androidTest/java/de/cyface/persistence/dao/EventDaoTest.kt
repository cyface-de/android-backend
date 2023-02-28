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
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the CRUD operations of the [EventDao].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
class EventDaoTest {
    private lateinit var database: Database
    private lateinit var eventDao: EventDao
    private lateinit var measurementDao: MeasurementDao
    private var measurementId: Long? = null

    @Before
    fun setupDatabase() {
        database = TestUtils.createDatabase()
        eventDao = database.eventDao()
        measurementDao = database.measurementDao()
        // Insert a default measurement as each event needs a measurement in the database
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
        createEvent(measurementId!!)

        // Assert
        assertThat(eventDao.getAll().size, equalTo(1))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun testInsert_withoutMeasurement() {
        // Arrange
        // Act
        createEvent(745674356783483465L)
        // Assert
    }

    @Test
    fun testGetAll() {
        // Arrange
        val event1 = createEvent(measurementId!!)
        val event2 = createEvent(measurementId!!)

        // Act
        val events = eventDao.getAll()

        // Assert
        assertThat(events.size, equalTo(2))
        assertThat(events, equalTo(listOf(event1, event2)))
    }

    @Test
    fun testLoadById() {
        // Arrange
        val event = createEvent(measurementId!!)
        // Act
        val loadedEvent = eventDao.loadById(event.id)

        // Assert
        assertThat(loadedEvent, equalTo(event))
    }

    @Test
    fun testLoadAllByMeasurementId() {
        // Arrange
        val event1 = createEvent(measurementId!!)
        val event2 = createEvent(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        createEvent(otherMeasurementId)

        // Act
        val events = eventDao.loadAllByMeasurementId(measurementId!!)

        // Assert
        assertThat(events.size, equalTo(2))
        assertThat(events, equalTo(listOf(event1, event2)))
    }

    @Test
    fun testLoadAllByMeasurementIdAndType() {
        // Arrange
        val event = createEvent(measurementId!!, EventType.LIFECYCLE_START)
        val otherMeasurementId = createMeasurement().id
        createEvent(measurementId!!, EventType.LIFECYCLE_STOP)
        createEvent(otherMeasurementId, EventType.LIFECYCLE_START)
        createEvent(otherMeasurementId, EventType.LIFECYCLE_STOP)

        // Act
        val events = eventDao.loadAllByMeasurementIdAndType(measurementId!!, EventType.LIFECYCLE_START)

        // Assert
        assertThat(events.size, equalTo(1))
        assertThat(events, equalTo(listOf(event)))
    }

    @Test
    fun testDeleteItemByMeasurementId() {
        // Arrange
        createEvent(measurementId!!)
        val otherMeasurementId = createMeasurement().id
        val keep = createEvent(otherMeasurementId)

        // Act
        val deleted = eventDao.deleteItemByMeasurementId(measurementId!!)

        // Assert
        val kept = eventDao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
    }

    @Test
    fun testDeleteItemById() {
        // Arrange
        val event = createEvent(measurementId!!)
        val keep = createEvent(measurementId!!)

        // Act
        val deleted = eventDao.deleteItemById(event.id)

        // Assert
        val kept = eventDao.getAll()
        assertThat(deleted, equalTo(1))
        assertThat(kept.size, equalTo(1))
        assertThat(kept, equalTo(listOf(keep)))
    }

    @Test
    fun testDeleteAll() {
        // Arrange
        for (i in 0..1) {
            createEvent(measurementId!!)
        }

        // Act
        val deleted = eventDao.deleteAll()

        // Assert
        assertThat(deleted, equalTo(2))
        assertThat(eventDao.getAll().size, equalTo(0))
    }

    /**
     * Creates an [Event] in the test database.
     *
     * @return The created object.
     */
    private fun createEvent(measurementId: Long, type: EventType = EventType.LIFECYCLE_START): Event {
        val event = TestUtils.eventFixture(measurementId, type)
        event.id = eventDao.insert(event)
        return event
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