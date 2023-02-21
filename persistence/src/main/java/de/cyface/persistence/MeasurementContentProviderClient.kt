package de.cyface.persistence

import android.content.ContentProviderClient
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import de.cyface.persistence.v1.EventTable
import de.cyface.persistence.v1.GeoLocationsTable
import de.cyface.persistence.v1.Utils
import de.cyface.utils.CursorIsNullException

/**
 * A wrapper for a `ContentProviderClient` used to provide access to one specific measurement.
 *
 * ATTENTION: If you use this class you must still close the provided `ContentProviderClient`. This class
 * will not do that for you. This has the benefit, that you may call multiple of its methods without requiring a new
 * `ContentProvider`.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 2.0.0
 * @property measurementIdentifier The identifier of the measurement handled by this client.
 * @property client The client used to load the data to serialize from the `ContentProvider`.
 * @property authority The authority also used by the provided `ContentProviderClient`. Unfortunately it is
 * not possible to retrieve this from the `client` itself. To communicate with the client this
 * information is required and so needs to be injected explicitly.
 */
class MeasurementContentProviderClient(
    private val measurementIdentifier: Long,
    private val client: ContentProviderClient,
    private val authority: String
) {
    /**
     * Loads a page of the geo locations for the measurement.
     *
     * @param offset The start index of the first geo location to load within the measurement
     * @param limit The number of geo locations to load. A recommended upper limit is:
     * [AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT]
     * @return A `Cursor` on the [ParcelableGeoLocation]s stored for the [Measurement].
     * @throws RemoteException If the content provider is not accessible.
     */
    @Throws(RemoteException::class)
    fun loadGeoLocations(offset: Int, limit: Int): Cursor? {
        val uri = Utils.getGeoLocationsUri(authority)
        val projection = arrayOf(
            GeoLocationsTable.COLUMN_GEOLOCATION_TIME,
            GeoLocationsTable.COLUMN_LAT,
            GeoLocationsTable.COLUMN_LON,
            GeoLocationsTable.COLUMN_SPEED,
            GeoLocationsTable.COLUMN_ACCURACY
        )
        val selection = GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?"
        val selectionArgs = arrayOf(
            java.lang.Long.valueOf(
                measurementIdentifier
            ).toString()
        )

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
        return client.query(
            uri, projection, selection, selectionArgs,
            GeoLocationsTable.COLUMN_MEASUREMENT_FK + " ASC limit " + limit + " offset " + offset
        )
    }

    /**
     * Loads a page of the [Event]s for the `Measurement`.
     *
     * @param offset The start index of the first `Event` to load within the Measurement
     * @param limit The number of Events to load. A recommended upper limit is:
     * [AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT]
     * @return A `Cursor` on the [Event]s stored for the [Measurement].
     * @throws RemoteException If the content provider is not accessible.
     */
    @Throws(RemoteException::class)
    fun loadEvents(offset: Int, limit: Int): Cursor? {
        val uri = Utils.getEventUri(authority)
        val projection = arrayOf(
            EventTable.COLUMN_TYPE, EventTable.COLUMN_VALUE,
            EventTable.COLUMN_TIMESTAMP
        )
        val selection = EventTable.COLUMN_MEASUREMENT_FK + "=?"
        val selectionArgs = arrayOf(
            java.lang.Long.valueOf(
                measurementIdentifier
            ).toString()
        )

        // Backward compatibility workaround from https://stackoverflow.com/a/12641015/5815054
        // the arguments limit and offset are only available starting with API 26 ("O")
        return client.query(
            uri, projection, selection, selectionArgs,
            EventTable.COLUMN_MEASUREMENT_FK + " ASC limit " + limit + " offset " + offset
        )
    }

    /**
     * Counts all the data elements from one table for the [Measurement]s. Data elements depend on the provided
     * [ContentProvider] [Uri] and might be [ParcelableGeoLocation]s.
     *
     * @param tableUri The content provider Uri of the table to count.
     * @param measurementForeignKeyColumnName The column name of the column containing the reference to the measurement
     * table.
     * @return the number of data elements stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(RemoteException::class, CursorIsNullException::class)
    fun countData(tableUri: Uri, measurementForeignKeyColumnName: String): Int {
        var cursor: Cursor? = null
        return try {
            val selection = "$measurementForeignKeyColumnName=?"
            val selectionArgs = arrayOf(
                java.lang.Long.valueOf(
                    measurementIdentifier
                ).toString()
            )
            cursor = client.query(tableUri, null, selection, selectionArgs, null)
            CursorIsNullException.softCatchNullCursor(cursor)
            cursor!!.count
        } finally {
            cursor?.close()
        }
    }
}