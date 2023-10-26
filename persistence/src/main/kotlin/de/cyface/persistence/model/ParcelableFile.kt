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
import de.cyface.persistence.content.MeasurementTable
import de.cyface.protos.model.File.FileType
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class represents a file [DataPoint], usually captured by a camera.
 *
 * An instance of this class represents a data point captured and cached but not yet persisted. Such a
 * [File] requires the measurement id to be set.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
open class ParcelableFile : DataPoint {
    /**
     * The status of the file.
     *
     * The status allows us to persist which files of a [MeasurementTable] entry are already
     * `synced` with the server.
     */
    open val status: FileStatus

    /**
     * The type of the file, e.g. JPG (compressed image).
     */
    open val type: FileType

    /**
     * The file format version of this data point, e.g. 1.
     *
     * This version allows us to change the way we store a file type (e.g. JPG) over time.
     * One example for this is when we change the JPG Exif header inside the file.
     */
    open val fileFormatVersion: Short

    /**
     * The size of the file represented by this data point in bytes.
     *
     * This way we can calculate the total size of the measurement without accessing all files.
     * An example usage for this is to calculate the upload progress when we deleted some files
     * which where already uploaded. Another example is to skip measurement which are too big.
     */
    open val size: Long

    /**
     * The path to the file represented by this data point.
     *
     * The path is by default relative to the app-specific external storage directory defined
     * by Android: https://developer.android.com/training/data-storage/app-specific
     *
     * To also support the possibility of storing files in another place, the format of the
     * paths allows to separate relative paths from absolute paths:
     * - relative: `./subFolder/file.extension`
     * - absolute: `/rootFolder/subFolder/file.extension`
     */
    open val path: Path

    /**
     * The latitude of the last known location, e.g. 51.123, or null if unknown.
     *
     * It allows to show the captured images on a map, e.g. to delete images before uploading.
     */
    open val lat: Double?

    /**
     * The longitude of the last known location, e.g. 13.123, or null if unknown.
     *
     * It allows to show the captured images on a map, e.g. to delete images before uploading.
     */
    open val lon: Double?

    /**
     * Column name for the column storing the Unix timestamp in milliseconds of the last known location,
     * or null if unknown.
     *
     * It allows to identify when the last known location is from too long. Additionally, it's the
     * link to the location data, e.g. to get additional data like the accuracy.
     */
    open val locationTimestamp: Long?

    /**
     * Creates a new completely initialized instance of this class.
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
        locationTimestamp: Long?
    ) : super(timestamp) {
        require(timestamp >= 0L) { "Illegal argument: timestamp was less than 0L!" }
        require(type != FileType.FILE_TYPE_UNSPECIFIED) { "Unsupported type $type." }
        require(fileFormatVersion >= 1) { "Unsupported format version $fileFormatVersion" }
        require(size > 0) { "Unsupported size: $size bytes" }
        if (lat != null) {
            require(!(lat < -90.0 || lat > 90.0)) {
                "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was $lat."
            }
        }
        if (lon != null) {
            require(!(lon < -180.0 || lon > 180.0)) {
                "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was $lon."
            }
        }
        if (locationTimestamp != null) {
            require(locationTimestamp >= 0L) { "Illegal argument: locationTimestamp was less than 0L!" }
        }
        this.status = status
        this.type = type
        this.fileFormatVersion = fileFormatVersion
        this.size = size
        this.path = path
        this.lat = lat
        this.lon = lon
        this.locationTimestamp = locationTimestamp
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
        status = FileStatus.valueOf(`in`.readString()!!)
        type = FileType.valueOf(`in`.readString()!!)
        fileFormatVersion = `in`.readInt().toShort()
        size = `in`.readLong()
        path = Paths.get(`in`.readString()) // supported < API 26 with NIO enabled desugaring
        lat = `in`.readValue(Double::class.java.classLoader) as? Double
        lon = `in`.readValue(Double::class.java.classLoader) as? Double
        locationTimestamp = `in`.readValue(Long::class.java.classLoader) as? Long
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(status.name)
        dest.writeString(type.name)
        dest.writeInt(fileFormatVersion.toInt())
        dest.writeLong(size)
        dest.writeString(path.toString())
        dest.writeValue(lat)   // Handle nullable Double
        dest.writeValue(lon)   // Handle nullable Double
        dest.writeValue(locationTimestamp)   // Handle nullable Long
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ParcelableFile

        if (status != other.status) return false
        if (type != other.type) return false
        if (fileFormatVersion != other.fileFormatVersion) return false
        if (size != other.size) return false
        if (path != other.path) return false
        if (lat != other.lat) return false
        if (lon != other.lon) return false
        if (locationTimestamp != other.locationTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @JvmField
        val CREATOR: Creator<ParcelableFile?> = object : Creator<ParcelableFile?> {
            override fun createFromParcel(`in`: Parcel): ParcelableFile {
                return ParcelableFile(`in`)
            }

            override fun newArray(size: Int): Array<ParcelableFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}