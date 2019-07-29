/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing.backend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import de.cyface.datacapturing.R;
import de.cyface.utils.Validate;

/**
 * Provides a placeholder notification for the {@link DataCapturingBackgroundService} to show until it gets the real
 * notification from the calling code.
 * <p>
 * This workaround is required since the {@code DataCapturingBackgroundService} is required to call
 * {@link android.app.Service#startForeground(int, Notification)} within the first five seconds of its existence. In
 * this time it first calls {@link Service#onCreate()} and then {@link Service#onStartCommand(Intent, int, int)}, but
 * only the later call receives an {@link Intent} and thus can recieve information about the notification to show.
 * However especially on slow devices it seems that {@link Service#onStartCommand(Intent, int, int)} is not reliably
 * called within the first five seconds, which means we need to call {@link Service#startForeground(int, Notification)}
 * from {@link Service#onCreate()}. Since we have can not get information about the notification to show in that method
 * we display this placeholder initially and substitute it by the real notification as soon as possible. On most devices
 * the user should not even notice that step.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 */
public final class PlaceholderNotificationBuilder {
    /**
     * Creates the placeholder notification for the provided {@code Context}.
     *
     * @param context The Android {@code Context} to create the notification for.
     * @return The notification to show, while the {@link DataCapturingBackgroundService} is loading.
     */
    public static Notification build(final @NonNull Context context) {
        Validate.notNull("No context provided!", context);
        final String channelId = context.getString(R.string.cyface_notification_channel_id);

        NotificationManager notificationManager = (NotificationManager)context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && notificationManager.getNotificationChannel(channelId) == null) {
            final NotificationChannel channel = new NotificationChannel(channelId,
                    context.getString(R.string.notification_text), NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        builder.setContentTitle(context.getString(R.string.notification_title));
                builder.setSmallIcon(R.drawable.ic_hourglass_empty_black_24dp);
                builder.setContentText(context.getString(R.string.notification_text));
                builder.setOngoing(true);
                builder.setAutoCancel(false);
        return builder.build();
    }
}
