package de.cyface.utils;

/**
 * A runtime exception thrown when a pre- or post condition check fails.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
 */
public class ValidationException extends RuntimeException {
    /**
     * Creates a new completely initialized {@code ValidationException} with a message explaining further details about
     * the reasons of this exception.
     *
     * @param detailMessage Provides a detailed explanation about this exception.
     */
    public ValidationException(final String detailMessage) {
        super(detailMessage);
    }
}
