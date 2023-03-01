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
import android.content.Context
import android.database.Cursor
import android.net.Uri
import de.cyface.persistence.Database

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
     * A representation of the database managed by this [MeasurementProvider].
     */
    private lateinit var helper: MeasurementProviderHelper

    /**
     * The Android context used by this `ContentProvider`.
     */
    private var myContext: Context? = null

    override fun onCreate(): Boolean {
        myContext = context
        val database = Database.getDatabase(context!!.applicationContext)
        helper = MeasurementProviderHelper(context!!, database)
        return true
    }

    override fun getType(uri: Uri): String {
        return helper.getType(uri)
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
        if (selectionArgs != null && BaseColumns.ID + "=?" == selection && selectionArgs.size == 1) {
            uriWithPotentialSelection = ContentUris.withAppendedId(uri, selectionArgs[0].toLong())
        }
        val rowsDeleted = helper.deleteRow(uriWithPotentialSelection, selection, selectionArgs)
        myContext!!.contentResolver.notifyChange(uriWithPotentialSelection, null)
        return rowsDeleted
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val newRowIdentifier = helper.insertRow(uri, values!!)
        myContext!!.contentResolver.notifyChange(uri, null)
        return Uri.parse(uri.lastPathSegment + "/" + newRowIdentifier)
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        return helper.bulkInsert(uri, values.toList()).size
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val cursor: Cursor = helper.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor.setNotificationUri(myContext!!.contentResolver, uri)
        return cursor
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val rowsUpdated = helper.update(uri, values, selection, selectionArgs)
        myContext!!.contentResolver.notifyChange(uri, null)
        return rowsUpdated
    }
}