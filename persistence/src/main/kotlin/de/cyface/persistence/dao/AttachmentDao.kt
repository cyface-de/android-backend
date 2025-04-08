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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.AttachmentTable
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.AttachmentStatus
import de.cyface.protos.model.File.FileType

/**
 * Data access object which provides the API to interact with the [Attachment] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 7.10.0
 */
@Dao
interface AttachmentDao {
    /**
     * Inserts a reference to an [Attachment] into the database.
     *
     * @param attachment The [Attachment] to create the reference for.
     * @return The identifier of the [Attachment] reference in the database.
     */
    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Insert
    suspend fun insertAll(vararg attachments: Attachment)

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH}")
    suspend fun getAll(): List<Attachment>

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} WHERE ${BaseColumns.ID} = :id")
    suspend fun loadById(id: Long): Attachment?

    @Query("SELECT ${BaseColumns.ID} FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${AttachmentTable.COLUMN_PATH} = :path " +
            "LIMIT 1")
    suspend fun loadIdByPath(path: String): Long?

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementId(measurementId: Long): List<Attachment>

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
            "LIMIT 1")
    suspend fun loadOneByMeasurementId(measurementId: Long): Attachment?

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
            "AND ${AttachmentTable.COLUMN_TYPE} = :type " +
            "ORDER BY ${BaseColumns.TIMESTAMP} ASC " +
            "LIMIT 1")
    suspend fun loadOneByMeasurementIdAndType(measurementId: Long, type: FileType): Attachment?

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
            "AND ${AttachmentTable.COLUMN_TYPE} = :type " +
            "ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementIdAndType(measurementId: Long, type: FileType): List<Attachment>

    @Query("SELECT * FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId " +
            "AND ${AttachmentTable.COLUMN_STATUS} = :status ORDER BY ${BaseColumns.TIMESTAMP} ASC")
    suspend fun loadAllByMeasurementIdAndStatus(measurementId: Long, status: AttachmentStatus): List<Attachment>

    /**
     * Returns the number of files found for a specific [measurementId].
     */
    @Query("SELECT COUNT(*) FROM ${AttachmentTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun countByMeasurementId(measurementId: Long): Int

    /**
     * Returns the number of files found for a specific [measurementId] and [type].
     */
    @Query("SELECT COUNT(*) FROM ${AttachmentTable.URI_PATH} " +
            "WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId AND ${AttachmentTable.COLUMN_TYPE} = :type")
    suspend fun countByMeasurementIdAndType(measurementId: Long, type: FileType): Int

    @Query("DELETE FROM ${AttachmentTable.URI_PATH} WHERE ${BaseColumns.MEASUREMENT_ID} = :measurementId")
    suspend fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM ${AttachmentTable.URI_PATH}")
    suspend fun deleteAll(): Int

    @Delete
    suspend fun delete(attachment: Attachment)

    @Query("UPDATE ${AttachmentTable.URI_PATH} SET ${AttachmentTable.COLUMN_SIZE} = :size " +
            "WHERE ${BaseColumns.ID} = :id")
    suspend fun updateSize(id: Long, size: Long)

    @Query("UPDATE ${AttachmentTable.URI_PATH} SET ${AttachmentTable.COLUMN_STATUS} = :status " +
            "WHERE ${BaseColumns.ID} = :id")
    suspend fun updateStatus(id: Long, status: AttachmentStatus)
}