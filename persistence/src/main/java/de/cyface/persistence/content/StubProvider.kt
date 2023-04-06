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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * In order to use `SyncAdapter` a `ContentProvider` must exist, but does not need to be used.
 *
 * We create a [StubProvider] which does nothing and load the data in the `SyncAdapter` normally.
 * The previous `MeasurementProvider` was removed after version 7.5.0. [STAD-460]
 *
 * See also:
 * - `Content provider client` in https://developer.android.com/training/sync-adapters/creating-sync-adapter
 * - discussion: https://stackoverflow.com/a/4650589/5815054
 * - guide: https://developer.android.com/training/sync-adapters/creating-stub-provider
 * - Beside the `SyncAdapter` use case which works with a [StubProvider], a [ContentProvider] is required
 * when other apps access the data from the SDK implementing app. This is not the case here.
 * https://developer.android.com/guide/topics/providers/content-provider-creating
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.6.0
 */
class StubProvider : ContentProvider() {
    /*
     * Always return true, indicating that the provider loaded correctly.
     */
    override fun onCreate(): Boolean = true

    /*
     * Return no type for MIME type
     */
    override fun getType(uri: Uri): String? = null

    /*
     * query() always returns no results
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    /*
     * insert() always returns null (no URI)
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    /*
     * delete() always returns "no rows affected" (0)
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /*
     * update() always returns "no rows affected" (0)
     */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}