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

/**
 * This class contains the default column names or columns used in most tables.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
class BaseColumns {
    companion object {
        /**
         * The unique identifier of a row in a database table.
         *
         * SQLite Type: INTEGER (long)
         */
        const val ID = "id"

        /**
         * Column name for the Unix timestamp in milliseconds of the data represented by the table row.
         */
        const val TIMESTAMP = "timestamp"

        /**
         * Column name for the column storing the foreign key referencing the [de.cyface.persistence.model.Measurement]
         * for the data represented by the table row.
         */
        const val MEASUREMENT_ID = "measurementId"
    }
}