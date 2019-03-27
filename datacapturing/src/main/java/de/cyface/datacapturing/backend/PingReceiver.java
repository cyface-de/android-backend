package de.cyface.datacapturing.backend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.synchronization.BundlesExtrasCodes;
import de.cyface.utils.Validate;

/**
 * A <code>BroadcastReceiver</code> that receives ping messages send to the <code>DataCapturingBackgroundService</code>.
 * This can be used to check if the service is alive.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.2
 * @since 2.0.0
 */
public class PingReceiver extends BroadcastReceiver {

    /**
     * Logging TAG to identify logs associated with the {@link PingReceiver} or {@link PongReceiver}.
     */
    private static final String TAG = PongReceiver.TAG;
    /**
     * A device-wide unique identifier for the application containing this SDK such as
     * {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     * <p>
     * <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    private final String appId;

    /**
     * @param appId A device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    public PingReceiver(@NonNull final String appId) {
        this.appId = appId;
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Validate.notNull(intent.getAction());
        Log.v(TAG, "PingReceiver.onReceive()");

        if (intent.getAction().equals(MessageCodes.getPingActionId(appId))) {
            final Intent pongIntent = new Intent(MessageCodes.getPongActionId(appId));
            if (BuildConfig.DEBUG) {
                final String pingPongIdentifier = intent.getStringExtra(BundlesExtrasCodes.PING_PONG_ID);
                Log.v(TAG, "PingReceiver.onReceive(): Received Ping with identifier " + pingPongIdentifier
                        + ". Sending Pong.");
                pongIntent.putExtra(BundlesExtrasCodes.PING_PONG_ID, pingPongIdentifier);
            }
            context.sendBroadcast(pongIntent);
        }
    }
}
