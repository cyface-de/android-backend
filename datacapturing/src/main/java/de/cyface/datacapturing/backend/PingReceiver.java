package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.ACTION_PING;
import static de.cyface.datacapturing.MessageCodes.ACTION_PONG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A <code>BroadcastReceiver</code> that receives ping messages send to the <code>DataCapturingBackgroundService</code>.
 * This can be used to check if the service is alive.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class PingReceiver extends BroadcastReceiver {

    /**
     * The tag used to identify Logcat messages.
     */
    private static final String TAG = "de.cyface.ping";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_PING)) {
            Log.d(TAG, "Received Ping sending Pong.");
            context.sendBroadcast(new Intent(ACTION_PONG));
        }
    }
}
