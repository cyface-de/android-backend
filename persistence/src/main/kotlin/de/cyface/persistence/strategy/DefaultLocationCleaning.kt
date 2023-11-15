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

import android.os.Parcel
import android.os.Parcelable.Creator
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.ParcelableGeoLocation
import kotlinx.coroutines.runBlocking

/**
 * An implementation of the [LocationCleaningStrategy] which uses simple lightweight filters
 * which can be applied "live".
 *
 * The goal is to ignore [ParcelableGeoLocation]s when standing still, to ignore very inaccurate locations and to
 * avoid
 * large distance "jumps", e.g. when the `LocationManager` implementation delivers an old, cached location at the
 * beginning of the track.
 *
 * @author Armin Schnabel
 * @version 1.1.2
 * @since 4.1.0
 */
class DefaultLocationCleaning : LocationCleaningStrategy {
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

    override fun isClean(location: ParcelableGeoLocation?): Boolean {
        return location!!.speed > LOWER_SPEED_THRESHOLD && location.accuracy!! < UPPER_ACCURACY_THRESHOLD && location.speed < UPPER_SPEED_THRESHOLD
    }

    override fun loadCleanedLocations(
        dao: LocationDao,
        measurementId: Long
    ): List<GeoLocation> = runBlocking {
        return@runBlocking dao.loadAllByMeasurementIdAndSpeedGtAndAccuracyLtAndSpeedLt(
            measurementId, LOWER_SPEED_THRESHOLD,
            UPPER_ACCURACY_THRESHOLD, UPPER_SPEED_THRESHOLD
        )
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
         * The lowest accuracy of [ParcelableGeoLocation]s in meters which is too "bad" to be used.
         */
        const val UPPER_ACCURACY_THRESHOLD = 20.0

        /**
         * The lower speed boundary in m/s which needs to be exceeded for the location to be "valid".
         */
        const val LOWER_SPEED_THRESHOLD = 1.0

        /**
         * The upper speed boundary in m/s which needs to be undershot for the location to be "valid".
         */
        const val UPPER_SPEED_THRESHOLD = 100.0

        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<DefaultLocationCleaning?> =
            object : Creator<DefaultLocationCleaning?> {
                override fun createFromParcel(`in`: Parcel): DefaultLocationCleaning {
                    return DefaultLocationCleaning(`in`)
                }

                override fun newArray(size: Int): Array<DefaultLocationCleaning?> {
                    return arrayOfNulls(size)
                }
            }
    }
}