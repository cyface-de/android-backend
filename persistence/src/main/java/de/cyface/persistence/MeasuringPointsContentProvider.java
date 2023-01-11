package de.cyface.persistence;

import static de.cyface.persistence.Utils.getGeoLocationsV6Uri;
import static de.cyface.persistence.Utils.getPressuresUri;

import java.util.Arrays;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.room.Room;

import de.cyface.utils.Validate;

/**
 * A content provider for the databased used as cache for all measurements acquired via the mobile device prior to
 * transferring the data to the server.
 *
 * @author Klemens Muthmann
 * @version 1.1.5
 * @since 1.0.0
 */
public final class MeasuringPointsContentProvider extends ContentProvider {

    /**
     * A representation of the database manged by this <code>ContentProvider</code>.
     */
    private DatabaseHelper database;
    //private DatabaseHelperV6 databaseV6;
    //private DatabaseV6 databaseV6;
    /**
     * The Android context used by this <code>ContentProvider</code>.
     */
    private Context context;

    @Override
    public boolean onCreate() {
        context = getContext();
        Validate.notNull(context);
        database = new DatabaseHelper(context);
        //databaseV6 = new DatabaseHelperV6(context);
        //databaseV6 = Room.databaseBuilder(context.getApplicationContext(), DatabaseV6.class, "v6").build();
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
        DatabaseHelperInterface database = getDatabase(uri);
        Uri uriWithPotentialSelection = uri;
        if (selectionArgs != null && (BaseColumns._ID + "=?").equals(selection) && selectionArgs.length == 1) {
            uriWithPotentialSelection = ContentUris.withAppendedId(uri, Long.parseLong(selectionArgs[0]));
        }
        int rowsDeleted = database.deleteRow(uriWithPotentialSelection, selection, selectionArgs);
        context.getContentResolver().notifyChange(uriWithPotentialSelection, null);

        return rowsDeleted;
    }

    private DatabaseHelperInterface getDatabase(final @NonNull Uri uri) {
        // FIXME: This information is only available after `CyfaceDataCapturingService` is called, not in here
        /*if (uri.equals(getGeoLocationsV6Uri("de.cyface.app.provider.v6")) ||
                uri.equals(getPressuresUri("de.cyface.app.provider.v6"))) {
            return this.databaseV6;
        } else {*/
            return this.database;
        //}
    }

    @Override
    public String getType(@NonNull final Uri uri) {
        return getDatabase(uri).getType(uri);
    }

    @Override
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {
        long newRowIdentifier = getDatabase(uri).insertRow(uri, values);
        context.getContentResolver().notifyChange(uri, null);
        return Uri.parse(uri.getLastPathSegment() + "/" + newRowIdentifier);
    }

    @Override
    public int bulkInsert(@NonNull final Uri uri, @NonNull final ContentValues[] values) {
        return getDatabase(uri).bulkInsert(uri, Arrays.asList(values)).length;
    }

    @Override
    public Cursor query(@NonNull final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        final Cursor cursor = getDatabase(uri).query(uri, projection, selection, selectionArgs, sortOrder);
        cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int update(@NonNull final Uri uri, final ContentValues values, final String selection,
            final String[] selectionArgs) {
        int rowsUpdated = getDatabase(uri).update(uri, values, selection, selectionArgs);
        context.getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }
}
