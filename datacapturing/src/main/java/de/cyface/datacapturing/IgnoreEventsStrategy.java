package de.cyface.datacapturing;

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

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;

/**
 * A default implementation of the {@link EventHandlingStrategy} used if not strategy was provided.
 * This does practically nothing and just allows the strategy to be optional.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.5.0
 */
public final class IgnoreEventsStrategy implements EventHandlingStrategy {

    private final static String CHANNEL_ID = "de.cyface.datacapturing.ignoreeventsstrategy";

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
     * No arguments constructor is redeclared here, since it is overwritten by the constructor required by <code>Parcelable</code>.
     */
    public IgnoreEventsStrategy() {
        // Nothing to do here
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>IgnoreEventsStrategy</code>.
     */
    private IgnoreEventsStrategy(final @NonNull Parcel in) {
        // Nothing to do here.
    }

    @Override
    public void handleSpaceWarning(final DataCapturingBackgroundService dataCapturingBackgroundService) {
        Log.d(BACKGROUND_TAG, "No strategy provided for the handleSpaceWarning event. Ignoring.");
    }

    @Override
    public Notification buildCapturingNotification(final @NonNull DataCapturingBackgroundService context) {
        Validate.notNull("No context provided!", context);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && notificationManager.getNotificationChannel(CHANNEL_ID)==null) {
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Cyface Data Capturing", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Cyface")
                .setSmallIcon(R.drawable.ic_movebis_notification)
                .setContentText("Running Cyface Data Capturing")
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
        return notification;
    }

    @Override
    public int getCapturingNotificationId() {
        return 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }
}
