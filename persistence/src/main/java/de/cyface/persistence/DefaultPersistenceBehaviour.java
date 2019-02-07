package de.cyface.persistence;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;

/**
 * This {@link PersistenceBehaviour} is used when a {@link PersistenceLayer} is only used to access existing
 * {@link Measurement}s and does not want to capture new {@code Measurements}.
 *
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 3.0.0
 */
public class DefaultPersistenceBehaviour implements PersistenceBehaviour {

    private PersistenceLayer persistenceLayer;

    @Override
    public void onStart(@NonNull PersistenceLayer persistenceLayer) {
        this.persistenceLayer = persistenceLayer;
    }

    @Override
    public void onNewMeasurement(long measurementId) {
        // nothing to do
    }

    @Override
    public void shutdown() {
        // nothing to do
    }

    @NonNull
    @Override
    public Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException {

        // The {@code DefaultPersistenceBehaviour} does not have a cache for this so load it from the persistence
        final Measurement measurement = persistenceLayer.loadCurrentlyCapturedMeasurementFromPersistence();
        if (measurement == null) {
            throw new NoSuchMeasurementException(
                    "Trying to load currently captured measurement while no measurement was open or paused!");
        }

        return measurement;
    }
}
