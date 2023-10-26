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
import de.cyface.protos.model.File.FileType
import java.nio.file.Path

/**
 * An `@Entity` which represents a persisted [ParcelableFile], usually captured by a camera.
 *
 * An instance of this class represents one row in a database table containing the file reference.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 * @property id The system-wide unique identifier of this entity, generated by the data store.
 * It's `0`, which equals `null` in the non-nullable column `Long` when the entry is not yet persisted.
 * @property measurementId The device-unique id of the measurement this data point belongs to.
 * This foreign key points to [Measurement.id] and is indexed to avoid full table scan on parent update.
 */
@Entity(
    // Keep the table schema in sync with `ContentProvider`'s [FileTable]
    foreignKeys = [ForeignKey(
        entity = Measurement::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("measurementId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class File(
    @ColumnInfo(name = "_id") // The CursorAdapter requires a column with the name `_id`
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    override val timestamp: Long,
    override val status: FileStatus,
    override val type: FileType,
    override val fileFormatVersion: Short,
    override val size: Long,
    override val path: Path,
    override val lat: Double?,
    override val lon: Double?,
    override val locationTimestamp: Long?,
    @ColumnInfo(index = true) val measurementId: Long
) : ParcelableFile(timestamp, status, type, fileFormatVersion, size, path, lat, lon, locationTimestamp) {

    /**
     * Creates a new instance of this class which was not yet persisted and has [id] set to `0`.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @param status The status of the file.
     * @param type The type of the file, e.g. JPG (compressed image).
     * @param fileFormatVersion The file format version of this data point, e.g. 1.
     * @param size The size of the file represented by this data point in bytes.
     * @param path The path to the file represented by this data point.
     * The path is by default relative to the app-specific external storage directory defined
     * by Android: https://developer.android.com/training/data-storage/app-specific
     * @param lat The latitude of the last known location, e.g. 51.123, or null if unknown.
     * @param lon The longitude of the last known location, e.g. 13.123, or null if unknown.
     * @param locationTimestamp The timestamp of the last known location, or null if unknown.
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    constructor(
        timestamp: Long,
        status: FileStatus,
        type: FileType,
        fileFormatVersion: Short,
        size: Long,
        path: Path,
        lat: Double?,
        lon: Double?,
        locationTimestamp: Long?,
        measurementId: Long
    ) : this(
        0,
        timestamp,
        status,
        type,
        fileFormatVersion,
        size,
        path,
        lat,
        lon,
        locationTimestamp,
        measurementId
    )

    init {
        require(timestamp >= 0L) { "Illegal argument: timestamp was less than 0L!" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as File

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (status != other.status) return false
        if (type != other.type) return false
        if (fileFormatVersion != other.fileFormatVersion) return false
        if (size != other.size) return false
        if (path != other.path) return false
        if (lat != other.lat) return false
        if (lon != other.lon) return false
        if (locationTimestamp != other.locationTimestamp) return false
        if (measurementId != other.measurementId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}