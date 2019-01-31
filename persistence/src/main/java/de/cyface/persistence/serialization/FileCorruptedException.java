package de.cyface.persistence.serialization;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> which occurs when a file could not be deserialized because it contains an
 * unexpected number of bytes.
 *
 * @author Armin Schnabel
 * @since 1.0.1
 * @version 3.0.0
 */
public class FileCorruptedException extends Exception {
    /**
     * Creates a new completely initialized <code>FileCorruptedException</code>, providing a detailed explanation
     * about the error to the caller.
     *
     * @param message The explanation of why this error occurred.
     */
    FileCorruptedException(final @NonNull String message) {
        super(message);
    }
}