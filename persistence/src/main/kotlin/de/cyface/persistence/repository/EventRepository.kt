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
package de.cyface.persistence.repository

import android.database.Cursor
import androidx.annotation.WorkerThread
import androidx.room.Query
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.EventTable
import de.cyface.persistence.dao.EventDao
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import kotlinx.coroutines.flow.Flow

/**
 * The repository offers a common interface for different data sources for a specific data type and
 * decides which data source to load the data from.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property dao The object to access data from the local persistence layer.
 */
class EventRepository(private val dao: EventDao) {

    @WorkerThread
    suspend fun insert(event: Event): Long {
        return dao.insert(event)
    }

    @WorkerThread
    suspend fun getAll(): List<Event> {
        return dao.getAll()
    }

    @WorkerThread
    suspend fun loadById(id: Long): Event? {
        return dao.loadById(id)
    }

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @WorkerThread
    suspend fun loadAllByMeasurementId(measurementId: Long): List<Event>? {
        return dao.loadAllByMeasurementId(measurementId)
    }

    /**
     * Loads and observes all [Event]s of a specified measurement.
     *
     * As this returns a `Flow`, queries are automatically run asynchronously on a background thread.
     */
    @WorkerThread
    fun observeAllByMeasurementId(measurementId: Long): Flow<List<Event>?> {
        return dao.observeAllByMeasurementId(measurementId)
    }

    /**
     * Returns a [Cursor] which points to a specific page defined by [limit] and [offset] of all events
     * of a measurement with a specified the [measurementId].
     *
     * This way we can reuse the code in `SyncAdapter` > `TransferFileSerializer` which queries and serializes
     * only 10_000 entries at a time which fixed performance issues with large measurements.
     *
     * The events are ordered by timestamp.
     */
    @WorkerThread
    suspend fun selectAllByMeasurementId(measurementId: Long, offset: Int, limit: Int): Cursor? {
        return dao.selectAllByMeasurementId(measurementId, offset, limit)
    }

    /**
     * Returns the number of events found for a specific [measurementId].
     */
    @WorkerThread
    suspend fun countByMeasurementId(measurementId: Long): Int {
        return dao.countByMeasurementId(measurementId)
    }

    /**
     * Ordered by timestamp is required.
     */
    @WorkerThread
    suspend fun loadAllByMeasurementIdAndType(measurementId: Long, type: EventType): List<Event>? {
        return dao.loadAllByMeasurementIdAndType(measurementId, type)
    }

    @WorkerThread
    suspend fun deleteItemByMeasurementId(measurementId: Long): Int {
        return dao.deleteItemById(measurementId)
    }

    @WorkerThread
    suspend fun deleteItemById(id: Long): Int {
        return dao.deleteItemById(id)
    }

    @WorkerThread
    suspend fun deleteAll(): Int {
        return dao.deleteAll()
    }
}