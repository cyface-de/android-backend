package de.cyface.synchronization;

import androidx.annotation.NonNull;

/**
 * An {@code Exception} thrown by the server when the Multipart request is erroneous, e.g. when there is not exactly one
 * file or a syntax error.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class EntityNotParsableException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    EntityNotParsableException(final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public EntityNotParsableException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);
    }
}
