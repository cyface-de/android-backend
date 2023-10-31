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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.MeasurementTable
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data access object which provides the API to interact with the [Measurement] database table.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.5.0
 */
@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: Measurement): Long

    @Query("SELECT * FROM ${MeasurementTable.URI_PATH}")
    suspend fun getAll(): List<Measurement>

    /**
     * Loads all measurements which are not in the [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] state starting with the newest measurement.
     */
    @Query("SELECT * FROM ${MeasurementTable.URI_PATH} WHERE ${MeasurementTable.COLUMN_STATUS} NOT IN ('OPEN', 'PAUSED') ORDER BY ${BaseColumns.ID} DESC")
    suspend fun loadAllCompleted(): List<Measurement>

    /**
     * Loads and observes all measurements which are not in the [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] state starting with the newest measurement.
     */
    @Query("SELECT * FROM ${MeasurementTable.URI_PATH} WHERE ${MeasurementTable.COLUMN_STATUS} NOT IN ('OPEN', 'PAUSED') ORDER BY ${BaseColumns.ID} DESC")
    fun observeAllCompleted(): Flow<List<Measurement>>

    @Query("SELECT * FROM ${MeasurementTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    suspend fun loadById(id: Long): Measurement?

    /**
     * Loads and observes a measurement.
     *
     * As this returns a `Flow`, queries are automatically run asynchronously on a background thread.
     */
    @Query("SELECT * FROM ${MeasurementTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    fun observeById(id: Long): Flow<Measurement?>

    @Query("SELECT * FROM ${MeasurementTable.URI_PATH} WHERE ${MeasurementTable.COLUMN_STATUS} = :status")
    suspend fun loadAllByStatus(status: MeasurementStatus): List<Measurement>

    // Try simplified updates: [RFR-341]
    // https://developer.android.com/training/data-storage/room/accessing-data#convenience-update
    //@Update
    //fun update(vararg measurements: Measurement)

    @Query("UPDATE ${MeasurementTable.URI_PATH} SET ${MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION} = :fileFormatVersion WHERE ${BaseColumns.ID} = :id")
    suspend fun updateFileFormatVersion(id: Long, fileFormatVersion: Short): Int

    @Query("UPDATE ${MeasurementTable.URI_PATH} SET ${MeasurementTable.COLUMN_STATUS} = :status WHERE ${BaseColumns.ID} = :id")
    suspend fun update(id: Long, status: MeasurementStatus): Int

    @Query("UPDATE ${MeasurementTable.URI_PATH} SET ${MeasurementTable.COLUMN_DISTANCE} = :distance WHERE ${BaseColumns.ID} = :id")
    suspend fun updateDistance(id: Long, distance: Double): Int

    @Query("DELETE FROM ${MeasurementTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    suspend fun deleteItemById(id: Long): Int

    @Query("DELETE FROM ${MeasurementTable.URI_PATH}")
    suspend fun deleteAll(): Int
}