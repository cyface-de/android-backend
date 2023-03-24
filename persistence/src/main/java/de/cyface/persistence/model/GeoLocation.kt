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
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * An `@Entity` which represents a persisted [ParcelableGeoLocation], usually captured by a GNSS.
 *
 * An instance of this class represents one row in a database table containing the location data.
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
    // Keep the table schema in sync with `ContentProvider`'s [LocationTable]
    tableName = "Location",
    foreignKeys = [ForeignKey(
        entity = Measurement::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("measurementId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class GeoLocation(
    @ColumnInfo(name = "_id") // The CursorAdapter requires a column with the name `_id`
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    override val timestamp: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double?,
    override val speed: Double,
    override val accuracy: Double?,
    override val verticalAccuracy: Double?,
    @Ignore
    override var isValid: Boolean = true,
    @ColumnInfo(index = true)
    val measurementId: Long
) : ParcelableGeoLocation(timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy) {

    /**
     * Creates a new instance of this class which was not yet persisted and has [id] set to `0`.
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
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(
        timestamp: Long, lat: Double, lon: Double, altitude: Double?,
        speed: Double, accuracy: Double?, verticalAccuracy: Double?, measurementId: Long
    ) : this(
        0,
        timestamp,
        lat,
        lon,
        altitude,
        speed,
        accuracy,
        verticalAccuracy,
        true,
        measurementId
    )

    /**
     * Creates a new instance of this class which was not yet persisted and has [id] set to `0`.
     *
     * @param location The cached [ParcelableGeoLocation] to create the [GeoLocation] from.
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(location: ParcelableGeoLocation, measurementId: Long) : this(
        location.timestamp, location.lat, location.lon, location.altitude,
        location.speed, location.accuracy, location.verticalAccuracy, measurementId
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as GeoLocation

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (lat != other.lat) return false
        if (lon != other.lon) return false
        if (altitude != other.altitude) return false
        if (speed != other.speed) return false
        if (accuracy != other.accuracy) return false
        if (verticalAccuracy != other.verticalAccuracy) return false
        if (isValid != other.isValid) return false
        if (measurementId != other.measurementId) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}