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
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus

/**
 * Data access object which provides the API to interact with the [Measurement] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@Dao
interface MeasurementDao {
    @Query("SELECT * FROM Measurement")
    fun getAll(): List<Measurement?>?

    @Query("SELECT * FROM Measurement WHERE uid = :uid")
    fun loadByUid(uid: Long): Measurement?

    @Query("SELECT * FROM Measurement WHERE status = :status")
    fun loadAllByStatus(status: MeasurementStatus): List<Measurement?>?

    @Insert
    fun insert(measurement: Measurement?) : Long

    @Query("DELETE FROM Measurement WHERE uid = :uid")
    fun deleteItemByUid(uid: Long): Int

    @Query("UPDATE Measurement SET fileFormatVersion = :fileFormatVersion WHERE uid = :uid")
    fun updateFileFormatVersion(uid: Long, fileFormatVersion: Short): Int

    @Query("UPDATE Measurement SET status = :status WHERE uid = :uid")
    fun update(uid: Long, status: MeasurementStatus): Int

    @Query("UPDATE Measurement SET distance = :distance WHERE uid = :uid")
    fun updateDistance(uid: Long, distance: Double): Int
}