package de.cyface.datacapturing.exception;

import android.support.annotation.NonNull;

/**
 * A wrapper <code>Exception</code> for code thrown by the <code>DataCapturingService</code> facade.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class DataCapturingException extends Exception {
    /**
     * Creates a new completely initialized <code>DataCapturingException</code>.
     *
     * @param message The message to show as a detailed explanation for this <code>Exception</code>.
     */
    public DataCapturingException(final @NonNull String message) {
        super(message);
    }
}
