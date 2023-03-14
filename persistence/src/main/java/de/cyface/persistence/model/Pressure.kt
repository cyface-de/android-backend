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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * An `@Entity` which represents a persisted [ParcelablePressure], usually captured by a barometer.
 *
 * An instance of this class represents one row in a database table containing the pressure data.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 6.3.0
 * @property id The system-wide unique identifier of this entity, generated by the data store.
 * It's `0`, which equals `null` in the non-nullable column `Long` when the entry is not yet persisted.
 * @property measurementId The device-unique id of the measurement this data point belongs to.
 * This foreign key points to [Measurement.id] and is indexed to avoid full table scan on parent update.
 */
@Entity(
    // Keep the table schema in sync with `ContentProvider`'s [PressureTable]
    foreignKeys = [ForeignKey(
        entity = Measurement::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("measurementId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Pressure(
    @ColumnInfo(name = "_id") // The CursorAdapter requires a column with the name `_id`
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    override val timestamp: Long,
    override val pressure: Double,
    @ColumnInfo(index = true) val measurementId: Long
) : ParcelablePressure(timestamp, pressure) {

    /**
     * Creates a new instance of this class which was not yet persisted and has [id] set to `0`.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since
     * 1.1.1970.
     * @property timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @property pressure The atmospheric pressure of this data point in hPa (millibar).
     * @property measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(timestamp: Long, pressure: Double, measurementId: Long) : this(
        0,
        timestamp,
        pressure,
        measurementId
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as Pressure

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (pressure != other.pressure) return false
        if (measurementId != other.measurementId) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}