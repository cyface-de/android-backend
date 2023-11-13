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
package de.cyface.persistence.content

import android.net.Uri

/**
 * Table for storing [de.cyface.persistence.model.File]s. The data in this table is intended for
 * storage prior to processing it by either transfer to a server or export to some external file or device.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.10.0
 */
class FileTable : AbstractCyfaceTable(URI_PATH) {
    companion object {
        /**
         * The path segment in the table URI identifying the [FileTable].
         */
        const val URI_PATH = "File"

        /**
         * Column name for the status of the file.
         *
         * The status allows us to persist which files of a [MeasurementTable] entry are already
         * `synced` with the server.
         */
        const val COLUMN_STATUS = "status"

        /**
         * Column name for the column storing the type of the file, e.g. JPG (compressed image).
         */
        const val COLUMN_TYPE = "type"

        /**
         * Column name for the column storing the file format version.
         *
         * This version allows us to change the way we store a file type (e.g. JPG) over time.
         * One example for this is when we change the JPG Exif header inside the file.
         */
        const val COLUMN_FILE_FORMAT_VERSION = "fileFormatVersion"

        /**
         * Column name for the column storing the size of the file in bytes.
         *
         * This way we can calculate the total size of the measurement without accessing all files.
         * An example usage for this is to calculate the upload progress when we deleted some files
         * which where already uploaded. Another example is to skip measurement which are too big.
         */
        const val COLUMN_SIZE = "size"

        /**
         * Column name for the column storing the path to the actual file on the disk.
         *
         * The path is by default relative to the app-specific external storage directory defined
         * by Android: https://developer.android.com/training/data-storage/app-specific
         *
         * To also support the possibility of storing files in another place, the format of the
         * paths allows to separate relative paths from absolute paths:
         * - relative: `./subFolder/file.extension`
         * - absolute: `/rootFolder/subFolder/file.extension`
         */
        const val COLUMN_PATH = "path"

        /**
         * Column name for the column storing the latitude of the last known location.
         *
         * It allows to show the captured images on a map, e.g. to delete images before uploading.
         */
        const val COLUMN_LAT = "lat"

        /**
         * Column name for the column storing the longitude of the last known location.
         *
         * It allows to show the captured images on a map, e.g. to delete images before uploading.
         */
        const val COLUMN_LON = "lon"

        /**
         * Column name for the column storing the Unix timestamp in milliseconds of the last known location.
         *
         * It allows to identify when the last known location was recorded too long ago. Additionally, it's the
         * link to the location data, e.g. to get additional data like the accuracy.
         */
        const val COLUMN_LOCATION_TIMESTAMP = "location_timestamp"

        /**
         * Returns the URI which identifies the table represented by this class.
         *
         * It's important to provide the authority string as parameter because depending on from where
         * you call this you want to access your own authorities database.
         *
         * @param authority The authority to access the database
         */
        fun getUri(authority: String): Uri {
            return Uri.Builder().scheme("content").authority(authority).appendPath(URI_PATH).build()
        }
    }

    override val databaseTableColumns: Array<String>
        get() = arrayOf(
            BaseColumns.ID,
            BaseColumns.TIMESTAMP,
            COLUMN_STATUS,
            COLUMN_TYPE,
            COLUMN_FILE_FORMAT_VERSION,
            COLUMN_SIZE,
            COLUMN_PATH,
            COLUMN_LAT,
            COLUMN_LON,
            COLUMN_LOCATION_TIMESTAMP,
            BaseColumns.MEASUREMENT_ID
        )
}