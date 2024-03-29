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
        lastLocation: ParcelableGeoLocation,
        newLocation: ParcelableGeoLocation
    ): Double {
        // Changed to that the code is more similar to SR-calculated distance [STAD-518]
        val previousLocation = Location(LocationManager.GPS_PROVIDER)
        val nextLocation = Location(LocationManager.GPS_PROVIDER)
        previousLocation.latitude = lastLocation.lat
        previousLocation.longitude = lastLocation.lon
        previousLocation.speed = lastLocation.speed.toFloat()
        previousLocation.time = lastLocation.timestamp
        nextLocation.latitude = newLocation.lat
        nextLocation.longitude = newLocation.lon
        nextLocation.speed = newLocation.speed.toFloat()
        nextLocation.time = newLocation.timestamp
        // w/o `accuracy`, `distanceTo()` returns `0` on Samsung Galaxy S9 Android 10 [STAD-513]
        if (lastLocation.accuracy != null) {
            previousLocation.accuracy = lastLocation.accuracy!!.toFloat()
        }
        if (newLocation.accuracy != null) {
            nextLocation.accuracy = newLocation.accuracy!!.toFloat()
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