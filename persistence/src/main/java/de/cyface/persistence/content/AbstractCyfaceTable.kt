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
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.database.sqlite.SQLiteQueryBuilder
import androidx.sqlite.db.SupportSQLiteDatabase


/**
 * Abstract base class for all Cyface measurement tables implementing common functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
abstract class AbstractCyfaceTable internal constructor(name: String) : CyfaceTable {
    /**
     * The database table name.
     */
    final override val name: String

    /**
     * An array containing all the column names used by this table.
     */
    protected abstract val databaseTableColumns: Array<String>

    init {
        check(name.isNotEmpty()) { "Database table name may not be empty." }
        this.name = name
    }

    override fun insertRow(database: SupportSQLiteDatabase, values: ContentValues): Long {
        // CONFLICT_NONE is used as this was the default behavior before (in SQLiteDatabase)
        return database.insert(name, CONFLICT_NONE, values)
        // We could use Room, but stick with the tested low-level implementation for now.
        // Room implementation: https://github.com/android/architecture-components-samples/
    }

    // BulkInsert is about 80 times faster than insertBatch
    override fun insertBatch(
        database: SupportSQLiteDatabase,
        valuesList: List<ContentValues>
    ): LongArray {
        val ret = LongArray(valuesList.size)
        database.beginTransaction()
        try {
            val len = valuesList.size
            for (i in 0 until len) {
                ret[i] = insertRow(database, valuesList[i])
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return ret
    }

    override fun deleteRow(
        database: SupportSQLiteDatabase,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return database.delete(name, selection, selectionArgs)
    }

    override fun query(
        database: SupportSQLiteDatabase, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        checkColumns(projection)
        // We could use a Room generated cursor: https://stackoverflow.com/a/46883664/5815054
        val builder = SQLiteQueryBuilder()
        builder.tables = name
        val query = builder.buildQuery(projection, selection, null, null, sortOrder, null)
        return database.query(query)
    }

    private fun checkColumns(projection: Array<String>?) {
        if (projection != null) {
            val requestedColumns: Set<String> = HashSet(listOf(*projection))
            val availableColumns: Set<String> = HashSet(mutableListOf(*databaseTableColumns))
            require(availableColumns.containsAll(requestedColumns)) { "Unknown columns in projection" }
        }
    }

    override fun update(
        database: SupportSQLiteDatabase,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        // CONFLICT_NONE is used as this was the default behavior before (in SQLiteDatabase)
        return database.update(name, CONFLICT_NONE, values, selection, selectionArgs)
    }

    override fun toString(): String {
        return name
    }

    companion object {
        /**
         * Loading all entries at once seems slower than loading it in chunks of 10k entries [#MOV-248]
         */
        const val DATABASE_QUERY_LIMIT = 10000
    }
}