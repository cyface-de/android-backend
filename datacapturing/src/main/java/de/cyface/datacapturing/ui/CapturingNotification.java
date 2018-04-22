package de.cyface.datacapturing.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.R;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * This class represents a notification shown to the user, while data synchronisation is active. The notification is
 * required to make the user aware of the background tracking. It shows up as a small symbol on the upper status bar.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
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
        String channelId = context.getText(BuildConfig.NOTIFICATION_CHANNEL).toString();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            createNotificationChannelIfNotExists(context, channelId);
        }

        wrappedNotification = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(context.getText(BuildConfig.NOTIFICATION_TITLE))
                .setContentText(context.getText(BuildConfig.NOTIFICATION_TEXT)).setSmallIcon(BuildConfig.NOTIFICATION_LOGO)
                // .setContentIntent(pendingIntent)
                .setTicker(context.getText(R.string.ticker_text)).build();

        return wrappedNotification;
    }

    /**
     * Since Android 8 it is necessary to create a new notification channel for a foreground service notification. To save system resources this should only happen if the channel does not exist. This method does just that.
     *
     * @param context The Android <code>Context</code> to use to create the notification channel.
     * @param channelId The identifier of the created or existing channel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannelIfNotExists(final @NonNull DataCapturingBackgroundService context, final @NonNull String channelId) {
        final CharSequence channelName = "Cyface";

        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) {
            throw new IllegalStateException("Manager for service notifications not available.");
        }

        if (manager.getNotificationChannel(channelId) == null) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }
}
