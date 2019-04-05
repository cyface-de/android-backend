package de.cyface.synchronization;

import androidx.annotation.NonNull;

/**
 * An {@code Exception} thrown when the server reports an internal server error.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class InternalServerErrorException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    InternalServerErrorException(@NonNull final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public InternalServerErrorException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);
    }
}
