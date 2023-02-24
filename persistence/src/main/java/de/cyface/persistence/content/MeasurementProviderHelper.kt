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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils
import de.cyface.persistence.Database

/**
 * This class is the part of the `ContentProvider` where the hard part takes place. It distributes queries
 * from the [MeasurementProvider] to the correct tables.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 */
internal class DatabaseHelper(context: Context) {
    /**
     * The table containing all the measurements, without the corresponding data. Data is stored in one table per type.
     */
    //private val measurementTable: MeasurementTable

    /**
     * The table to store all the geo locations captured on the device.
     */
    //private val geoLocationsTable: GeoLocationsTable

    /**
     * The table to store the device identifier to make sure its reset when the database, and thus the next measurement
     * id count is reset, too.
     */
    //private val identifierTable: IdentifierTable

    /**
     * The table to store the [Event]s on the device.
     */
    //private val eventTable: EventTable

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param context The Android context to use to access the Android System via.
     */
    init {
        //measurementTable = MeasurementTable()
        //geoLocationsTable = GeoLocationsTable()
        //identifierTable = IdentifierTable(context)
        //eventTable = EventTable()
    }

    /**
     * The onCreate method is called when the app is freshly installed (i.e. there is no data yet on the phone)
     * Update this (in DatabaseHelper()) if the database structure changes
     *
     * @param db the database in which the data shall be stored
     */
    fun onCreate(db: SQLiteDatabase?) {
        //identifierTable.onCreate(db)
        //measurementTable.onCreate(db)
        //geoLocationsTable.onCreate(db)
        //eventTable.onCreate(db)
    }

    /**
     * The onUpgrade method is called when the app is upgraded and the DATABASE_VERSION changed.
     *
     *
     * This method is not called incrementally by the system which is why this method implements this.
     *
     *
     * This method is automatically executed in a transaction, do not wrap the code in another transaction!
     *
     * @param database the database which shall be upgraded
     * @param oldVersion the database version the app was in before the upgrade
     * @param newVersion the database version of the new, upgraded app which shall be reached
     * /
    fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Validate.isTrue(oldVersion == 8 || oldVersion >= 11, "Unsupported versions")

    // Upgrade incrementally to reduce the amount of migration code required
    for (fromVersion in oldVersion until newVersion) {
    val toVersion = fromVersion + 1
    Log.w(Constants.TAG, "Upgrading database from version $fromVersion to $toVersion")
    when (fromVersion) {
    8 -> {
    // Upgrade from V8 which was the last version in SDK V2.
    // We don't support a data preserving upgrade for sensor data stored in the database
    Log.w(Constants.TAG, "Dropping sensor data from database")

    // The following tables and table names are deprecated, thus, hard-coded
    // database.execSQL("DELETE FROM sqlite_sequence;");
    database.execSQL("DELETE FROM sample_points;")
    database.execSQL("DROP TABLE sample_points;")
    database.execSQL("DELETE FROM rotation_points;")
    database.execSQL("DROP TABLE rotation_points;")
    database.execSQL("DELETE FROM magnetic_value_points;")
    database.execSQL("DROP TABLE magnetic_value_points;")
    }
    }

    // Incremental upgrades for existing tables
    measurementTable.onUpgrade(database, fromVersion, toVersion)
    geoLocationsTable.onUpgrade(database, fromVersion, toVersion)
    identifierTable.onUpgrade(database, fromVersion, toVersion)
    eventTable.onUpgrade(database, fromVersion, toVersion)
    }
    }*/

    /**
     * Deletes one or multiple rows (depending on the format of the provided URI) from the database. If you delete a
     * [de.cyface.persistence.model.Measurement] all data linked via `ForeignKey` is cascadingly deleted as well.
     *
     * @param database FIXME
     * @param uri The URI specifying the table to delete from. If this ends with a single numeric identifier that row is
     * deleted otherwise multiple rows might be deleted depending on the `selection` and
     * `selectionArgs`.
     * @param selection The part of an SQL where statement behind the where. You can use '?' as a placeholder to secure
     * yourself from SQL injections.
     * @param selectionArgs The arguments to place inside the '?' placeholder from `selection`.
     * @return The number of rows deleted.
     */
    fun deleteRow(
        database: Database,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        val pathSegments = uri.pathSegments
        val table: CyfaceMeasurementTable = matchTable(uri)
        var ret = 0
        database.beginTransaction()
        return try {
            if (pathSegments.size == 2) {
                val rowIdentifier = pathSegments[1]
                when (pathSegments[0]) {
                    MeasurementTable.URI_PATH -> {
                        // Measurement requires to also delete all dependent entries and then call table.deleteRow
                        // All other database entries just call table.deleteRow directly.
                        ret += deleteDataForMeasurement(database, rowIdentifier.toLong())
                        // Add the id specified by the URI to implement expected behaviour of a content resolver, where
                        // the last element of the path is the identifier of the element requested. This is only
                        // necessary for single row deletions.
                        val adaptedSelection = (BaseColumns._ID + "=" + rowIdentifier
                                + if (selection == null) "" else " AND $selection")
                        ret += table.deleteRow(
                            getWritableDatabase(),
                            adaptedSelection,
                            selectionArgs
                        )
                        database.setTransactionSuccessful()
                        ret
                    }
                    GeoLocationsTable.URI_PATH, PressureTABLE.URI_PATH, EventTable.URI_PATH -> {
                        val adaptedSelection = (BaseColumns._ID + "=" + rowIdentifier
                                + if (selection == null) "" else " AND $selection")
                        ret += table.deleteRow(
                            getWritableDatabase(),
                            adaptedSelection,
                            selectionArgs
                        )
                        database.setTransactionSuccessful()
                        ret
                    }
                    else -> throw IllegalStateException("Unknown table identified by content provider URI: $uri")
                }
            } else if (pathSegments.size == 1) {
                when (pathSegments[0]) {
                    EventTable.URI_PATH -> {
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs)
                        database.setTransactionSuccessful()
                        ret
                    }
                    IdentifierTable.URI_PATH -> {
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs)
                        database.setTransactionSuccessful()
                        ret
                    }
                    MeasurementTable.URI_PATH -> {
                        query(
                            uri, arrayOf(BaseColumns._ID),
                            selection,
                            selectionArgs, null
                        ).use { selectedMeasurementsCursor ->
                            while (selectedMeasurementsCursor.moveToNext()) {
                                ret += deleteDataForMeasurement(
                                    database, selectedMeasurementsCursor
                                        .getLong(
                                            selectedMeasurementsCursor.getColumnIndex(
                                                BaseColumns._ID
                                            )
                                        )
                                )
                            }
                        }
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs)
                        database.setTransactionSuccessful()
                        ret
                    }
                    GeoLocationsTable.URI_PATH, PressureTABLE.URI_PATH, EventTable.URI_PATH -> {
                        ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs)
                        database.setTransactionSuccessful()
                        ret
                    }
                    else -> throw IllegalStateException(
                        "Unable to delete data from table corresponding to URI " + uri
                                + ". There seems to be no such table."
                    )
                }
            } else {
                throw IllegalStateException("Invalid path in content provider URI: $uri")
            }
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Cascadingly deletes all data for a single [Measurement] from the database. This only includes
     * [GeoLocation]s and [Event]s but not the [Point3dFile]s as they are not stored in database.
     *
     * @param database The database object to delete from.
     * @param measurementIdentifier The device wide unique identifier of the measurement to delete.
     * @return The number of rows deleted.
     */
    private fun deleteDataForMeasurement(
        database: SQLiteDatabase,
        measurementIdentifier: Long
    ): Int {
        val identifierAsArgs = arrayOf(java.lang.Long.valueOf(measurementIdentifier).toString())
        var ret = 0
        ret += geoLocationsTable.deleteRow(
            database,
            GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
            identifierAsArgs
        )
        ret += eventTable.deleteRow(
            database,
            EventTable.COLUMN_MEASUREMENT_FK + "=?",
            identifierAsArgs
        )
        return ret
    }

    /**
     * Inserts a single row into a table.
     *
     * @param uri The table to insert the row into.
     * @param values The values to insert in the new table row.
     * @return The identifier of the new row.
     */
    fun insertRow(uri: Uri, values: ContentValues): Long {
        val table: CyfaceMeasurementTable = matchTable(uri)
        return table.insertRow(getWritableDatabase(), values)
    }

    /**
     * Inserts a list of `ContentValues` as new rows into a table.
     *
     * @param uri The table to insert the new rows into.
     * @param values The values to insert.
     * @return An array of identifiers for the newly created table rows.
     */
    fun bulkInsert(uri: Uri, values: List<ContentValues?>): LongArray {
        val table: CyfaceMeasurementTable = matchTable(uri)
        return table.insertBatch(getWritableDatabase(), values)
    }

    /**
     * Sends a query to the database and provides the result of that query.
     *
     * @param uri The table to send the query to.
     * @param projection The projection is the part behind the SQL select statement without the `SELECT`.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     * statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in `selection`. The amount of
     * '?' and arguments needs to match.
     * @param sortOrder This is either `ASC` for ascending or `DESC` for descending.
     * @return A `Cursor` over the resulting rows. Do not forget to close this cursor after you finished
     * reading from it. Preferably use `finally` to close the cursor to avoid memory leaks.
     */
    fun query(
        uri: Uri, projection: Array<String?>?, selection: String?, selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor {
        val table: CyfaceMeasurementTable = matchTable(uri)
        return if (uri.pathSegments.size == 1) {
            table.query(getReadableDatabase(), projection, selection, selectionArgs, sortOrder)
        } else if (uri.pathSegments.size == 2) {
            val adaptedSelection = (BaseColumns._ID + "=" + uri.lastPathSegment
                    + if (selection == null) "" else " AND $selection")
            table.query(
                getReadableDatabase(),
                projection,
                adaptedSelection,
                selectionArgs,
                sortOrder
            )
        } else {
            throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Updates a row or rows in the database with new values.
     *
     * @param uri The table to update rows in.
     * @param values The new values to insert.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     * statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in `selection`. The amount of
     * '?' and arguments needs to match.
     * @return The number of updated rows.
     */
    fun update(
        uri: Uri, values: ContentValues, selection: String,
        selectionArgs: Array<String?>?
    ): Int {
        val table: CyfaceMeasurementTable = matchTable(uri)
        return if (uri.pathSegments.size == 1) {
            table.update(getWritableDatabase(), values, selection, selectionArgs)
        } else if (uri.pathSegments.size == 2) {
            val id = uri.lastPathSegment
            val adaptedSelection = (BaseColumns._ID + "=" + id
                    + if (TextUtils.isEmpty(selection)) "" else " AND $selection")
            table.update(getWritableDatabase(), values, adaptedSelection, selectionArgs)
        } else {
            throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Provides the MIME type for the provided URI. This is either `vnd.android.cursor.item/de.cyface.data`
     * for an URI identifying a single item, or `vnd.android.cursor.dir/de.cyface.data` for an URI
     * identifying a collection of items.
     *
     * @param uri The URI to provide the MIME type for.
     * @return The MIME type corresponding to that URI.
     */
    fun getType(uri: Uri): String {
        return when (uri.pathSegments.size) {
            1 -> "vnd.android.cursor.item/de.cyface.data"
            2 -> "vnd.android.cursor.dir/de.cyface.data"
            else throw IllegalStateException("Invalid content provider URI: $uri")
        }
    }

    /**
     * Matches the provided `uri` with the corresponding table and returns that table.
     *
     * @param uri A content provider uri referring to a table within this content provider. if this `uri` is
     * not valid an `IllegalArgumentException` is thrown.
     * @return The table corresponding to the provided `uri`.
     */
    private fun matchTable(uri: Uri): CyfaceMeasurementTable {
        val firstPathSegment = uri.pathSegments[0]
        return when (firstPathSegment) {
            MeasurementTable.URI_PATH -> measurementTable
            GeoLocationsTable.URI_PATH -> geoLocationsTable
            IdentifierTable.URI_PATH -> identifierTable
            EventTable.URI_PATH -> eventTable
            else -> throw IllegalStateException("Unknown table with URI: $uri")
        }
    }

    companion object {
        /**
         * Name of the database used by the content provider to store data.
         */
        //private const val DATABASE_NAME = "measures"

        /**
         * Increase the DATABASE_VERSION if the database structure changes with a new update
         * but don't forget to adjust onCreate and onUpgrade accordingly for the new structure and incremental upgrade
         *
         * **ATTENTION**: DO NOT INCREASE THIS VERSION, to keep this branch upgradable to V17/V18.
         * There is already migration code to 17 (accuracy [m]) and 18 (migrate to Room and merge v6.1 database).
         */
        //private const val DATABASE_VERSION = 16 // (!) Don't increase this version on this branch!
    }
}