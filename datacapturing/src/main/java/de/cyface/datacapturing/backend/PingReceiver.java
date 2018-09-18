package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.ACTION_PING;
import static de.cyface.datacapturing.MessageCodes.ACTION_PONG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.BundlesExtrasCodes;
import de.cyface.utils.Validate;

/**
 * A <code>BroadcastReceiver</code> that receives ping messages send to the <code>DataCapturingBackgroundService</code>.
 * This can be used to check if the service is alive.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.0
 */
public class PingReceiver extends BroadcastReceiver {

    /**
     * The tag used to identify Logcat messages.
     */
    private static final String TAG = "de.cyface.background";

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Validate.notNull(intent.getAction());
        if (intent.getAction().equals(ACTION_PING)) {
            Intent pongIntent = new Intent(ACTION_PONG);
            if(BuildConfig.DEBUG) {
                String pingPongIdentifier = intent.getStringExtra(BundlesExtrasCodes.PING_PONG_ID);
                Log.d(TAG, "PingReceiver.onReceive(): Received Ping with identifier "+ pingPongIdentifier +". Sending Pong.");
                pongIntent.putExtra(BundlesExtrasCodes.PING_PONG_ID, pingPongIdentifier);
            }
            context.sendBroadcast(pongIntent);
        }
    }
}
