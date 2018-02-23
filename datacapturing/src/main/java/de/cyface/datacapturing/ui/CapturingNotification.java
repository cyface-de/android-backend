package de.cyface.datacapturing.ui;

import android.app.Notification;
import android.support.v4.app.NotificationCompat;

import de.cyface.datacapturing.R;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * This class represents a notification shown to the user, while data synchronisation is active. The notification is
 * required to make the user aware of the background tracking. It shows up as a small symbol on the upper status bar.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class CapturingNotification {

    /**
     * The Android <code>Notification</code> wrapped by this class.
     */
    private Notification wrappedNotification;

    /**
     * @return An identifier required by the Android system to display the notification.
     */
    public int getNotificationId() {
        return 1;
    }

    /**
     * Provides an Android representation of the <code>CapturingNotification</code>, that can be displayed on screen.
     *
     * @param context The <code>DataCapturingService</code> as context for the new <code>Notification</code>.
     * @return An Android <code>Notification</code> object configured to work as capturing notification.
     */
    public Notification getNotification(DataCapturingBackgroundService context) {
        if (wrappedNotification != null) {
            return wrappedNotification;
        }

        // // Open Activity when the notification is clicked
        // Intent onClickIntent = new Intent();
        // onClickIntent.setComponent(new ComponentName("de.cynav.client", "de.cynav.client.ui.MainActivity"));
        // PendingIntent onClickPendingIntent = PendingIntent.getActivity(
        // context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Cancel capturing when the cancel button is clicked in the notification
        // Intent cancelIntent = new Intent(context, DataCapturingBackgroundService.NotificationActionService.class);
        // cancelIntent.setAction(CANCEL_REQUEST);
        // PendingIntent pendingCancelIntent = PendingIntent.getService(
        // context, 0, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        wrappedNotification = new NotificationCompat.Builder(context, "de.cyface.notification")
                .setContentTitle(context.getText(R.string.notification_title))
                .setContentText(context.getText(R.string.capturing_active)).setSmallIcon(R.drawable.ic_logo_only_c)
                // .setContentIntent(pendingIntent)
                .setTicker(context.getText(R.string.ticker_text)).build();

        return wrappedNotification;
    }
}
