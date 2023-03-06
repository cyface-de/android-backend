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
package de.cyface.persistence.strategy

import android.os.Parcelable
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.ParcelableGeoLocation

/**
 * Interface for strategies to filter ("clean") [ParcelableGeoLocation]s captured by
 * `DataCapturingBackgroundService#onLocationCaptured()` events.
 *
 * Must be `Parcelable` to be passed from the `DataCapturingService` via `Intent`.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.1.0
 */
interface LocationCleaningStrategy : Parcelable {
    /**
     * Implements a strategy to filter [ParcelableGeoLocation]s.
     *
     * @param location The `GeoLocation` to be checked
     * @return `True` if the `GeoLocation` is considered "clean" by this strategy.
     */
    fun isClean(location: ParcelableGeoLocation?): Boolean

    /**
     * Implements the SQL-equivalent to load only the "cleaned" [GeoLocation]s with the same filters as
     * in the [.isClean] implementation.
     *
     *
     * **Attention: The caller needs to wrap this method call with a try-finally block to ensure the returned
     * `Cursor` is always closed after use. The cursor cannot be closed within this implementation as it's
     * accessed by the caller.**
     *
     * @param dao the object that provides access to the [GeoLocation]s.
     * @param measurementId The identifier for the [de.cyface.persistence.model.Measurement] to load the track for.
     * @return A list which contains the "clean" [GeoLocation]s of that measurement.
     */
    fun loadCleanedLocations(dao: LocationDao, measurementId: Long): List<GeoLocation?>?
}