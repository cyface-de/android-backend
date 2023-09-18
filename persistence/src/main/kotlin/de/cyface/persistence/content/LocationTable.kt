/*
 * Copyright 2017-2023 Cyface GmbH
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
 * Table for storing [de.cyface.persistence.model.GeoLocation]s. The data in this table is intended for
 * storage prior to processing it by either transfer to a server or export to some external file or device.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 */
class LocationTable : AbstractCyfaceTable(URI_PATH) {
    companion object {
        /**
         * The path segment in the table URI identifying the [LocationTable].
         */
        const val URI_PATH = "Location"

        /**
         * Column name for the column storing the latitude.
         */
        const val COLUMN_LAT = "lat"

        /**
         * Column name for the column storing the longitude.
         */
        const val COLUMN_LON = "lon"

        /**
         * Column name for the column storing the altitude from the GNSS data in meters.
         */
        const val COLUMN_ALTITUDE = "altitude"

        /**
         * Column name for the column storing the speed in meters per second.
         */
        const val COLUMN_SPEED = "speed"

        /**
         * Column name for the column storing the horizontal accuracy in meters.
         */
        const val COLUMN_ACCURACY = "accuracy"

        /**
         * Column name for the column storing the vertical accuracy in meters.
         */
        const val COLUMN_VERTICAL_ACCURACY = "verticalAccuracy"

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
            BaseColumns.ID, BaseColumns.TIMESTAMP, COLUMN_LAT, COLUMN_LON, COLUMN_ALTITUDE,
            COLUMN_SPEED, COLUMN_ACCURACY, COLUMN_VERTICAL_ACCURACY, BaseColumns.MEASUREMENT_ID
        )
}