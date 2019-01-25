package de.cyface.persistence;

import android.content.ContentProvider;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.CursorIsNullException;

/**
 * The {@link PersistenceBehaviour} defines how the {@link PersistenceLayer} is works. Select a behaviour depending on
 * if you want to use the {@code PersistenceLayer} to capture a new {@link Measurement} or to load existing data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public interface PersistenceBehaviour {

    /**
     * This is called in the {@code Persistence}'s constructor.
     */
    void onStart(@NonNull PersistenceLayer persistenceLayer);

    /**
     * This is called after a {@link PersistenceLayer#newMeasurement(Vehicle)} was created.
     * 
     * @param measurementId The id of the recently created {@link Measurement}
     */
    void onNewMeasurement(long measurementId);

    /**
     * This is called when the {@link PersistenceLayer} is no longer needed by {@link PersistenceLayer#shutdown()}.
     */
    void shutdown();

    /**
     * Loads the current {@link Measurement} if an {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED}
     * {@code Measurement} exists.
     *
     * @return The currently captured {@code Measurement}
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     *             {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} {@code Measurement}
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @NonNull
    Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException;
}
