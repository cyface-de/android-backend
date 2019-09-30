package de.cyface.persistence;

import static de.cyface.utils.CursorIsNullException.softCatchNullCursor;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;

/**
 * A wrapper for a <code>ContentProviderClient</code> used to provide access to one specific measurement.
 * <p>
 * ATTENTION: If you use this class you must still close the provided <code>ContentProviderClient</code>. This class
 * will not do that for you. This has the benefit, that you may call multiple of its methods without requiring a new
 * <code>ContentProvider</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.2
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
    public MeasurementContentProviderClient(final long measurementIdentifier,
            final @NonNull ContentProviderClient client, final String authority) {
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
     * @return A <code>Cursor</code> on the {@link GeoLocation}s stored for the {@link Measurement}.
     * @throws RemoteException If the content provider is not accessible.
     */
    public Cursor loadGeoLocations(final int offset, final int limit) throws RemoteException {
        final Uri uri = Utils.getGeoLocationsUri(authority);
        final String[] projection = new String[] {GeoLocationsTable.COLUMN_GEOLOCATION_TIME,
                GeoLocationsTable.COLUMN_LAT,
                GeoLocationsTable.COLUMN_LON, GeoLocationsTable.COLUMN_SPEED, GeoLocationsTable.COLUMN_ACCURACY};
        final String selection = GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?";
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
                GeoLocationsTable.COLUMN_MEASUREMENT_FK + " ASC limit " + limit + " offset " + offset);
    }

    /**
     * Counts all the data elements from one table for the {@link Measurement}s. Data elements depend on the provided
     * {@link ContentProvider} {@link Uri} and might be {@link GeoLocation}s.
     *
     * @param tableUri The content provider Uri of the table to count.
     * @param measurementForeignKeyColumnName The column name of the column containing the reference to the measurement
     *            table.
     * @return the number of data elements stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public int countData(final @NonNull Uri tableUri, final @NonNull String measurementForeignKeyColumnName)
            throws RemoteException, CursorIsNullException {
        Cursor cursor = null;

        try {
            final String selection = measurementForeignKeyColumnName + "=?";
            final String[] selectionArgs = new String[] {Long.valueOf(measurementIdentifier).toString()};

            cursor = client.query(tableUri, null, selection, selectionArgs, null);
            softCatchNullCursor(cursor);

            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public @NonNull Uri createGeoLocationTableUri() {
        return Utils.getGeoLocationsUri(authority);
    }
}
