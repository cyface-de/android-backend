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
import de.cyface.persistence.content.PressureTable
import de.cyface.persistence.model.Pressure

/**
 * Data access object which provides the API to interact with the [Pressure] database table.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.5.0
 */
@Dao
interface PressureDao {
    @Insert
    suspend fun insert(pressure: Pressure): Long

    @Insert
    suspend fun insertAll(vararg pressures: Pressure)

    @Query("SELECT * FROM ${PressureTable.URI_PATH}")
    suspend fun getAll(): List<Pressure>

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${PressureTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementId(measurementId: Long): List<Pressure>

    @Query("DELETE FROM ${PressureTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${PressureTable.URI_PATH}")
    suspend fun deleteAll(): Int
}