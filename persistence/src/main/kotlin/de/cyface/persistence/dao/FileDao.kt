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
import de.cyface.persistence.model.FileStatus
import de.cyface.protos.model.File.FileType

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

    @Query("SELECT * FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    fun loadById(id: Long): File?

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    fun loadAllByMeasurementId(measurementId: Long): List<File>

    @Query("SELECT * FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId LIMIT 1")
    fun loadOneByMeasurementId(measurementId: Long): File?

    @Query("SELECT * FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId AND ${FileTable.COLUMN_STATUS} = :status ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    fun loadAllByMeasurementIdAndStatus(measurementId: Long, status: FileStatus): List<File>

    /**
     * Returns the number of files found for a specific [measurementId].
     */
    @Query("SELECT COUNT(*) FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    fun countByMeasurementId(measurementId: Long): Int

    /**
     * Returns the number of files found for a specific [measurementId] and [type].
     */
    @Query("SELECT COUNT(*) FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId AND ${FileTable.COLUMN_TYPE} = :type")
    fun countByMeasurementIdAndType(measurementId: Long, type: FileType): Int

    @Query("DELETE FROM ${FileTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${FileTable.URI_PATH}")
    fun deleteAll(): Int

    @Query("UPDATE ${FileTable.URI_PATH} SET ${FileTable.COLUMN_SIZE} = :size WHERE ${BaseColumns.ID} = :id")
    suspend fun updateSize(id: Long, size: Long)

    @Query("UPDATE ${FileTable.URI_PATH} SET ${FileTable.COLUMN_STATUS} = :status WHERE ${BaseColumns.ID} = :id")
    suspend fun updateStatus(id: Long, status: FileStatus)
}