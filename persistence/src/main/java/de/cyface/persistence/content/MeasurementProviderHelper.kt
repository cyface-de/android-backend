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
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils
import androidx.sqlite.db.SupportSQLiteDatabase
import de.cyface.persistence.Database

/**
 * This class is the part of the `ContentProvider` where the hard part takes place. It distributes queries
 * from the [MeasurementProvider] to the correct tables.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 * @property database The database to be used for data access.
 */
internal class MeasurementProviderHelper(
    /**
     * The Android context to use to access the Android System via.
     */
    context: Context,
    val database: Database
) {
    /**
     * The table containing all the measurements, without the corresponding data. Data is stored in one table per type.
     */
    private val measurementTable: MeasurementTable = MeasurementTable()

    /**
     * The table to store all the geo locations captured on the device.
     */
    private val geoLocationTable: GeoLocationTable = GeoLocationTable()

    /**
     * The table to store all the pressures captured on the device.
     */
    private val pressureTable: PressureTable = PressureTable()

    /**
     * The table to store the device identifier to make sure its reset when the database, and thus the next measurement
     * id count is reset, too.
     */
    private val identifierTable: IdentifierTable

    /**
     * The table to store the events on the device.
     */
    private val eventTable: EventTable = EventTable()

    init {
        identifierTable = IdentifierTable(context)
    }

    /**
     * Deletes one or multiple rows (depending on the format of the provided URI) from the database. If you delete a
     * [de.cyface.persistence.model.Measurement] all data linked via `ForeignKey` is cascadingly deleted as well.
     *
     * @param uri The URI specifying the table to delete from. If this ends with a single numeric identifier that row is
     * deleted otherwise multiple rows might be deleted depending on the `selection` and
     * `selectionArgs`.
     * @param selection The part of an SQL where statement behind the where. You can use '?' as a placeholder to secure
     * yourself from SQL injections.
     * @param selectionArgs The arguments to place inside the '?' placeholder from `selection`.
     * @return The number of rows deleted.
     */
    fun deleteRow(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val pathSegments = uri.pathSegments
        val database = getWritableDatabase()

        val table: CyfaceTable = matchTable(uri)

        var ret = 0
        database.beginTransaction()
        return try {
            when (pathSegments.size) {
                2 -> {
                    val rowIdentifier = pathSegments[1]
                    when (pathSegments[0]) {
                        // FIXME: ensure the measurement-related data is deleted automatically cascadingly (write/execute test)
                        MeasurementTable.URI_PATH, GeoLocationTable.URI_PATH, PressureTable.URI_PATH, EventTable.URI_PATH -> {
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
                }
                1 -> {
                    when (pathSegments[0]) {
                        // FIXME: ensure the measurement-related data is deleted automatically cascadingly (write/execute test)
                        MeasurementTable.URI_PATH, GeoLocationTable.URI_PATH, PressureTable.URI_PATH, EventTable.URI_PATH, IdentifierTable.URI_PATH -> {
                            ret += table.deleteRow(getWritableDatabase(), selection, selectionArgs)
                            database.setTransactionSuccessful()
                            ret
                        }
                        else -> throw IllegalStateException(
                            "Unable to delete data from table corresponding to URI $uri. There seems to be no such table."
                        )
                    }
                }
                else -> {
                    throw IllegalStateException("Invalid path in content provider URI: $uri")
                }
            }
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Create/Open a database that can be used for writing.
     *
     * @see [androidx.sqlite.db.SupportSQLiteOpenHelper.getWritableDatabase]
     */
    private fun getWritableDatabase(): SupportSQLiteDatabase {
        return database.openHelper.writableDatabase
    }

    /**
     * Create/Open a database that can be used for reading.
     *
     * @see [androidx.sqlite.db.SupportSQLiteOpenHelper.getReadableDatabase]
     */
    private fun getReadableDatabase(): SupportSQLiteDatabase {
        return database.openHelper.readableDatabase
    }

    /**
     * Inserts a single row into a table.
     *
     * @param uri The table to insert the row into.
     * @param values The values to insert in the new table row.
     * @return The identifier of the new row.
     */
    fun insertRow(uri: Uri, values: ContentValues): Long {
        val table: CyfaceTable = matchTable(uri)
        return table.insertRow(getWritableDatabase(), values)
    }

    /**
     * Inserts a list of `ContentValues` as new rows into a table.
     *
     * @param uri The table to insert the new rows into.
     * @param values The values to insert.
     * @return An array of identifiers for the newly created table rows.
     */
    fun bulkInsert(uri: Uri, values: List<ContentValues>): LongArray {
        val table: CyfaceTable = matchTable(uri)
        // FIXME: Why is insertBatch used here when we state everywhere that bulkInsert is 80x faster?
        // The only old task I found is: https://cyface.atlassian.net/browse/MOV-248
        // But nothing about bulkInsert. I guess we leave it like this as this is working
        // writableDatabase.bulkInsert(uri, Arrays.asList(*values)).length
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
        uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val table: CyfaceTable = matchTable(uri)
        return when (uri.pathSegments.size) {
            1 -> table.query(getReadableDatabase(), projection, selection, selectionArgs, sortOrder)
            2 -> {
                val adaptedSelection = (BaseColumns._ID + "=" + uri.lastPathSegment
                        + if (selection == null) "" else " AND $selection")
                table.query(
                    getReadableDatabase(),
                    projection,
                    adaptedSelection,
                    selectionArgs,
                    sortOrder
                )
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
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
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val table: CyfaceTable = matchTable(uri)
        return when (uri.pathSegments.size) {
            1 -> table.update(getWritableDatabase(), values, selection, selectionArgs)
            2 -> {
                val id = uri.lastPathSegment
                val adaptedSelection = (BaseColumns._ID + "=" + id
                        + if (TextUtils.isEmpty(selection)) "" else " AND $selection")
                table.update(getWritableDatabase(), values, adaptedSelection, selectionArgs)
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
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
            else -> throw IllegalStateException("Invalid content provider URI: $uri")
        }
    }

    /**
     * Matches the provided `uri` with the corresponding table and returns that table.
     *
     * @param uri A content provider uri referring to a table within this content provider. if this `uri` is
     * not valid an `IllegalArgumentException` is thrown.
     * @return The table corresponding to the provided `uri`.
     */
    private fun matchTable(uri: Uri): CyfaceTable {
        @Suppress("MoveVariableDeclarationIntoWhen") // For readability
        val firstPathSegment = uri.pathSegments[0]
        return when (firstPathSegment) {
            MeasurementTable.URI_PATH -> measurementTable
            GeoLocationTable.URI_PATH -> geoLocationTable
            PressureTable.URI_PATH -> pressureTable
            IdentifierTable.URI_PATH -> identifierTable
            EventTable.URI_PATH -> eventTable
            else -> throw IllegalStateException("Unknown table with URI: $uri")
        }
    }
}