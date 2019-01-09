package de.cyface.datacapturing;

import android.app.Notification;
import android.content.Intent;
import android.os.Parcelable;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * Interface for strategies to respond to events triggered by the {@link DataCapturingBackgroundService}.
 * E.g.: Show a notification when little space is available and stop the capturing.
 * Must be {@link Parcelable} to be passed from the {@link DataCapturingService} via {@link Intent}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 2.5.0
 */
public interface EventHandlingStrategy extends Parcelable {

    /**
     * Implement a strategy to react to a low space warning.
     *
     * @param dataCapturingBackgroundService A reference to the background service to allow operations
     *            on it like stopping the capturing.
     */
    void handleSpaceWarning(final DataCapturingBackgroundService dataCapturingBackgroundService);

    /**
     * Provides an Android representation of the <code>CapturingNotification</code>, that can be displayed on screen.
     *
     * @param context The <code>DataCapturingService</code> as context for the new <code>Notification</code>.
     * @return An Android <code>Notification</code> object configured to work as capturing notification or
     *         <code>null</code>. If <code>null</code> is returned the data capturing service is not started as a
     *         foreground service and will be killed without further notice by newer Android systems.
     */
    Notification buildCapturingNotification(final DataCapturingBackgroundService context);

    /**
     * @return The application wide unique identifier of the notification shown during data capturing. The implementing
     *         code needs to make sure, that this is unique. This is condition is not checked by the Cyface SDK.
     */
    int getCapturingNotificationId();
}
