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
package de.cyface.persistence.model

import android.os.Parcel
import android.os.Parcelable.Creator
import android.util.Log
import androidx.room.Ignore
import de.cyface.persistence.Constants
import java.util.Locale
import java.util.Objects

/**
 * This class represents a geographical location, usually captured by a GNSS.
 *
 * An instance of this class represents a data point captured and cached but not yet persisted. Such an
 * [GeoLocation] requires the measurement id to be set.
 *
 * [ParcelableGeoLocation] DB Version 17 now contains accuracy in meters.
 * [ParcelableGeoLocation] accuracy is still in the old format (cm), vertical in the new (m)
 * FIXME This is fixed after merging `measures` and `v6` databases (both in m)
 *
 * This is fixed automatically in `measures` V16-V17 upgrade which is already implemented in the SDK 7 branch.
 * FIXME: but as we'll use the `GeoLocationsV6` which contain elevation data, we need to convert the accuracy there!
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 */
open class ParcelableGeoLocation : DataPoint {
    /**
     * The captured latitude of this data point in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    open val lat: Double

    /**
     * The captured longitude of this data point in decimal coordinates as a value between -180.0 and 180.0.
     */
    open val lon: Double

    /**
     * The captured altitude of this data point in meters above WGS 84 if available.
     */
    open val altitude: Double?

    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    open val speed: Double

    /**
     * The current accuracy of the measuring device in meters if available.
     *
     * FIXME: Write `null` when `Location.hasAccuracy()` is false. The transfer file format might need adjustment for that.
     */
    open val accuracy: Double?

    /**
     * The current vertical accuracy of the measuring device in meters if available.
     */
    open val verticalAccuracy: Double?

    /**
     * `True` if this location is considered "clean" by the provided [de.cyface.persistence.LocationCleaningStrategy].
     *
     * This is not persisted, as the validity can be different depending on the strategy implementation.
     */
    open var isValid: Boolean? = null

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since
     * 1.1.1970.
     * @param lat The captured latitude of this data point in decimal coordinates as a value between -90.0 (south
     * pole) and 90.0 (north pole).
     * @param lon The captured longitude of this data point in decimal coordinates as a value between -180.0
     * and 180.0.
     * @param altitude The captured altitude of this data point in meters above WGS 84.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in meters.
     * @param verticalAccuracy The current vertical accuracy of the measuring device in meters.
     */
    constructor(
        timestamp: Long, lat: Double, lon: Double, altitude: Double?,
        speed: Double, accuracy: Double?, verticalAccuracy: Double?
    ) : super(timestamp) {
        require(!(lat < -90.0 || lat > 90.0)) {
            String.format(
                Locale.US,
                "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %f.",
                lat
            )
        }
        require(!(lon < -180.0 || lon > 180.0)) {
            String.format(
                Locale.US,
                "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %f.",
                lon
            )
        }
        // lowest and highest point on earth with a few meters added because of inaccuracy
        require(!(altitude != null && (altitude < -500.0 || altitude > 10000.0))) {
            String.format(
                Locale.US,
                "Illegal value for altitude. Is required to be between -500.0 and 10_000.0 but was %f.",
                altitude
            )
        }
        if (speed < 0.0) {
            // Occurred on Huawei 10 Mate Pro (RAD-51)
            Log.w(
                Constants.TAG,
                String.format(
                    Locale.US,
                    "Illegal value for speed. Is required to be positive but was %f.",
                    speed
                )
            )
        }
        require(accuracy != null && accuracy >= 0.0) {
            String.format(
                Locale.US,
                "Illegal value for accuracy. Is required to be positive but was %f.", accuracy
            )
        }
        require(!(verticalAccuracy != null && verticalAccuracy < 0.0)) {
            String.format(
                Locale.US,
                "Illegal value for verticalAccuracy. Is required to be positive but was %f.",
                verticalAccuracy
            )
        }
        this.lat = lat
        this.lon = lon
        this.altitude = altitude
        this.speed = speed
        this.accuracy = accuracy
        this.verticalAccuracy = verticalAccuracy
    }

    /**
     * @param valid `True` if this location is considered "clean" by the provided
     * [de.cyface.persistence.LocationCleaningStrategy].
     */
    open fun setValid(valid: Boolean) {
        isValid = valid
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by `Parcelable` implementation.
     *
     * @param in A `Parcel` that is a serialized version of a data point.
     */
    @Ignore // Parcelable requires this constructor, make {@code Room} ignore this constructor.
    protected constructor(`in`: Parcel) : super(`in`) {
        lat = `in`.readDouble()
        lon = `in`.readDouble()
        altitude = `in`.readDouble()
        speed = `in`.readDouble()
        accuracy = `in`.readDouble()
        verticalAccuracy = `in`.readDouble()
        isValid = `in`.readByte().toInt() != 0
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeDouble(lat)
        dest.writeDouble(lon)
        dest.writeDouble(altitude!!)
        dest.writeDouble(speed)
        dest.writeDouble(accuracy!!)
        dest.writeDouble(verticalAccuracy!!)
        dest.writeByte((if (isValid!!) 1 else 0).toByte())
    }

    override fun toString(): String {
        return "ParcelableGeoLocation(lat=$lat, lon=$lon, altitude=$altitude, speed=$speed, accuracy=$accuracy, verticalAccuracy=$verticalAccuracy, isValid=$isValid)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ParcelableGeoLocation
        return (that.lat.compareTo(lat) == 0 && that.lon.compareTo(lon) == 0
                && that.speed.compareTo(speed) == 0 && that.accuracy == accuracy
                && altitude == that.altitude && verticalAccuracy == that.verticalAccuracy
                && isValid == that.isValid)
    }

    // To ease migration with `main` branch, we keep the models similar to `GeoLocation` but might want to change this
    // in future. https://github.com/cyface-de/android-backend/pull/258#discussion_r1071077508
    override fun hashCode(): Int {
        return Objects.hash(lat, lon, altitude, speed, accuracy, verticalAccuracy)
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<ParcelableGeoLocation?> = object : Creator<ParcelableGeoLocation?> {
            override fun createFromParcel(`in`: Parcel): ParcelableGeoLocation {
                return ParcelableGeoLocation(`in`)
            }

            override fun newArray(size: Int): Array<ParcelableGeoLocation?> {
                return arrayOfNulls(size)
            }
        }
    }
}