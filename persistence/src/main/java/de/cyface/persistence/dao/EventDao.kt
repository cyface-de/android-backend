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
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType

/**
 * Data access object which provides the API to interact with the [PersistedGeoLocation] database table.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
@Dao
interface EventDao {
    @Insert
    fun insert(event: Event) : Long

    @Query("SELECT * FROM Event")
    fun getAll(): List<Event>

    @Query("SELECT * FROM Event WHERE id = :id")
    fun loadById(id: Long): Event?

    /**
     * Ordered by timestamp for [de.cyface.persistence.DefaultPersistenceLayer.loadTracks] to work.
     */
    @Query("SELECT * FROM Event WHERE measurementId = :measurementId ORDER BY timestamp ASC")
    fun loadAllByMeasurementId(measurementId: Long): List<Event>

    /**
     * Ordered by timestamp is required.
     */
    @Query("SELECT * FROM Event WHERE measurementId = :measurementId AND type = :type ORDER BY timestamp ASC")
    fun loadAllByMeasurementIdAndType(measurementId: Long, type: EventType): List<Event>

    @Query("DELETE FROM Event WHERE measurementId = :measurementId")
    fun deleteItemByMeasurementId(measurementId: Long): Int

    @Query("DELETE FROM Event WHERE id = :id")
    fun deleteItemById(id: Long): Int

    @Query("DELETE FROM Event")
    fun deleteAll() : Int
}