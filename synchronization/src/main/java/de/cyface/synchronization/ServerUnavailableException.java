package de.cyface.synchronization;

/**
 * An {@code Exception} thrown when the Cyface server cannot be reached.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.1.0
 */
public class ServerUnavailableException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public ServerUnavailableException(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public ServerUnavailableException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }

    /**
     * @param cause The <code>Exception</code> that caused this one.
     */
    public ServerUnavailableException(final Exception cause) {
        super(cause);
    }
}
