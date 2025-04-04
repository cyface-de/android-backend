/*
 * Copyright 2023-2025 Cyface GmbH
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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.EventTable
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import kotlinx.coroutines.flow.Flow

/**
 * Data access object which provides the API to interact with the
 * [de.cyface.persistence.model.GeoLocation] database table.
 *
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 7.5.0
 */
@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event): Long

    @Query("SELECT * FROM ${EventTable.URI_PATH}")
    suspend fun getAll(): List<Event>

    @Query("SELECT * FROM ${EventTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    suspend fun loadById(id: Long): Event?

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query(
        "SELECT * FROM ${EventTable.URI_PATH} " +
                "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
                "ORDER BY timestamp ASC"
    )
    suspend fun loadAllByMeasurementId(measurementId: Long): List<Event>?

    /**
     * Loads and observes all [Event]s of a specified measurement.
     *
     * As this returns a `Flow`, queries are automatically run asynchronously on a background thread.
     */
    @Query(
        "SELECT * FROM ${EventTable.URI_PATH} " +
                "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
                "ORDER BY timestamp ASC"
    )
    fun observeAllByMeasurementId(measurementId: Long): Flow<List<Event>?>

    /**
     * Returns the number of events found for a specific [measurementId].
     */
    @Query("SELECT COUNT(*) FROM ${EventTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun countByMeasurementId(measurementId: Long): Int

    /**
     * Ordered by timestamp is required.
     */
    @Query(
        "SELECT * FROM ${EventTable.URI_PATH} " +
                "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
                "AND ${EventTable.COLUMN_TYPE} = :type " +
                "ORDER BY ${BaseColumns.TIMESTAMP} ASC"
    )
    suspend fun loadAllByMeasurementIdAndType(measurementId: Long, type: EventType): List<Event>?

    @Query("DELETE FROM ${EventTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${EventTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    suspend fun deleteItemById(id: Long): Int

    @Query("DELETE FROM ${EventTable.URI_PATH}")
    suspend fun deleteAll(): Int
}