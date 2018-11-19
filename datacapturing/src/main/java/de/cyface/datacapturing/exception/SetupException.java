package de.cyface.datacapturing.exception;

import androidx.annotation.NonNull;

/**
 * An <code>Exception</code> that is thrown each time setting up the <code>DataCapturingService</code> fails.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class SetupException extends Exception {
    /**
     * Creates a new completely initialized <code>SetupException</code>, wrapping another <code>Exception</code> from
     * deeper within the system.
     *
     * @param e The wrapped <code>Exception</code>.
     */
    public SetupException(final @NonNull Exception e) {
        super(e);
    }

    /**
     * Creates a new completely initialized <code>SetupException</code>, providing a detailed explanation about the
     * error.
     *
     * @param message The message explaining the error condition.
     */
    public SetupException(final @NonNull String message) {
        super(message);
    }
}
