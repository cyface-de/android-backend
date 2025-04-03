/*
 * Copyright 2019-2023 Cyface GmbH
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
package de.cyface.persistence

import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement

/**
 * This [PersistenceBehaviour] is used when a [DefaultPersistenceLayer] is only used to access existing
 * [Measurement]s and does not want to capture new `Measurements`.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
class DefaultPersistenceBehaviour : PersistenceBehaviour {
    private var persistenceLayer: DefaultPersistenceLayer<*>? = null
    override fun onStart(persistenceLayer: DefaultPersistenceLayer<*>) {
        this.persistenceLayer = persistenceLayer
    }

    override fun onNewMeasurement(measurementId: Long) {
        // nothing to do
    }

    override fun shutdown() {
        // nothing to do
    }

    @Throws(NoSuchMeasurementException::class)
    override suspend fun loadCurrentlyCapturedMeasurement(): Measurement {

        // The {@code DefaultPersistenceBehaviour} does not have a cache for this so load it from the persistence
        return persistenceLayer!!.loadCurrentlyCapturedMeasurementFromPersistence()
            ?: throw NoSuchMeasurementException(
                "Trying to load currently captured measurement while no measurement was open or paused!"
            )
    }
}