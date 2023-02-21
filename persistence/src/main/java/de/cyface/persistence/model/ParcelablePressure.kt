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
import java.util.Locale
import java.util.Objects

/**
 * This class represents a pressure [DataPoint], usually captured by a barometer.
 *
 * An instance of this class represents a data point captured and cached but not yet persisted. Such an
 * [Pressure] requires the measurement id to be set.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
open class ParcelablePressure : DataPoint {
    /**
     * The atmospheric pressure of this data point in hPa (millibar).
     */
    open val pressure: Double

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @param pressure The atmospheric pressure of this data point in hPa (millibar).
     */
    constructor(timestamp: Long, pressure: Double) : super(timestamp) {

        // Lowest/highest pressure on earth with a bounding box because of inaccuracy and weather. We only support
        // measuring between death see and mt. everest, no flying, diving and caves are supported.
        require(!(pressure < 250.0 || pressure > 1100.0)) {
            String.format(
                Locale.US,
                "Illegal value for pressure. Is required to be between 250.0 and 1_100.0 but was %f.",
                pressure
            )
        }
        this.pressure = pressure
    }
    /*
     * MARK: Parcelable Interface
     */
    /**
     * Constructor as required by `Parcelable` implementation.
     *
     * @param in A `Parcel` that is a serialized version of a data point.
     */
    protected constructor(`in`: Parcel) : super(`in`) {
        pressure = `in`.readDouble()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeDouble(pressure)
    }

    override fun toString(): String {
        return "Pressure{" +
                "pressure=" + pressure +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val pressure1 = other as ParcelablePressure
        return pressure1.pressure.compareTo(pressure) == 0
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), pressure)
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<ParcelablePressure?> = object : Creator<ParcelablePressure?> {
            override fun createFromParcel(`in`: Parcel): ParcelablePressure {
                return ParcelablePressure(`in`)
            }

            override fun newArray(size: Int): Array<ParcelablePressure?> {
                return arrayOfNulls(size)
            }
        }
    }
}