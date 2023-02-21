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
import de.cyface.model.Point3D
import java.util.Objects

/**
 * This class represents a [DataPoint] with three coordinates such as an acceleration-, rotation- or direction point.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 1.0.0
 */
open class ParcelablePoint3D : DataPoint, Point3D {
    /**
     * The x component of the data point.
     */
    override val x: Float
    /**
     * The y component of the data point.
     */
    override val y: Float
    /**
     * The z component of the data point.
     */
    override val z: Float

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @param x The x component of the data point.
     * @param y The y component of the data point.
     * @param z The z component of the data point.
     */
    constructor(timestamp: Long, x: Float, y: Float, z: Float) : super(timestamp) {
        this.x = x
        this.y = y
        this.z = z
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
        x = `in`.readFloat()
        y = `in`.readFloat()
        z = `in`.readFloat()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeFloat(x)
        dest.writeFloat(y)
        dest.writeFloat(z)
    }

    override fun toString(): String {
        return "Point3d(x=$x, y=$y, z=$z)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val point3D1 = other as ParcelablePoint3D
        return point3D1.x.compareTo(x) == 0
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), x, y, z)
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<ParcelablePoint3D?> = object : Creator<ParcelablePoint3D?> {
            override fun createFromParcel(`in`: Parcel): ParcelablePoint3D {
                return ParcelablePoint3D(`in`)
            }

            override fun newArray(size: Int): Array<ParcelablePoint3D?> {
                return arrayOfNulls(size)
            }
        }
    }
}