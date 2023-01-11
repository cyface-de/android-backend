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
package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * TODO
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
interface DatabaseHelperInterface {

    /**
     * Deletes one or multiple rows (depending on the format of the provided URI) from the database. If you delete a
     * {@link Measurement} all corresponding {@link GeoLocation} data is cascadingly deleted as well.
     *
     * @param uri The URI specifying the table to delete from. If this ends with a single numeric identifier that row is
     *            deleted otherwise multiple rows might be deleted depending on the <code>selection</code> and
     *            <code>selectionArgs</code>.
     * @param selection The part of an SQL where statement behind the where. You can use '?' as a placeholder to secure
     *            yourself from SQL injections.
     * @param selectionArgs The arguments to place inside the '?' placeholder from <code>selection</code>.
     * @return The number of rows deleted.
     */
    int deleteRow(final @NonNull Uri uri, final String selection, final String[] selectionArgs);

    /**
     * Inserts a single row into a table.
     *
     * @param uri The table to insert the row into.
     * @param values The values to insert in the new table row.
     * @return The identifier of the new row.
     */
    long insertRow(final @NonNull Uri uri, final @NonNull ContentValues values);

    /**
     * Inserts a list of <code>ContentValues</code> as new rows into a table.
     *
     * @param uri The table to insert the new rows into.
     * @param values The values to insert.
     * @return An array of identifiers for the newly created table rows.
     */
    long[] bulkInsert(final @NonNull Uri uri, final @NonNull List<ContentValues> values);

    /**
     * Sends a query to the database and provides the result of that query.
     *
     * @param uri The table to send the query to.
     * @param projection The projection is the part behind the SQL select statement without the <code>SELECT</code>.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @param sortOrder This is either <code>ASC</code> for ascending or <code>DESC</code> for descending.
     * @return A <code>Cursor</code> over the resulting rows. Do not forget to close this cursor after you finished
     *         reading from it. Preferably use <code>finally</code> to close the cursor to avoid memory leaks.
     */
    public Cursor query(final @NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder);

    /**
     * Updates a row or rows in the database with new values.
     *
     * @param uri The table to update rows in.
     * @param values The new values to insert.
     * @param selection A selection query used to select a set of rows to update. This is the part behind the SQL where
     *            statement without the 'WHERE'. You may use '?' placeholders to avoid SQL injection attacks.
     * @param selectionArgs The arguments to insert for the placeholders used in <code>selection</code>. The amount of
     *            '?' and arguments needs to match.
     * @return The number of updated rows.
     */
    public int update(final @NonNull Uri uri, final @NonNull ContentValues values, final String selection,
            final String[] selectionArgs);

    /**
     * Provides the MIME type for the provided URI. This is either <code>vnd.android.cursor.item/de.cyface.data</code>
     * for an URI identifying a single item, or <code>vnd.android.cursor.dir/de.cyface.data</code> for an URI
     * identifying a collection of items.
     *
     * @param uri The URI to provide the MIME type for.
     * @return The MIME type corresponding to that URI.
     */
    String getType(final @NonNull Uri uri);
}
