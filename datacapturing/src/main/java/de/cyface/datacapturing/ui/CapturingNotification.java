package de.cyface.datacapturing.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import de.cyface.datacapturing.R;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * This class represents a notification shown to the user, while data synchronisation is active. The notification is
 * required to make the user aware of the background tracking. It shows up as a small symbol on the upper status bar.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public class CapturingNotification {

    /**
     * The identifier of the notification channel used by the foreground service.
     */
    private static final String CHANNEL_ID = "de.cyface.notification";

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
        String channelId = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channelId = createNotificationChannelIfNotExists(context);
        } else {
            channelId = CHANNEL_ID;
        }

        wrappedNotification = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(context.getText(R.string.notification_title))
                .setContentText(context.getText(R.string.capturing_active)).setSmallIcon(R.drawable.ic_logo_only_c)
                // .setContentIntent(pendingIntent)
                .setTicker(context.getText(R.string.ticker_text)).build();

        return wrappedNotification;
    }

    /**
     * Since Android 8 it is necessary to create a new notification channel for a foreground service notification. To save system resources this should only happen if the channel does not exist. This method does just that.
     *
     * @param context The Android <code>Context</code> to use to create the notification channel.
     * @return The identifier of the created or existing channel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private String createNotificationChannelIfNotExists(final DataCapturingBackgroundService context) {
        final String channelId = CHANNEL_ID;
        final CharSequence channelName = "Cyface";

        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager.getNotificationChannel(channelId) == null) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        return channelId;
    }
}
