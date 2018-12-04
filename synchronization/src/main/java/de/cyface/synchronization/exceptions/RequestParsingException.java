package de.cyface.synchronization.exceptions;

/**
 * An {@code Exception} thrown when the request to the Cyface server could not be generated.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.1.0
 */
public class RequestParsingException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public RequestParsingException(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public RequestParsingException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }
}
