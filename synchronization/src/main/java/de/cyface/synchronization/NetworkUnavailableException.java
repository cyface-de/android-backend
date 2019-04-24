package de.cyface.synchronization;

/**
 * An {@code Exception} thrown when the network used for transmission is no longer available.
 * <p>
 * This is usually indicated by OkHttp via {@code SSLException}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class NetworkUnavailableException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public NetworkUnavailableException(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public NetworkUnavailableException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }
}
