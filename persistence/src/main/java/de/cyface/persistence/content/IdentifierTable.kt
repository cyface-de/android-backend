/*
 * Copyright 2019-2023 Cyface GmbH
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

import android.content.Context
import android.net.Uri

/**
 * This class represents the table containing the measurement-independent identifiers stored on this device.
 *
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 3.0.0
 * @property context The `Context` required to load the old device id from the `SharedPreferences` when
 * upgrading from [de.cyface.persistence.Database] 8.
 */
class IdentifierTable
internal constructor(
    override val databaseTableColumns: Array<String> = arrayOf(
        BaseColumns.ID, COLUMN_DEVICE_ID
    ),
    private val context: Context
) : AbstractCyfaceTable(
    URI_PATH
) {
    companion object {
        /**
         * The path segment in the table URI identifying the identifier table.
         */
        const val URI_PATH = "Identifier"

        /**
         * A String value which contains an identifier for this device.
         */
        const val COLUMN_DEVICE_ID = "deviceId"

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
}