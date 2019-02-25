package de.cyface.datacapturing.exception;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;

/**
 * An {@link Exception} thrown when there are {@link Measurement}s in an invalid {@link MeasurementStatus}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 */
public final class CorruptedMeasurementException extends Exception {

    /**
     * Creates a new completely initialized {@link CorruptedMeasurementException}.
     *
     * @param message The message to show as a detailed explanation for this <code>Exception</code>.
     */
    public CorruptedMeasurementException(final @NonNull String message) {
        super(message);
    }
}
