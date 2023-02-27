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
class PressureTable : AbstractCyfaceTable(URI_PATH) {
    companion object {
        /**
         * The path segment in the table URI identifying the [PressureTable].
         */
        const val URI_PATH = "Pressure"

        /**
         * Column name for the column storing the atmospheric pressure in hPa.
         */
        const val COLUMN_PRESSURE = "pressure"

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
            COLUMN_PRESSURE,
            BaseColumns.MEASUREMENT_ID
        )
}