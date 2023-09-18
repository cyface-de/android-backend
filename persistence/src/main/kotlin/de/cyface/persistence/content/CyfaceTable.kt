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
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base interface for all tables used by this content provider. You need to provide the SQLite database to every method
 * call for performance reasons. That way the connection to the database need not be constructed before the first real
 * call to any data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 1.0.0
 */
interface CyfaceTable {
    /**
     * Called whenever someone deletes a row in the database. The method should call some SQL statement to actually
     * delete the row in the database.
     *
     * @param database The database containing this table.
     * @param selection Selection statement as supported by the Android API, probably with ? for placeholders. This
     * corresponds to the SQL WHERE clause.
     * @param selectionArgs Concrete values for the ? placeholders in the `selection` parameter. Use this to avoid
     * SQL injection attacks.
     * @return The number of rows deleted or -1 if now rows have been deleted.
     */
    fun deleteRow(
        database: SupportSQLiteDatabase,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int

    /**
     * @return The database name as used in the SQLite database itself.
     */
    val name: String

    /**
     * Inserts a new data row into the this table.
     *
     * @param database The database the table belongs to.
     * @param values The new values to insert.
     * @return The identifier of the newly created row as referenced by `BaseColumns#_ID`.
     */
    fun insertRow(database: SupportSQLiteDatabase, values: ContentValues): Long

    /**
     * Query the table for specific data rows.
     *
     * @param database The database this table belongs to.
     * @param projection The set of rows to select from the table. This is the part you would place behind an SQL SELECT
     * statement. You may pass null to indicate that all columns should be selected.
     * @param selection The conditions a row needs to meet to be selected. This is the part you would place into the SQL
     * WHERE statement. You may insert placeholders via ?. You may pass `null` if there is no selection
     * required.
     * @param selectionArgs The values used to replace the ? placeholders in the `selection` part. You may pass
     * `null` if there are no selection arguments. Use this to avoid SQL injection attacks.
     * @param sortOrder The sort order of the returned rows formatted as SQL ORDER BY value without the ORDER BY. So
     * usually either "asc" or "desc". Passing `null` will result in no particular sort order.
     * @return A cursor running over the selected rows.
     */
    fun query(
        database: SupportSQLiteDatabase,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor

    /**
     * Update a specific row of this table with the provided values.
     *
     * @param database The database this table belongs to.
     * @param values The values to update for the specified row.
     * @param selection A selection statement selecting exactly one row to update. May contain ? placeholders filled by
     * `selectionArgs`. This is what you usually expect behind an SQL WHERE statement.
     * @param selectionArgs The values for the ? placeholders within `selection`. Use this to avoid SQL injection
     * attacks.
     * @return The number of updated rows in the database.
     */
    fun update(
        database: SupportSQLiteDatabase,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int

    /**
     * Allows to batch insert multiple values into this table.
     *
     * @param database The database the table is part of.
     * @param valuesList The list of values for each row to insert.
     * @return the list of identifiers for the newly created table rows.
     */
    fun insertBatch(database: SupportSQLiteDatabase, valuesList: List<ContentValues>): LongArray
}