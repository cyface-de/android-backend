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
import de.cyface.persistence.model.Pressure

/**
 * Data access object which provides the API to interact with the [Pressure] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@Dao
interface PressureDao {
    @Insert
    fun insert(pressure: Pressure): Long

    @Insert
    fun insertAll(vararg pressures: Pressure)

    @Query("SELECT * FROM Pressure")
    fun getAll(): List<Pressure>

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM Pressure WHERE measurementId = :measurementId ORDER BY timestamp ASC")
    fun loadAllByMeasurementId(measurementId: Long): List<Pressure>

    @Query("DELETE FROM Pressure WHERE measurementId = :measurementId")
    fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM Pressure")
    fun deleteAll(): Int
}