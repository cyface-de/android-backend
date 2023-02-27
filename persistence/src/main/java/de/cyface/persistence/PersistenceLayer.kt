package de.cyface.persistence

import android.content.Context
import de.cyface.persistence.dao.FileDao
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.utils.CursorIsNullException
import java.io.File

/**
 * Interface for [DefaultPersistenceLayer] created to be able to mock [DefaultPersistenceLayer] in `DataCapturingLocalTest`.
 */
interface PersistenceLayer<B : PersistenceBehaviour?> {
    /**
     * The [Context] required to locate the app's internal storage directory.
     */
    val context: Context?

    /**
     * The [FileDao] used to interact with files.
     *
     * **ATTENTION:** This should not be used by SDK implementing apps.
     */
    val fileDao: FileDao

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     *
     * **ATTENTION:** This method is called automatically and should not be called from outside the SDK.
     */
    fun shutdown()

    /**
     * Provide one specific [Measurement] from the data storage if it exists.
     *
     * Attention: As the loaded `Measurement` object and the persistent version of it in the
     * [DefaultPersistenceLayer] are not directly connected the loaded object is not notified when
     * the it's counterpart in the `PersistenceLayer` is changed (e.g. the [MeasurementStatus]).
     *
     * @param measurementIdentifier The device wide unique identifier of the `Measurement` to load.
     * @return The loaded `Measurement` if it exists; `null` otherwise.
     */
    @Throws(CursorIsNullException::class)  // Sdk implementing apps (SR) use this to load single measurements
    fun loadMeasurement(measurementIdentifier: Long): Measurement?

    /**
     * Returns the directory used to store temporary files such as the files prepared for synchronization.
     *
     * @return The directory to be used for temporary files
     */
    val cacheDir: File
}