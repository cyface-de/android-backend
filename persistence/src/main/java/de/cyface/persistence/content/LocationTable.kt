/*
 * Copyright 2017 Cyface GmbH
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

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import de.cyface.persistence.Constants

/**
 * Table for storing [GeoLocation] measuring points. The data in this table is intended for storage prior to
 * processing it by either transfer to a server or export to some external file or device.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.4.4
 * @since 1.0.0
 */
class LocationTable
/**
 * Provides a completely initialized object as a representation of a table containing geo locations in the database.
 */
protected constructor(
    /**
     * An array containing all the column names used by this table.
     */
    override val databaseTableColumns: Array<String> = arrayOf(
        BaseColumns._ID, COLUMN_GEOLOCATION_TIME, COLUMN_LAT, COLUMN_LON,
        COLUMN_SPEED, COLUMN_ACCURACY, COLUMN_MEASUREMENT_FK
    )
) : AbstractCyfaceTable(URI_PATH) {
    /**
     * Don't forget to update the [MeasurementProviderHelper]'s `DATABASE_VERSION` if you upgrade this table.
     *
     * The Upgrade is automatically executed in a transaction, do not wrap the code in another transaction!
     *
     * This upgrades are called incrementally by [MeasurementProviderHelper.onUpgrade].
     *
     * Remaining documentation: [CyfaceTable.onUpgrade]
     */
    fun onUpgrade(database: SQLiteDatabase, fromVersion: Int, toVersion: Int) {
        when (fromVersion) {
            8 -> {
                Log.d(Constants.TAG, "Upgrading geoLocation table from V8")
                migrateDatabaseFromV8(database)
            }
        }
    }

    /**
     * Renames table, updates the table structure and copies the data.
     *
     * @param database The `SQLiteDatabase` to upgrade
     */
    private fun migrateDatabaseFromV8(database: SQLiteDatabase) {
        // To drop columns we need to copy the table. We anyway renamed the table to locations.
        database.execSQL("ALTER TABLE gps_points RENAME TO _locations_old;")

        // To drop columns "is_synced" we need to create a new table
        database.execSQL(
            "CREATE TABLE locations (_id INTEGER PRIMARY KEY AUTOINCREMENT, gps_time INTEGER NOT NULL, "
                    + "lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL, accuracy INTEGER NOT NULL, "
                    + "measurement_fk INTEGER NOT NULL);"
        )
        // and insert the old data accordingly. This is anyway cleaner (no defaults)
        // We ignore the value as we upload to a new API.
        database.execSQL(
            "INSERT INTO locations " + "(_id,gps_time,lat,lon,speed,accuracy,measurement_fk) "
                    + "SELECT _id,gps_time,lat,lon,speed,accuracy,measurement_fk " + "FROM _locations_old"
        )

        // Remove temp table
        database.execSQL("DROP TABLE _locations_old;")
    }

    companion object {
        /**
         * The path segment in the table URI identifying the [LocationTable].
         */
        const val URI_PATH = "locations"

        /**
         * Column name for the column storing the [GeoLocation] timestamp.
         */
        const val COLUMN_GEOLOCATION_TIME = "gps_time"

        /**
         * Column name for the column storing the [GeoLocation] latitude.
         */
        const val COLUMN_LAT = "lat"

        /**
         * Column name for the column storing the [GeoLocation] longitude.
         */
        const val COLUMN_LON = "lon"

        /**
         * Column name for the column storing the [GeoLocation] speed in meters per second.
         */
        const val COLUMN_SPEED = "speed"

        /**
         * Column name for the column storing the [GeoLocation] accuracy in centimeters.
         */
        const val COLUMN_ACCURACY = "accuracy"

        /**
         * Column name for the column storing the foreign key referencing the [Measurement] for this
         * [GeoLocation].
         */
        const val COLUMN_MEASUREMENT_FK = "measurement_fk"

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