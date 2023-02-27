package de.cyface.persistence

import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.utils.CursorIsNullException

/**
 * This [PersistenceBehaviour] is used when a [DefaultPersistenceLayer] is only used to access existing
 * [Measurement]s and does not want to capture new `Measurements`.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
class DefaultPersistenceBehaviour : PersistenceBehaviour {
    private var persistenceLayer: DefaultPersistenceLayer<*>? = null
    override fun onStart(persistenceLayer: DefaultPersistenceLayer<*>) {
        this.persistenceLayer = persistenceLayer
    }

    override fun onNewMeasurement(measurementId: Long) {
        // nothing to do
    }

    override fun shutdown() {
        // nothing to do
    }

    @Throws(
        NoSuchMeasurementException::class,
        CursorIsNullException::class
    )
    override fun loadCurrentlyCapturedMeasurement(): Measurement {

        // The {@code DefaultPersistenceBehaviour} does not have a cache for this so load it from the persistence
        return persistenceLayer!!.loadCurrentlyCapturedMeasurementFromPersistence()
            ?: throw NoSuchMeasurementException(
                "Trying to load currently captured measurement while no measurement was open or paused!"
            )
    }
}