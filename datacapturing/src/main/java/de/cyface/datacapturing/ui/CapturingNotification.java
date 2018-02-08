package de.cyface.datacapturing.ui;

import android.app.Notification;
import android.support.v4.app.NotificationCompat;

import de.cyface.datacapturing.R;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class CapturingNotification {

    private Notification wrappedNotification;

    public int getNotificationId() {
        return 1;
    }

    public Notification getNotification(DataCapturingBackgroundService context) {
        if(wrappedNotification != null) {
            return wrappedNotification;
        }

//            // Open Activity when the notification is clicked
//            Intent onClickIntent = new Intent();
//            onClickIntent.setComponent(new ComponentName("de.cynav.client", "de.cynav.client.ui.MainActivity"));
//            PendingIntent onClickPendingIntent = PendingIntent.getActivity(
//                    context, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Cancel capturing when the cancel button is clicked in the notification
//            Intent cancelIntent = new Intent(context, DataCapturingBackgroundService.NotificationActionService.class);
//            cancelIntent.setAction(CANCEL_REQUEST);
//            PendingIntent pendingCancelIntent = PendingIntent.getService(
//                    context, 0, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            wrappedNotification = new NotificationCompat.Builder(context, "de.cyface.notification")
                    .setContentTitle(context.getText(R.string.notification_title))
                    .setContentText(context.getText(R.string.capturing_active))
                    .setSmallIcon(R.drawable.ic_logo_only_c)
                    //.setContentIntent(pendingIntent)
                    .setTicker(context.getText(R.string.ticker_text))
                    .build();

        return wrappedNotification;
    }
}
