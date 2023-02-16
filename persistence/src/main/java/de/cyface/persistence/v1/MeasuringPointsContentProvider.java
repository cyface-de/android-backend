/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.v1;

import java.util.Arrays;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import de.cyface.persistence.v1.DatabaseHelper;
import de.cyface.utils.Validate;

/**
 * A content provider for the databased used as cache for all measurements acquired via the mobile device prior to
 * transferring the data to the server.
 *
 * @author Klemens Muthmann
 * @version 1.1.6
 * @since 1.0.0
 */
public final class MeasuringPointsContentProvider extends ContentProvider {

    /**
     * A representation of the database manged by this <code>ContentProvider</code>.
     */
    private DatabaseHelper database;
    /**
     * The Android context used by this <code>ContentProvider</code>.
     */
    private Context context;

    @Override
    public boolean onCreate() {
        context = getContext();
        Validate.notNull(context);
        database = new DatabaseHelper(context);
        return true;
    }

    /**
     * When only one element should be deleted (by id) this method adjusts the url accordingly (i.e. ../ids to ../#id)
     *
     * @param uri The uri identifies the type ob object which should be deleted
     * @param selection The selection defines by which column the deleted object is to be found (e.g. id)
     * @param selectionArgs The selectionArgs contain the column values (e.g. the id(s) of the targeted objects)
     * @return the number of rows ob the given object (e.g. measurement) which has been deleted
     */
    @Override
    public int delete(final @NonNull Uri uri, final String selection, final String[] selectionArgs) {
        Uri uriWithPotentialSelection = uri;
        if (selectionArgs != null && (BaseColumns._ID + "=?").equals(selection) && selectionArgs.length == 1) {
            uriWithPotentialSelection = ContentUris.withAppendedId(uri, Long.parseLong(selectionArgs[0]));
        }
        int rowsDeleted = database.deleteRow(uriWithPotentialSelection, selection, selectionArgs);
        context.getContentResolver().notifyChange(uriWithPotentialSelection, null);

        return rowsDeleted;
    }

    @Override
    public String getType(@NonNull final Uri uri) {
        return database.getType(uri);
    }

    @Override
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {
        long newRowIdentifier = database.insertRow(uri, values);
        context.getContentResolver().notifyChange(uri, null);
        return Uri.parse(uri.getLastPathSegment() + "/" + newRowIdentifier);
    }

    @Override
    public int bulkInsert(@NonNull final Uri uri, @NonNull final ContentValues[] values) {
        return database.bulkInsert(uri, Arrays.asList(values)).length;
    }

    @Override
    public Cursor query(@NonNull final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        final Cursor cursor = database.query(uri, projection, selection, selectionArgs, sortOrder);
        cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int update(@NonNull final Uri uri, final ContentValues values, final String selection,
            final String[] selectionArgs) {
        int rowsUpdated = database.update(uri, values, selection, selectionArgs);
        context.getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }
}
