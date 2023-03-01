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
class DefaultProviderClient(
    private val measurementIdentifier: Long,
    private val client: ContentProviderClient,
    private val authority: String
) : MeasurementProviderClient {
    @Throws(RemoteException::class)
    override fun loadGeoLocations(offset: Int, limit: Int): Cursor? {
        val uri = createGeoLocationTableUri()
        val projection = arrayOf(
            BaseColumns.TIMESTAMP,
            LocationTable.COLUMN_LAT,
            LocationTable.COLUMN_LON,
            LocationTable.COLUMN_ALTITUDE,
            LocationTable.COLUMN_SPEED,
            LocationTable.COLUMN_ACCURACY,
            LocationTable.COLUMN_VERTICAL_ACCURACY
        )
        // Constructing selection clause with replaceable parameter `?` avoids SQL injection
        val selection = BaseColumns.MEASUREMENT_ID + "=?"
        val selectionArgs = arrayOf(measurementIdentifier.toString())

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
        // FIXME: There is a cleaner solution in that thread now, which does not abuse `order` for `limit`
        // https://stackoverflow.com/a/24055457/5815054
        return client.query(
            uri, projection, selection, selectionArgs,
            BaseColumns.MEASUREMENT_ID + " ASC limit " + limit + " offset " + offset
        )
    }

    @Throws(RemoteException::class)
    override fun loadEvents(offset: Int, limit: Int): Cursor? {
        val uri = createEventTableUri()
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
        // FIXME: see other usages of this hack in our code
        return client.query(
            uri, projection, selection, selectionArgs,
            BaseColumns.MEASUREMENT_ID + " ASC limit " + limit + " offset " + offset
        )
    }

    @Throws(RemoteException::class, CursorIsNullException::class)
    override fun countData(tableUri: Uri, measurementForeignKeyColumnName: String): Int {
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

    override fun createGeoLocationTableUri(): Uri {
        return LocationTable.getUri(authority)
    }

    override fun createEventTableUri(): Uri {
        return EventTable.getUri(authority)
    }
}