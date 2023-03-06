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
import java.util.Objects

/**
 * An abstract base class for all data points processed by Cyface. It provides the generic functionality of a data point
 * to be unique on this device and to have a Unix timestamp associated with it.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 * @property timestamp The Unix timestamp at which this [DataPoint] was measured in milliseconds.
 */
abstract class DataPoint(open val timestamp: Long) : Parcelable {
    init {
        require(timestamp >= 0L) { "Illegal argument: timestamp was less than 0L!" }
    }

    /*
     * MARK: Code for Parcelable interface.
     */

    /**
     * Recreates this point from the provided `Parcel`.
     *
     * @param in Serialized form of a [DataPoint].
     */
    protected constructor(`in`: Parcel) : this(`in`.readLong())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(timestamp)
    }

    override fun toString(): String {
        return "DataPoint{" +
                "timestamp=" + timestamp +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as DataPoint
        return timestamp == that.timestamp
    }

    override fun hashCode(): Int {
        return Objects.hash(timestamp)
    }
}