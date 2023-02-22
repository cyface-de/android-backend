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

/**
 * An `@Entity` which represents a persisted [ParcelableGeoLocation], usually captured by a GNSS.
 *
 * An instance of this class represents one row in a database table containing the location data.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 6.3.0
 */
@Entity(
    tableName = "Location",
    foreignKeys = [ForeignKey(
        entity = Measurement::class,
        parentColumns = arrayOf("uid"),
        childColumns = arrayOf("measurementId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class GeoLocation(
    /**
     * The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     */
    override val timestamp: Long,
    /**
     * The captured latitude of this data point in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    override val lat: Double,
    /**
     * The captured longitude of this data point in decimal coordinates as a value between -180.0 and 180.0.
     */
    override val lon: Double,
    /**
     * The captured altitude of this data point in meters above WGS 84 if available.
     */
    override val altitude: Double?,
    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    override val speed: Double,
    /**
     * The current accuracy of the measuring device in meters if available.
     *
     * FIXME: Write `null` when `Location.hasAccuracy()` is false. The transfer file format might need adjustment for that.
     */
    override val accuracy: Double?,
    /**
     * The current vertical accuracy of the measuring device in meters if available.
     */
    override val verticalAccuracy: Double?,
    /**
     * `True` if this location is considered "clean" by the provided [de.cyface.persistence.LocationCleaningStrategy].
     *
     * This is not persisted, as the validity can be different depending on the strategy implementation.
     */
    @Ignore
    override var isValid: Boolean? = null,
    /**
     * The device-unique id of the measurement this data point belongs to.
     *
     * This foreign key points to [Measurement.uid] and is indexed to avoid full table scan on parent update.
     */
    @field:ColumnInfo(index = true)
    val measurementId: Long
) : ParcelableGeoLocation(timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy) {

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
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(
        timestamp: Long, lat: Double, lon: Double, altitude: Double?,
        speed: Double, accuracy: Double?, verticalAccuracy: Double?, measurementId: Long
    ) : this(timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy, null, measurementId)

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param location The cached [ParcelableGeoLocation] to create the [GeoLocation] from.
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(location: ParcelableGeoLocation, measurementId: Long) : this(
        location.timestamp, location.lat, location.lon, location.altitude,
        location.speed, location.accuracy, location.verticalAccuracy, measurementId
    )

    // is timestamp checked?
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as GeoLocation
        return (that.lat.compareTo(lat) == 0 && that.lon.compareTo(lon) == 0
                && that.speed.compareTo(speed) == 0 && that.accuracy == accuracy
                && measurementId == that.measurementId && altitude == that.altitude
                && verticalAccuracy == that.verticalAccuracy && isValid == that.isValid)
    }

    // FIXME: I changed the hashCode to `uid` only like in all other @Entity classes,
    // as the constructor which has `uid` set to `0` is usually only used to insert an entry into the db.
    // The only issue which could occur would be if we want to insert a set or new points into the database,
    // as the set would then just have one of these points as entry, as `uid` is `0`.
    // But for Measurement, GeoLocation, Event and Pressure we usually insert one point at a time.
    // DEPRECATED: To ease migration with `main` branch, we keep the models similar to `GeoLocation` but might want to change this
    // DEPRECATED: in future. https://github.com/cyface-de/android-backend/pull/258#discussion_r1070618174
    override fun hashCode(): Int {
        return uid.hashCode()
        // FIXME: In `Track` v1 `ParcelableGeoLocation` is used, which has the following hashCode:
        // GeoLocation.hashCode(): lat, lon, timestamp, speed, accuracy
        // ParcelableGeoLocation.hashCode: geolocation.hashCode + isValid
        // In this class it should probably be: uid
        // In the child class GeoLocation: super.timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy
        // or simply: timestamp, as we only expect one gnss point per timestamp
        //return Objects.hash(lat, lon, altitude, speed, accuracy, verticalAccuracy, measurementId)
    }
}