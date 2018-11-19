package de.cyface.synchronization;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> thrown each time data synchronisation with a Cyface/Movebis server fails. It provides
 * further information either via a message or another wrapped <code>Exception</code>.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.0.0
 */
public final class SynchronisationException extends Exception {

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, wrapping another
     * <code>Exception</code> from
     * deeper within the system.
     *
     * @param cause The wrapped <code>Exception</code>.
     */
    public SynchronisationException(final @NonNull Exception cause) {
        super(cause);
    }

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, providing a detailed explanation
     * about the
     * error.
     *
     * @param message The message explaining the error condition.
     */
    public SynchronisationException(final @NonNull String message) {
        super(message);
    }

    /**
     * Creates a new completely initialized <code>SynchronisationException</code>, providing a detailed explanation
     * about the
     * error.
     *
     * @param message The message explaining the error condition.
     * @param cause The wrapped <code>Exception</code>.
     */
    public SynchronisationException(final @NonNull String message, final @NonNull Exception cause) {
        super(message, cause);
    }
}
