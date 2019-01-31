package de.cyface.persistence;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * An <code>Exception</code> which occurs every time someone wants to execute an action on a
 * {@link Measurement}, that does not exist on this device.
 *
 * @author Klemens Muthmann
 * @since 2.0.1
 * @version 1.0.1
 */
public final class NoSuchMeasurementException extends Exception {
    /**
     * Creates a new completely initialized <code>NoSuchMeasurementException</code>, providing a detailed explanation
     * about the error to the caller.
     * 
     * @param message The explanation of why this error occurred.
     */
    public NoSuchMeasurementException(final @NonNull String message) {
        super(message);
    }
}
