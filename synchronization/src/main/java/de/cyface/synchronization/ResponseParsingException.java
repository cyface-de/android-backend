package de.cyface.synchronization;

/**
 * An {@code Exception} thrown when the Cyface server response could not be parsed.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.1.0
 */
public class ResponseParsingException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public ResponseParsingException(final String detailedMessage) {
        this(detailedMessage, null);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public ResponseParsingException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }
}
