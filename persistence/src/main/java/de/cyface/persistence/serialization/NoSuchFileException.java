package de.cyface.persistence.serialization;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> which occurs every time someone wants to load a file which does not exist.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public final class NoSuchFileException extends Exception {

    /**
     * Creates a new completely initialized {@link NoSuchFileException}, providing a detailed explanation
     * about the error to the caller.
     *
     * @param message The explanation of why this error occurred.
     */
    public NoSuchFileException(@NonNull final String message) {
        super(message);
    }
}
