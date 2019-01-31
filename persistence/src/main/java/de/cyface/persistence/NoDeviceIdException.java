package de.cyface.persistence;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> which occurs every time someone wants to load the device id from the
 * {@link PersistenceLayer} when there is no such entry in the database.
 *
 * @author Armin Schnabel
 * @since 3.0.0
 * @version 1.0.0
 */
public final class NoDeviceIdException extends Exception {
    /**
     * Creates a new completely initialized {@link NoDeviceIdException}, providing a detailed explanation
     * about the error to the caller.
     *
     * @param message The explanation of why this error occurred.
     */
    public NoDeviceIdException(final @NonNull String message) {
        super(message);
    }
}
