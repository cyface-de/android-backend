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
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.model.Identifier

/**
 * The repository mediates between different data sources for [Identifier].
 *
 * It manages identifier queries and decides to fetch data from local persistence or network.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property dao The object to access identifier data from the persistence layer.
 */
class IdentifierRepository(private val dao: IdentifierDao) {

    // Room runs suspend queries off the main thread.
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(identifier: Identifier) {
        dao.insert(identifier)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun getAll(): List<Identifier> {
        return dao.getAll()
    }
}