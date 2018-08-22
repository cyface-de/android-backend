package de.cyface.synchronization;

/**
 * An {@code Exception} thrown when a database operation failed.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.1.0
 */
public class DatabaseException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     */
    public DatabaseException(final String detailedMessage) {
        this(detailedMessage, null);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this <code>Exception</code>.
     * @param cause The <code>Exception</code> that caused this one.
     */
    public DatabaseException(final String detailedMessage, final Exception cause) {
        super(detailedMessage, cause);
    }
}
