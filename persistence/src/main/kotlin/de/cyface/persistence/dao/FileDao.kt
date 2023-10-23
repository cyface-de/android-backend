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
import de.cyface.persistence.content.FileTable
import de.cyface.persistence.model.File

/**
 * Data access object which provides the API to interact with the [File] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
@Dao
interface FileDao {
    @Insert
    fun insert(file: File): Long

    @Insert
    fun insertAll(vararg files: File)

    @Query("SELECT * FROM ${FileTable.URI_PATH}")
    fun getAll(): List<File>

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    fun loadAllByMeasurementId(measurementId: Long): List<File>

    @Query("DELETE FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${FileTable.URI_PATH}")
    fun deleteAll(): Int
}