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
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.Objects

/**
 * An abstract base class for all data points processed by Cyface. It provides the generic functionality of a data point
 * to be unique on this device and to have a Unix timestamp associated with it.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
abstract class DataPoint : Parcelable {
    /**
     * The system wide unique identifier for this [DataPoint]. If the point was not saved yet it may be
     * `null`, since the persistence layers assigns a unique identifier on saving a data point.
     * For AndroidDataAccessLayer.getIdOfNextUnSyncedMeasurement() to work the id must be long ASC
     */
    @PrimaryKey(autoGenerate = true)
    var uid = 0

    /**
     * The Unix timestamp at which this [DataPoint] was measured in milliseconds.
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long

    /**
     * Manually implemented getter to resolve [Point3D] interface
     *
     * @return The Unix timestamp at which this [DataPoint] was measured in milliseconds.
     * /
    fun getTimestamp(): Long {
        return timestamp
    }*/

    /**
     * Creates a new completely initialized `DataPointV6`.
     *
     * @param timestamp The Unix timestamp at which this `DataPointV6` was measured in milliseconds.
     */
    constructor(timestamp: Long) {
        require(timestamp >= 0L) { "Illegal argument: timestamp was less than 0L!" }
        this.timestamp = timestamp
    }

    /*
     * MARK: Code for Parcelable interface.
     */

    /**
     * Recreates this point from the provided `Parcel`.
     *
     * @param in Serialized form of a `DataPointV6`.
     */
    protected constructor(`in`: Parcel) {
        uid = (`in`.readValue(javaClass.classLoader) as Int?)!!
        timestamp = `in`.readLong()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeValue(uid)
        dest.writeLong(timestamp)
    }

    override fun toString(): String {
        return "DataPointV6{" +
                "uid=" + uid +
                ", timestamp=" + timestamp +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as DataPoint
        return uid == that.uid && timestamp == that.timestamp
    }

    // To ease migration with `main` we keep the `hashCode()` similar to `DataPoint`:
    // https://github.com/cyface-de/android-backend/pull/258#discussion_r1071071508
    override fun hashCode(): Int {
        return Objects.hash(uid)
    }
}