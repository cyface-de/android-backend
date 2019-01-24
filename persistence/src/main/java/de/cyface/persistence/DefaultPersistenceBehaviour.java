package de.cyface.persistence;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * This {@link PersistenceBehaviour} is used when a {@link PersistenceLayer} is only used to access existing
 * {@link Measurement}s and does not want to capture new {@code Measurements}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class DefaultPersistenceBehaviour implements PersistenceBehaviour {
    @Override
    public void onStart(@NonNull PersistenceLayer persistenceLayer) {
        // nothing to do
    }

    @Override
    public void onNewMeasurement(long measurementId) {
        // nothing to do
    }

    @Override
    public void shutdown() {
        // nothing to do
    }
}
