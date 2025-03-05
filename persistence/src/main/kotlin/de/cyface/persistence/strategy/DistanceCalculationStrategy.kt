/*
 * Copyright 2019-2025 Cyface GmbH
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
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.ParcelableGeoLocation

/**
 * Interface for implementations used to calculate distances between geo-locations.
 *
 * This is used in:
 * - `DataCapturingBackgroundService#onLocationCaptured()` events to calculate the
 * [de.cyface.persistence.model.Measurement.distance] from [ParcelableGeoLocation] pairs.
 * - `LocationAnonymization` to calculate the distance used to trim location within a radius.
 *
 * Must be `Parcelable` to be passed from the `DataCapturingService` via `Intent`.
 *
 * @author Armin Schnabel
 * @version 3.1.0
 * @since 3.2.0
 */
interface DistanceCalculationStrategy : Parcelable {
    /**
     * Calculates the distance between two [ParcelableGeoLocation]s.
     *
     * @param location1 The first [ParcelableGeoLocation].
     * @param location2 The second [ParcelableGeoLocation].
     * @return The distance between the two [ParcelableGeoLocation]s in meters.
     */
    fun calculateDistance(
        location1: ParcelableGeoLocation,
        location2: ParcelableGeoLocation
    ): Double

    /**
     * Calculates the distance based on two [GeoLocation]s.
     *
     * @param location1 The first [GeoLocation].
     * @param location2 The second [GeoLocation].
     * @return The distance between the two [GeoLocation]s in meters.
     */
    fun calculateDistance(
        location1: GeoLocation,
        location2: GeoLocation
    ): Double
}
