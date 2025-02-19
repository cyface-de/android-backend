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

/**
 * This class represents a geographical location, usually captured by a GNSS.
 *
 * An instance of this class represents a data point captured and cached but not yet persisted. Such an
 * [GeoLocation] requires the measurement id to be set.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 * @param timestamp The time at which this data point was captured in milliseconds since 1.1.1970.
 * @property lat The captured latitude of this data point in decimal coordinates as a value between
 * -90.0 (south pole) and 90.0 (north pole).
 * @property lon The captured longitude of this data point in decimal coordinates as a value between
 * -180.0 and 180.0.
 * @property altitude The captured altitude of this data point in meters above WGS 84 if available.
 * @property speed The current speed of the measuring device according to its location sensor in
 * meters per second.
 * @property accuracy The current accuracy of the measuring device in meters if available.
 * @property verticalAccuracy The current vertical accuracy of the measuring device in meters if
 * available.
 */
open class ParcelableGeoLocation(
    timestamp: Long,
    open val lat: Double,
    open val lon: Double,
    open val altitude: Double?,
    open val speed: Double,
    open val accuracy: Double?,
    open val verticalAccuracy: Double?
) : DataPoint(timestamp) {
    /**
     *`True` if this location is considered "clean" by the provided
     * [de.cyface.persistence.strategy.LocationCleaningStrategy].
     *
     * This is not persisted, as the validity can be different depending on the strategy
     * implementation.
     */
    open var isValid: Boolean = true

    init {
        require(timestamp >= 0L) { "Illegal argument: timestamp was less than 0L!" }
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
        require(!(altitude != null && (altitude!! < -500.0 || altitude!! > 10000.0))) {
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
        require(accuracy != null && accuracy!! >= 0.0) {
            String.format(
                Locale.US,
                "Illegal value for accuracy. Is required to be positive but was %f.", accuracy
            )
        }
        require(!(verticalAccuracy != null && verticalAccuracy!! < 0.0)) {
            String.format(
                Locale.US,
                "Illegal value for verticalAccuracy. Is required to be positive but was %f.",
                verticalAccuracy
            )
        }
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
    protected constructor(`in`: Parcel) : this(
        `in`.readLong(),
        `in`.readDouble(),
        `in`.readDouble(),
        `in`.readValue(Double::class.java.classLoader) as? Double, // supports `null` [STAD-496]
        `in`.readDouble(),
        `in`.readValue(Double::class.java.classLoader) as? Double, // supports `null` [STAD-496]
        `in`.readValue(Double::class.java.classLoader) as? Double, // supports `null` [STAD-496]
    ) {
        isValid = `in`.readByte().toInt() > 0
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeDouble(lat)
        dest.writeDouble(lon)
        dest.writeSerializable(altitude) // to support `null` as value [STAD-496]
        dest.writeDouble(speed)
        dest.writeSerializable(accuracy) // to support `null` as value [STAD-496]
        dest.writeSerializable(verticalAccuracy) // to support `null` as value [STAD-496]
        dest.writeByte((if (isValid) 1 else 0).toByte())
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

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + lat.hashCode()
        result = 31 * result + lon.hashCode()
        result = 31 * result + (altitude?.hashCode() ?: 0)
        result = 31 * result + speed.hashCode()
        result = 31 * result + (accuracy?.hashCode() ?: 0)
        result = 31 * result + (verticalAccuracy?.hashCode() ?: 0)
        return result
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