package de.cyface.persistence.content

import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import de.cyface.persistence.model.Event
import de.cyface.utils.CursorIsNullException

/**
 * Interface for [DefaultProviderClient] created to be able to mock [DefaultProviderClient] in `MeasurementSerializerTest`.
 */
interface MeasurementProviderClient {

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
    fun loadGeoLocations(offset: Int, limit: Int): Cursor?

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
    fun loadEvents(offset: Int, limit: Int): Cursor?

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
    fun countData(tableUri: Uri, measurementForeignKeyColumnName: String): Int

    fun createGeoLocationTableUri(): Uri

    fun createEventTableUri(): Uri
}
