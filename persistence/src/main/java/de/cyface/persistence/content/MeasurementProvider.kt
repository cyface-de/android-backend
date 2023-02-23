/*
 * Copyright 2021-2023 Cyface GmbH
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

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import de.cyface.persistence.Database
import de.cyface.persistence.dao.IdentifierDao
import java.util.Arrays

/**
 * A content provider for the databased used as cache for all measurements acquired via the mobile device prior to
 * transferring the data to the server.
 *
 * A `ContentProvider` is required for other apps to access the persistence layer from the app which
 * integrates this SDK. In our case we use a `SyncAdapter` which is like "another app" accessing our
 * data, so a `ContentProvider` is needed.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
class MeasurementProvider : ContentProvider() {
    /**
     * The database to load data from.
     */
    private lateinit var database: Database

    /**
     * The object to access [de.cyface.persistence.model.Identifier] data from.
     */
    private var identifierDao: IdentifierDao? = null

    override fun onCreate(): Boolean {
        database = Database.getDatabase(context!!.applicationContext)
        identifierDao = database.identifierDao()
        return true
    }

    /**
     * When only one element should be deleted (by id) this method adjusts the url accordingly (i.e. ../ids to ../#id)
     *
     * @param uri The uri identifies the type ob object which should be deleted
     * @param selection The selection defines by which column the deleted object is to be found (e.g. id)
     * @param selectionArgs The selectionArgs contain the column values (e.g. the id(s) of the targeted objects)
     * @return the number of rows ob the given object (e.g. measurement) which has been deleted
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        var uriWithPotentialSelection = uri
        if (selectionArgs != null && BaseColumns._ID + "=?" == selection && selectionArgs.size == 1) {
            uriWithPotentialSelection = ContentUris.withAppendedId(uri, selectionArgs[0].toLong())
        }
        val rowsDeleted: Int =
            database.openHelper.writableDatabase.delete(
                uriWithPotentialSelection,
                selection,
                selectionArgs
            )
        localContext!!.contentResolver.notifyChange(uriWithPotentialSelection, null)
        return rowsDeleted
    }

    /**
     * Provides the MIME type for the provided URI. This is either `vnd.android.cursor.item/de.cyface.data`
     * for an URI identifying a single item, or `vnd.android.cursor.dir/de.cyface.data` for an URI
     * identifying a collection of items.
     *
     * @param uri The URI to provide the MIME type for.
     * @return The MIME type corresponding to that URI.
     */
    override fun getType(uri: Uri): String {
        return when (uri.pathSegments.size) {
            1 -> "vnd.android.cursor.item/de.cyface.data"
            2 -> "vnd.android.cursor.dir/de.cyface.data"
            else -> throw IllegalStateException("Invalid content provider URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // FIXME: Insert code here to determine which DAO to use when inserting data, handle error conditions, etc.
        val newRowIdentifier: Long = database.insertRow(uri, values)
        localContext!!.contentResolver.notifyChange(uri, null)
        return Uri.parse(uri.lastPathSegment + "/" + newRowIdentifier)
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        return database.bulkInsert(uri, Arrays.asList(*values)).length
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val cursor: Cursor = database.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor.setNotificationUri(localContext!!.contentResolver, uri)
        return cursor
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val rowsUpdated: Int = database.update(uri, values, selection, selectionArgs)
        localContext!!.contentResolver.notifyChange(uri, null)
        return rowsUpdated
    }
}