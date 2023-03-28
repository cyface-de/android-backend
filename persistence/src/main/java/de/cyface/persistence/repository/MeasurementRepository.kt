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
package de.cyface.persistence.repository

import androidx.annotation.WorkerThread
import de.cyface.persistence.dao.MeasurementDao
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import kotlinx.coroutines.flow.Flow

/**
 * The repository offers a common interface for different data sources for a specific data type and
 * decides which data source to load the data from.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property dao The object to access data from the local persistence layer.
 */
class MeasurementRepository(private val dao: MeasurementDao) {

    // `suspend` tells the compiler this is long running code and may not be executed from the main thread.

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(measurement: Measurement): Long {
        return dao.insert(measurement)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun getAll(): List<Measurement> {
        return dao.getAll()
    }

    /**
     * Observes all entries.
     */
    @WorkerThread
    fun observeAllCompleted(): Flow<List<Measurement>> {
        return dao.observeAllCompleted()
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun loadAllCompleted(): List<Measurement> {
        return dao.loadAllCompleted()
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun loadById(id: Long): Measurement? {
        return dao.loadById(id)
    }

    /**
     * Observes a single entry.
     *
     * @param id The id of the [Measurement] to observe.
     */
    @WorkerThread
    fun observeById(id: Long): Flow<Measurement?> {
        return dao.observeById(id)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun loadAllByStatus(status: MeasurementStatus): List<Measurement> {
        return dao.loadAllByStatus(status)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun update(measurement: Measurement) {
        dao.update(measurement)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun updateFileFormatVersion(id: Long, fileFormatVersion: Short): Int {
        return dao.updateFileFormatVersion(id, fileFormatVersion)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun update(id: Long, status: MeasurementStatus): Int {
        return dao.update(id, status)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun updateDistance(id: Long, distance: Double): Int {
        return dao.updateDistance(id, distance)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun deleteItemById(id: Long): Int {
        return dao.deleteItemById(id)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun deleteAll(): Int {
        return dao.deleteAll()
    }
}