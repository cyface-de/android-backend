package de.cyface.persistence;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;

import de.cyface.persistence.AbstractCyfaceMeasurementTable;
import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.model.Point3DSerializer;
import de.cyface.persistence.RotationPointTable;

import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_FALSE;
import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_TRUE;

/**
 * A wrapper for a <code>ContentProviderClient</code> used to provide access to one specific measurement.
 * <p>
 * ATTENTION: If you use this class you must still close the provided <code>ContentProviderClient</code>. This class
 * will not do that for you. This has the benefit, that you may call multiple of its methods without requiring a new
 * <code>ContentProvider</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.3
 * @since 2.0.0
 */
public class MeasurementContentProviderClient {

    /**
     * The identifier of the measurement handled by this client.
     */
    private final long measurementIdentifier;
    /**
     * The client used to load the data to serialize from the <code>ContentProvider</code>.
     */
    private final ContentProviderClient client;
    /**
     * The authority also used by the provided <code>ContentProviderClient</code>. Unfortunately it is
     * not possible to retrieve this from the <code>client</code> itself. To communicate with the client this
     * information is required and so needs to be injected explicitly.
     */
    private final String authority;

    /**
     * Creates a new completely initialized <code>MeasurementContentProviderClient</code> for one measurement, wrapping
     * a <code>ContentProviderClient</code>. The wrapped client is not closed by this object. You are still responsible
     * for closing it after you have finished communication with the <code>ContentProvider</code>.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to serialize.
     * @param client The <code>ContentProviderClient</code> wrapped by this object.
     * @param authority The authority also used by the provided <code>ContentProviderClient</code>. Unfortunately it is
     *            not possible to retrieve this from the <code>client</code> itself. To communicate with the client this
     *            information is required and so needs to be injected explicitly.
     */
    public MeasurementContentProviderClient(final long measurementIdentifier, final @NonNull ContentProviderClient client,
                                            final String authority) {
        this.measurementIdentifier = measurementIdentifier;
        this.client = client;
        this.authority = authority;
    }

    /**
     * Loads a page of the geo locations for the measurement.
     *
     * @param offset The start index of the first geo location to load within the measurement
     * @param limit The number of geo locations to load. A recommended upper limit is:
     *            {@link AbstractCyfaceMeasurementTable#DATABASE_QUERY_LIMIT}
     * @return A <code>Cursor</code> on the geo locations stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    public Cursor loadGeoLocations(final int offset, final int limit) throws RemoteException {
        final Uri uri = new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH)
                .build();
        final String[] projection = new String[] {GpsPointsTable.COLUMN_GPS_TIME, GpsPointsTable.COLUMN_LAT,
                GpsPointsTable.COLUMN_LON, GpsPointsTable.COLUMN_SPEED, GpsPointsTable.COLUMN_ACCURACY};
        final String selection = GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?";
        final String[] selectionArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};

        /*
         * For some reason this does not work (tested on N5X) so we always use the workaround implementation
         * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         * Bundle queryArgs = new Bundle();
         * queryArgs.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
         * queryArgs.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
         * queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
         * queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
         * return client.query(uri, projection, queryArgs, null);
         * }
         */

        // Backward compatibility workaround from https://stackoverflow.com/a/12641015/5815054
        // the arguments limit and offset are only available starting with API 26 ("O")
        return client.query(uri, projection, selection, selectionArgs,
                GpsPointsTable.COLUMN_MEASUREMENT_FK + " ASC limit " + limit + " offset " + offset);
    }

    /**
     * Counts all the data elements from one table for the measurement. Data elements depend on the provided content
     * provider URI and might be geo locations, accelerations, rotations or directions.
     *
     * @param tableUri The content provider Uri of the table to count.
     * @param measurementForeignKeyColumnName The column name of the column containing the reference to the measurement
     *            table.
     * @return the number of data elements stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    public int countData(final @NonNull Uri tableUri, final @NonNull String measurementForeignKeyColumnName)
            throws RemoteException {
        Cursor cursor = null;

        try {
            final String selection = measurementForeignKeyColumnName + "=?";
            final String[] selectionArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};

            cursor = client.query(tableUri, null, selection, selectionArgs, null);
            if (cursor == null) {
                throw new IllegalStateException("Unable to count GeoLocations for measurement.");
            }
            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Loads data points for the measurement. Such points might be accelerometer, gyroscope or compass points.
     *
     * @param serializer A serializer defining which kind of data points to load and how to access them.
     * @return A <code>Cursor</code> on one kind of data points stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    public Cursor load3DPoint(final @NonNull Point3DSerializer serializer) throws RemoteException {
        final Uri contentProviderUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(serializer.getTableUriPathSegment()).build();
        return client.query(contentProviderUri,
                new String[] {serializer.getTimestampColumnName(), serializer.getXColumnName(),
                        serializer.getYColumnName(), serializer.getZColumnName()},
                serializer.getMeasurementKeyColumnName() + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
    }

    /**
     * Loads data points for the measurement. Such points might be accelerometer, gyroscope or compass points.
     *
     * @param serializer A serializer defining which kind of data points to load and how to access them.
     * @param offset The start index of the first point to load within the measurement
     * @param limit The number of points to load. A recommended upper limit is:
     *            {@link AbstractCyfaceMeasurementTable#DATABASE_QUERY_LIMIT}.
     * @return A <code>Cursor</code> on one kind of data points stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    Cursor load3DPoint(final @NonNull Point3DSerializer serializer, final int offset, final int limit)
            throws RemoteException {
        String[] projection = new String[] {serializer.getTimestampColumnName(), serializer.getXColumnName(),
                serializer.getYColumnName(), serializer.getZColumnName()};
        String selection = serializer.getMeasurementKeyColumnName() + "=?";
        String[] selectionArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};
        // This is a hack, that only works for a content provider with a backing database. More recent Android version
        // provide a native API to support offset and limit. We may switch to that API if we increase the minimum
        // version.
        String sortOrder = serializer.getMeasurementKeyColumnName() + " ASC limit " + limit + " offset " + offset;

        final Uri contentProviderUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(serializer.getTableUriPathSegment()).build();
        return client.query(contentProviderUri, projection, selection, selectionArgs, sortOrder);
    }

    /**
     * Cleans the measurement by deleting all data points (accelerations, rotations and directions). And marking the measurement
     * and the gps points as synced. This operation can not be revoked. Your data will be lost afterwards.
     *
     * @return The amount of deleted data points.
     * @throws RemoteException If the content provider is not accessible.
     */
    public int cleanMeasurement() throws RemoteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_SYNCED, true);
        client.update(
                new Uri.Builder().scheme("content").authority(authority).appendPath(MeasurementTable.URI_PATH).build(),
                values, BaseColumns._ID + "=?", new String[] {Long.valueOf(measurementIdentifier).toString()});
        values.clear();

        // gps points
        values.put(GpsPointsTable.COLUMN_IS_SYNCED, true);
        client.update(new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH).build(),
                values, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});

        // data points
        int ret = 0;
        ret += client.delete(
                new Uri.Builder().scheme("content").authority(authority).appendPath(AccelerationPointTable.URI_PATH).build(),
                AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        ret += client.delete(
                new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH)
                        .build(),
                RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        ret += client.delete(
                new Uri.Builder().scheme("content").authority(authority).appendPath(DirectionPointTable.URI_PATH)
                        .build(),
                DirectionPointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        return ret;
    }

    public @NonNull Uri createGeoLocationTableUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH).build();
    }

    /**
     * Loads all measurements from the content provider that are already finished capturing, but have not been
     * synchronized yet.
     *
     * @param provider A client with access to the content provider containing the measurements.
     * @param authority The content provider authority to load the measurements from.
     * @return An initialized cursor pointing to the unsynchronized measurements.
     * @throws RemoteException If the query to the content provider has not been successful.
     * @throws IllegalStateException If the <code>Cursor</code> was not successfully initialized.
     */
    public static Cursor loadSyncableMeasurements(final @NonNull ContentProviderClient provider,
                                                  final @NonNull String authority) throws RemoteException {
        final Uri measurementTableUri = new Uri.Builder().scheme("content").authority(authority)
                .appendPath(MeasurementTable.URI_PATH).build();
        final Cursor ret = provider.query(measurementTableUri, null,
                MeasurementTable.COLUMN_FINISHED + "=? AND " + MeasurementTable.COLUMN_SYNCED + "=?",
                new String[] {String.valueOf(SQLITE_TRUE), String.valueOf(SQLITE_FALSE)}, null);

        if (ret == null) {
            throw new IllegalStateException("Unable to load measurement from content provider!");
        }

        return ret;
    }
}
