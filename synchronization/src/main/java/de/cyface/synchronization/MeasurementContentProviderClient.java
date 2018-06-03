package de.cyface.synchronization;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * A wrapper for a <code>ContentProviderClient</code> used to provide access to one specific measurement.
 * <p>
 * ATTENTION: You must still close the provided <code>ContentProviderClient</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.0
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
     * Creates a new completely initialized <code>MeasurementContentProviderClient</code> for one measurement, wrapping
     * a <code>ContentProviderClient</code>. The wrapped client is not closed by this object. You are still responsible
     * for closing it after you have finished communication with the <code>ContentProvider</code>.
     *
     * @param measurementIdentifier The device wide unqiue identifier of the measurement to serialize.
     * @param client
     */
    public MeasurementContentProviderClient(final long measurementIdentifier,
                                            final @NonNull ContentProviderClient client) {
        this.measurementIdentifier = measurementIdentifier;
        this.client = client;
    }

    /**
     * Loads a page of the geo locations for the measurement.
     *
     * @param offset The start index of the first geoLocation to load within the measurement
     * @param limit The number of GeoLocations to load, recommended: {@code DATABASE_QUERY_LIMIT}
     * @return A <code>Cursor</code> on the geo locations stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    public Cursor loadGeoLocations(final int offset, final int limit) throws RemoteException {

        final Uri uri = MeasuringPointsContentProvider.GPS_POINTS_URI;
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
    public int countGeoLocations() throws RemoteException {

        final Uri uri = MeasuringPointsContentProvider.GPS_POINTS_URI;
        final String selection = GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?";
        final String[] selectionArgs = new String[]{Long.valueOf(measurementIdentifier).toString()};

        Cursor locationsCursor = client.query(uri, null, selection, selectionArgs, null);
            if (locationsCursor == null) {
                throw new IllegalStateException("Unable to count GeoLocations for measurement.");
            }
            return locationsCursor.getCount();
    }

    /**
     * Loads data points for the measurement. Such points might be accelerometer, gyroscope or compass points.
     *
     * @param serializer A serializer defining which kind of data points to load and how to access them.
     * @return A <code>Cursor</code> on one kind of data points stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    Cursor load3DPoint(final @NonNull Point3DSerializer serializer) throws RemoteException {
        return client.query(serializer.getTableUri(),
                new String[] {serializer.getTimestampColumnName(), serializer.getXColumnName(),
                        serializer.getYColumnName(), serializer.getZColumnName()},
                serializer.getMeasurementKeyColumnName() + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()}, null);
    }

    /**
     * Cleans the measurement by deleting all data points (accelerations, rotations and directions). This operation can
     * not be revoked. Your data will be lost afterwards.
     *
     * @return The amount of deleted data points.
     * @throws RemoteException If the content provider is not accessible.
     */
    int cleanMeasurement() throws RemoteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_SYNCED, true);
        client.update(MeasuringPointsContentProvider.MEASUREMENT_URI, values, BaseColumns._ID + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        int ret = 0;
        ret += client.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        ret += client.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        ret += client.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        return ret;
    }

    /**
     * Loads all measurements from the content provider that are already finished capturing, but have not been
     * synchronized yet.
     *
     * @param provider A client with access to the content provider containing the measurements.
     * @return An initialized cursor pointing to the unsynchronized measurements.
     * @throws RemoteException If the query to the content provider has not been successful.
     * @throws IllegalStateException If the <code>Cursor</code> was not successfully initialized.
     */
    static Cursor loadSyncableMeasurements(final @NonNull ContentProviderClient provider) throws RemoteException {
        Cursor ret = provider.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                MeasurementTable.COLUMN_FINISHED + "=? AND " + MeasurementTable.COLUMN_SYNCED + "=?",
                new String[] {Integer.valueOf(1).toString(), Integer.valueOf(0).toString()}, null);

        if (ret == null) {
            throw new IllegalStateException("Unable to load measurement from content provider!");
        }

        return ret;
    }
}
