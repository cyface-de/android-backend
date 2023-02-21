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
import de.cyface.utils.CursorIsNullException

/**
 * The [PersistenceBehaviour] defines how the [PersistenceLayer] works. Select a behaviour depending on
 * if you want to use the `PersistenceLayer` to capture a new [Measurement] or to load existing data.
 *
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 3.0.0
 */
interface PersistenceBehaviour {
    /**
     * This is called in the `Persistence`'s constructor.
     */
    fun onStart(persistenceLayer: PersistenceLayer<*>)

    /**
     * This is called after a [PersistenceLayer.newMeasurement] was created.
     *
     * @param measurementId The id of the recently created [Measurement]
     */
    fun onNewMeasurement(measurementId: Long)

    /**
     * This is called when the [PersistenceLayer] is no longer needed by [PersistenceLayer.shutdown].
     */
    fun shutdown()

    /**
     * Loads the current [Measurement] if an [de.cyface.persistence.model.MeasurementStatus.OPEN] or [de.cyface.persistence.model.MeasurementStatus.PAUSED]
     * `Measurement` exists.
     *
     * @return The currently captured `Measurement`
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     * [de.cyface.persistence.model.MeasurementStatus.OPEN] or [de.cyface.persistence.model.MeasurementStatus.PAUSED] `Measurement`
     */
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun loadCurrentlyCapturedMeasurement(): Measurement
}