package de.cyface.synchronization;

/**
 * An {@code Exception} thrown when the server returns an 400 error. The {@code Exception} contains
 * all the details available and relevant to diagnose the error case.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
 */
final class BadRequestException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    BadRequestException(final String detailedMessage) {
        this(detailedMessage, null);

    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    @SuppressWarnings("WeakerAccess") // May be used in future
    BadRequestException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);

    }
}
