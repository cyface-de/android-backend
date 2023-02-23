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
import de.cyface.persistence.model.ParcelableGeoLocation

/**
 * Interface for strategies to respond to `DataCapturingBackgroundService#onLocationCaptured()` events
 * to calculate the [de.cyface.persistence.model.Measurement.distance] from [ParcelableGeoLocation] pairs.
 *
 *
 * Must be `Parcelable` to be passed from the `DataCapturingService` via `Intent`.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.2.0
 */
interface DistanceCalculationStrategy : Parcelable {
    /**
     * Implements a strategy to calculate the [de.cyface.persistence.model.Measurement.distance] based on two subsequent
     * [ParcelableGeoLocation]s.
     *
     * @param lastLocation The `GeoLocation` captured before {@param newLocation}
     * @param newLocation The `GeoLocation` captured after {@param lastLocation}
     * @return The distance which is added to the `Measurement` based on the provided `GeoLocation`s.
     */
    fun calculateDistance(
        lastLocation: ParcelableGeoLocation,
        newLocation: ParcelableGeoLocation
    ): Double
}