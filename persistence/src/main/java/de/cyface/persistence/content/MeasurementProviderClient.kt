/*
 * Copyright 2018-2023 Cyface GmbH
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

import android.content.ContentProviderClient
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import de.cyface.persistence.model.Event
import de.cyface.utils.CursorIsNullException

/**
 * A wrapper for a `ContentProviderClient` used to provide access to one specific measurement.
 *
 * See [MeasurementProvider] for why a `ContentProvider` is required.
 *
 * *ATTENTION:* If you use this class you must still close the provided `ContentProviderClient`. This class
 * will not do that for you. This has the benefit, that you may call multiple of its methods without requiring a new
 * `ContentProvider`.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 * @property measurementIdentifier The identifier of the measurement handled by this client.
 * @property client The client used to load the data to serialize from the `ContentProvider`.
 * @property authority The authority also used by the provided `ContentProviderClient`. Unfortunately it is
 * not possible to retrieve this from the `client` itself. To communicate with the client this
 * information is required and so needs to be injected explicitly.
 */
class MeasurementProviderClient(
    private val measurementIdentifier: Long,
    private val client: ContentProviderClient,
    private val authority: String
) {
    /**
     * Loads a page of the geo locations for the measurement.
     *
     * @param offset The start index of the first geo location to load within the measurement
     * @param limit The number of geo locations to load. A recommended upper limit is:
     * [AbstractCyfaceTable.DATABASE_QUERY_LIMIT]
     * @return A `Cursor` on the [de.cyface.persistence.model.GeoLocation]s stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     */
    @Throws(RemoteException::class)
    fun loadGeoLocations(offset: Int, limit: Int): Cursor? {
        val uri = LocationTable.getUri(authority)
        val projection = arrayOf(
            BaseColumns.TIMESTAMP,
            LocationTable.COLUMN_LAT,
            LocationTable.COLUMN_LON,
            LocationTable.COLUMN_SPEED,
            LocationTable.COLUMN_ACCURACY
        )
        // Constructing selection clause with replaceable parameter `?` avoids SQL injection
        val selection = BaseColumns.MEASUREMENT_ID + "=?"
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
            BaseColumns.MEASUREMENT_ID + " ASC limit " + limit + " offset " + offset
        )
    }

    /**
     * Loads a page of the [Event]s for the `Measurement`.
     *
     * @param offset The start index of the first `Event` to load within the Measurement
     * @param limit The number of Events to load. A recommended upper limit is:
     * [AbstractCyfaceTable.DATABASE_QUERY_LIMIT]
     * @return A `Cursor` on the [Event]s stored for the [de.cyface.persistence.model.Measurement].
     * @throws RemoteException If the content provider is not accessible.
     */
    @Throws(RemoteException::class)
    fun loadEvents(offset: Int, limit: Int): Cursor? {
        val uri = EventTable.getUri(authority)
        val projection = arrayOf(
            EventTable.COLUMN_TYPE, EventTable.COLUMN_VALUE, BaseColumns.TIMESTAMP
        )
        // Constructing selection clause with replaceable parameter `?` avoids SQL injection
        val selection = BaseColumns.MEASUREMENT_ID + "=?"
        val selectionArgs = arrayOf(
            java.lang.Long.valueOf(
                measurementIdentifier
            ).toString()
        )

        // Backward compatibility workaround from https://stackoverflow.com/a/12641015/5815054
        // the arguments limit and offset are only available starting with API 26 ("O")
        return client.query(
            uri, projection, selection, selectionArgs,
            BaseColumns.MEASUREMENT_ID + " ASC limit " + limit + " offset " + offset
        )
    }

    /**
     * Counts all the data elements from one table for the measurements. Data elements depend on the provided
     * `ContentProvider` [Uri].
     *
     * @param tableUri The content provider Uri of the table to count.
     * @param measurementForeignKeyColumnName The column name of the column containing the reference to the measurement
     * table.
     * @return the number of data elements stored for the measurement.
     * @throws RemoteException If the content provider is not accessible.
     * @throws CursorIsNullException If `ContentProvider` was inaccessible.
     */
    @Throws(RemoteException::class, CursorIsNullException::class)
    fun countData(tableUri: Uri, measurementForeignKeyColumnName: String): Int {
        var cursor: Cursor? = null
        return try {
            // Constructing selection clause with replaceable parameter `?` avoids SQL injection
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

    fun createGeoLocationTableUri(): Uri {
        return LocationTable.getUri(authority)
    }

    fun createEventTableUri(): Uri {
        return EventTable.getUri(authority)
    }
}