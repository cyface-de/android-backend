package de.cyface.synchronization;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Measurement;

/**
 * An {@code Exception} thrown when the user is authorized but has no permissions to posts {@link Measurement}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class ForbiddenException extends Exception {

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     */
    ForbiddenException(@NonNull final String detailedMessage) {
        super(detailedMessage);
    }

    /**
     * @param detailedMessage A more detailed message explaining the context for this {@code Exception}.
     * @param cause The {@code Exception} that caused this one.
     */
    @SuppressWarnings("unused") // May be used in the future
    public ForbiddenException(@NonNull final String detailedMessage, @NonNull final Exception cause) {
        super(detailedMessage, cause);
    }
}
