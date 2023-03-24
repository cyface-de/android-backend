/*
 * Copyright 2018-2023 Cyface GmbH
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
 * Table for storing [de.cyface.persistence.model.Event]s.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.0.0
 */
class EventTable : AbstractCyfaceTable(URI_PATH) {
    companion object {
        /**
         * The path segment in the table URI identifying the [EventTable].
         */
        const val URI_PATH = "Event"

        /**
         * Column name for the column storing the [de.cyface.persistence.model.EventType].
         */
        const val COLUMN_TYPE = "type"

        /**
         * Column name for the column storing the optional value required for some [COLUMN_TYPE]s.
         */
        const val COLUMN_VALUE = "value"

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
            COLUMN_TYPE,
            BaseColumns.MEASUREMENT_ID,
            COLUMN_VALUE
        )
}