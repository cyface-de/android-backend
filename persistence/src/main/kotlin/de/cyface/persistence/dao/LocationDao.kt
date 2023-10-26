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

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.LocationTable
import de.cyface.persistence.model.GeoLocation

/**
 * Data access object which provides the API to interact with the [GeoLocation] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: GeoLocation): Long

    @Insert
    suspend fun insertAll(vararg locations: GeoLocation)

    @Query("SELECT * FROM ${LocationTable.URI_PATH}")
    suspend fun getAll(): List<GeoLocation>

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${LocationTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementId(measurementId: Long): List<GeoLocation>

    /**
     * Returns a [Cursor] which points to a specific page defined by [limit] and [offset] of all locations
     * of a measurement with a specified the [measurementId].
     *
     * This way we can reuse the code in `SyncAdapter` > `TransferFileSerializer` which queries and serializes
     * only 10_000 entries at a time which fixed performance issues with large measurements.
     *
     * The locations are ordered by timestamp.
     *
     * This could be replaced by room-paging, but it's not straight forward.
     */
    @Query("SELECT * FROM ${LocationTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC LIMIT :limit OFFSET :offset")
    fun selectAllByMeasurementId(measurementId: Long, offset: Int, limit: Int): Cursor?

    /**
     * Returns the number of locations found for a specific [measurementId].
     */
    @Query("SELECT COUNT(*) FROM ${LocationTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun countByMeasurementId(measurementId: Long): Int

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${LocationTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId AND ${LocationTable.COLUMN_SPEED} > :lowerSpeedThreshold AND ${LocationTable.COLUMN_ACCURACY} < :accuracyThreshold AND ${LocationTable.COLUMN_SPEED} < :upperSpeedThreshold ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementIdAndSpeedGtAndAccuracyLtAndSpeedLt(
        measurementId: Long,
        lowerSpeedThreshold: Double,
        accuracyThreshold: Double,
        upperSpeedThreshold: Double
    ): List<GeoLocation>

    @Query("DELETE FROM ${LocationTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${LocationTable.URI_PATH}")
    suspend fun deleteAll(): Int
}