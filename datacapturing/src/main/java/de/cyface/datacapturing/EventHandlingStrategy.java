package de.cyface.datacapturing;

import android.content.Intent;
import android.os.Parcelable;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * Interface for strategies to respond to events triggered by the {@link DataCapturingBackgroundService}.
 * E.g.: Show a notification when little space is available and stop the capturing.
 * Must be {@link Parcelable} to be passed from the {@link DataCapturingService} via {@link Intent}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.5.0
 */
public interface EventHandlingStrategy extends Parcelable {

    /**
     * Implement a strategy to react to a low space warning.
     */
    void handleSpaceWarning(final DataCapturingService dataCapturingService);
}
