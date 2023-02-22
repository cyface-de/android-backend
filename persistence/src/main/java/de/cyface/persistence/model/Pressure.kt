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

import androidx.room.Entity
import java.util.Objects

/**
 * An `@Entity` which represents a persisted [ParcelablePressure], usually captured by a barometer.
 *
 * An instance of this class represents one row in a database table containing the pressure data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
 * @param pressure The atmospheric pressure of this data point in hPa (millibar).
 * @param measurementId The device-unique id of the measurement this data point belongs to.
 * This foreign key points to [Measurement.uid] and is indexed to avoid full table scan on parent update.
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Measurement::class,
        parentColumns = arrayOf("uid"),
        childColumns = arrayOf("measurementId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Pressure(
    override val timestamp: Long,
    override val pressure: Double,
    @field:ColumnInfo(index = true) val measurementId: Long
) : ParcelablePressure(timestamp, pressure) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val pressure1 = other as Pressure
        return (pressure1.pressure.compareTo(pressure) == 0
                && measurementId == pressure1.measurementId)
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), pressure, measurementId)
    }
}