package de.cyface.persistence.repository

import androidx.annotation.WorkerThread
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.model.Identifier

/**
 * The repository mediates between different data sources for [Identifier].
 *
 * It manages identifier queries and decides to fetch data from local persistence or network.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 * @property dao The object to access identifier data from the persistence layer.
 */
class IdentifierRepository(private val dao: IdentifierDao) {

    // Room executes queries on separate threads. Flow notifies the observers on data changes.
    // FIXME: see https://developer.android.com/codelabs/android-room-with-a-view-kotlin#8
    // The list of words is a public property. It's initialized by getting the Flow list of words from Room;
    // you can do this because of how you defined the getAlphabetizedWords method to return Flow in the
    // "Observing database changes" step. Room executes all queries on a separate thread.
    //val allIdentifiers: Flow<List<Identifier>> = identifierDao.getAll()

    // Room runs suspend queries off the main thread.
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(identifier: Identifier) {
        dao.insert(identifier)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun getAll(): List<Identifier?>? {
        return dao.getAll()
    }
}