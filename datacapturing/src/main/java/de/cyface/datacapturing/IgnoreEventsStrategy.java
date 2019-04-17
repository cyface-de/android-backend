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
package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.utils.Validate;

/**
 * A default implementation of the {@link EventHandlingStrategy} used if not strategy was provided.
 * For most events it does practically nothing and just allows the strategy to be optional.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 2.5.0
 */
public final class IgnoreEventsStrategy implements EventHandlingStrategy {

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<IgnoreEventsStrategy> CREATOR = new Creator<IgnoreEventsStrategy>() {
        @Override
        public IgnoreEventsStrategy createFromParcel(final Parcel in) {
            return new IgnoreEventsStrategy(in);
        }

        @Override
        public IgnoreEventsStrategy[] newArray(final int size) {
            return new IgnoreEventsStrategy[size];
        }
    };

    /**
     * No arguments constructor is redeclared here, since it is overwritten by the constructor required by
     * <code>Parcelable</code>.
     */
    public IgnoreEventsStrategy() {
        // Nothing to do here
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEventsStrategy</code>.
     */
    private IgnoreEventsStrategy(@SuppressWarnings("unused") final @NonNull Parcel in) {
        // Nothing to do here.
    }

    @Override
    public void handleSpaceWarning(@NonNull final DataCapturingBackgroundService dataCapturingBackgroundService) {
        Log.d(BACKGROUND_TAG, "No strategy provided for the handleSpaceWarning event. Ignoring.");
    }

    @Override
    @NonNull
    public Notification buildCapturingNotification(@NonNull final DataCapturingBackgroundService context) {
        Validate.notNull("No context provided!", context);

        // The NotificationChannel settings are cached so you need to temporarily change the channel id for testing
        final String channelId = context.getString(R.string.cyface_notification_channel_id);
        final NotificationManager notificationManager = (NotificationManager)context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Validate.notNull(notificationManager);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && notificationManager.getNotificationChannel(channelId) == null) {
            final NotificationChannel channel = new NotificationChannel(channelId, "Cyface Data Capturing",
                    NotificationManager.IMPORTANCE_LOW); // to disable vibration
            notificationManager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(context, channelId)
                .setContentTitle(context.getString(R.string.notification_title))
                .setSmallIcon(R.drawable.ic_hourglass_empty_black_24dp)
                .setContentText(context.getString(R.string.notification_text)).setOngoing(true).setAutoCancel(false)
                .build();
    }

    @Override
    public int describeContents() {
        // Nothing to do
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        // Nothing to do
    }
}
