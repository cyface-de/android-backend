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

import android.location.Location
import android.location.LocationManager
import android.os.Parcel
import android.os.Parcelable.Creator
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.ParcelableGeoLocation

/**
 * The default implementation of the [DistanceCalculationStrategy] which calculates the
 * [de.cyface.persistence.model.Measurement.distance] using simply [Location.distanceTo].
 *
 * @author Armin Schnabel
 * @version 2.0.3
 * @since 3.2.0
 */
class DefaultDistanceCalculation : DistanceCalculationStrategy {
    /**
     * No arguments constructor is re-declared here, since it is overwritten by the constructor required by
     * `Parcelable`.
     */
    constructor() {
        // Nothing to do here
    }

    /**
     * Constructor as required by `Parcelable` implementation.
     *
     * @param in A `Parcel` that is a serialized version of a `IgnoreEventsStrategy`.
     */
    @Suppress("UNUSED_PARAMETER")
    private constructor(`in`: Parcel) {
        // Nothing to do here.
    }

    override fun calculateDistance(
        location1: ParcelableGeoLocation,
        location2: ParcelableGeoLocation
    ): Double {
        return calculateDistance(
            GeoLocation(location1, 0L),
            GeoLocation(location2, 0L),
        )
    }

    override fun calculateDistance(location1: GeoLocation, location2: GeoLocation): Double {
        // Changed to that the code is more similar to SR-calculated distance [STAD-518]
        val previousLocation = Location(LocationManager.GPS_PROVIDER)
        val nextLocation = Location(LocationManager.GPS_PROVIDER)
        previousLocation.latitude = location1.lat
        previousLocation.longitude = location1.lon
        previousLocation.speed = location1.speed.toFloat()
        previousLocation.time = location1.timestamp
        nextLocation.latitude = location2.lat
        nextLocation.longitude = location2.lon
        nextLocation.speed = location2.speed.toFloat()
        nextLocation.time = location2.timestamp
        // w/o `accuracy`, `distanceTo()` returns `0` on Samsung Galaxy S9 Android 10 [STAD-513]
        if (location1.accuracy != null) {
            previousLocation.accuracy = location1.accuracy.toFloat()
        }
        if (location2.accuracy != null) {
            nextLocation.accuracy = location2.accuracy.toFloat()
        }
        return previousLocation.distanceTo(nextLocation).toDouble()
    }

    override fun describeContents(): Int {
        // Nothing to do
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // Nothing to do
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<DefaultDistanceCalculation?> =
            object : Creator<DefaultDistanceCalculation?> {
                override fun createFromParcel(`in`: Parcel): DefaultDistanceCalculation {
                    return DefaultDistanceCalculation(`in`)
                }

                override fun newArray(size: Int): Array<DefaultDistanceCalculation?> {
                    return arrayOfNulls(size)
                }
            }
    }
}