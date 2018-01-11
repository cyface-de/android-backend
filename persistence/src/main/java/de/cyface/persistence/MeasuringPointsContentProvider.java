/*
 * Created at 17:41:23 on 19.01.2015
 */
package de.cyface.persistence;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import java.util.Arrays;
import java.util.Map;

/**
 * <p>
 * A content provider for the databased used as cache for all measurements acquired via the mobile device prior to
 * transferring the data to the server.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 1.0.0
 */
public final class MeasuringPointsContentProvider extends ContentProvider {

    private final static String TAG = MeasuringPointsContentProvider.class.getName();
    public final static String AUTHORITY = BuildConfig.testProvider;
    public final static Uri MEASUREMENT_URI = (new Uri.Builder()).scheme("content")
            .encodedAuthority(AUTHORITY).path(DatabaseHelper.MEASUREMENT_URI_PATH).build();
    public final static Uri GPS_POINTS_URI = (new Uri.Builder()).scheme("content")
            .encodedAuthority(AUTHORITY).appendPath(DatabaseHelper.GPS_POINT_URI_PATH).build();
    public final static Uri SAMPLE_POINTS_URI = (new Uri.Builder()).scheme("content")
            .encodedAuthority(AUTHORITY).appendPath(DatabaseHelper.SAMPLE_POINT_URI_PATH).build();
    public final static Uri ROTATION_POINTS_URI = (new Uri.Builder()).scheme("content")
            .encodedAuthority(AUTHORITY).appendPath(DatabaseHelper.ROTATION_POINT_URI_PATH).build();
    public final static Uri MAGNETIC_VALUE_POINTS_URI = new Uri.Builder().scheme("content")
            .encodedAuthority(AUTHORITY).appendEncodedPath(DatabaseHelper.MAGNETIC_VALUE_POINT_URI_PATH).build();

    public static final String DATABASE_NAME = DatabaseHelper.DATABASE_NAME;

    private DatabaseHelper database;
    private Context context;

    @Override
    public boolean onCreate() {
        context = getContext();
        database = new DatabaseHelper(context);
        return true;
    }

    /**
     * When only one element should be deleted (by id) this method adjusts the url accordingly (i.e. ../ids to ../#id)
     * @param uri The uri identifies the type ob object which should be deleted
     * @param selection The selection defines by which column the deleted object is to be found (e.g. id)
     * @param selectionArgs The selectionArgs contain the column values (e.g. the id(s) of the targeted objects)
     * @return the number of rows ob the given object (e.g. measurement) which has been deleted
     */
    @Override
    public int delete(Uri uri, final String selection, final String[] selectionArgs) {
        if (selection != null && selection.equals(BaseColumns._ID+ "=?") && selectionArgs.length == 1) {
            uri = ContentUris.withAppendedId(uri, Long.valueOf(selectionArgs[0]));
        }
        int rowsDeleted = database.deleteRow(uri, selection, selectionArgs);
        context.getContentResolver().notifyChange(uri, null);

        return rowsDeleted;
    }

    @Override
    public String getType(final Uri uri) {
        return database.getType(uri);
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        long newRowIdentifier = database.insertRow(uri, values);
        context.getContentResolver().notifyChange(uri, null);
        return Uri.parse(uri.getLastPathSegment() + "/" + newRowIdentifier);
    }

    @Override public int bulkInsert(Uri uri, ContentValues[] values) {
        //Log.d(TAG,"Writing bulk to database: #"+values.length);
        return database.bulkInsert(uri, Arrays.asList(values)).length;
    }

    private String toString(final ContentValues[] valuesArray) {
        StringBuffer ret = new StringBuffer();
        for(ContentValues values:valuesArray) {
            ret.append(toString(values));
        }
        return ret.toString();
    }

    private String toString(final ContentValues values) {
        StringBuffer ret = new StringBuffer();
        for(Map.Entry<String,Object> entry:values.valueSet()) {
            ret.append("\n\t").append(entry.getKey()).append(":").append(entry.getValue());
        }
        return ret.toString();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final Cursor cursor = database.query(uri, projection, selection, selectionArgs, sortOrder);
        cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        int rowsUpdated = database.update(uri, values, selection, selectionArgs);
        context.getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }

    public long bulkDeleteSyncedMeasurementPoints(Context context, long gps_unSynced_from, long sp_unSynced_from, long rp_unSynced_from, long mp_unSynced_from) {
        long deleted = 0;
        this.context = context;
        database = new DatabaseHelper(context);
        database.getWritableDatabase().beginTransaction();
        deleted += delete(MeasuringPointsContentProvider.GPS_POINTS_URI, BaseColumns._ID + "<=?", new String[]{String.valueOf(gps_unSynced_from - 1)});
        deleted += delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, BaseColumns._ID + "<=?", new String[]{String.valueOf(sp_unSynced_from - 1)});
        deleted += delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI, BaseColumns._ID + "<=?", new String[]{String.valueOf(rp_unSynced_from - 1)});
        deleted += delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, BaseColumns._ID + "<=?", new String[]{String.valueOf(mp_unSynced_from - 1)});
        database.getWritableDatabase().setTransactionSuccessful();
        database.getWritableDatabase().endTransaction();
        return deleted;
    }
}
