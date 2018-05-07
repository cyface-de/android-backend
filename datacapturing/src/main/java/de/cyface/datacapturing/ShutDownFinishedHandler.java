package de.cyface.datacapturing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Handler for shutdown finished events. Just implement the {@link #shutDownFinished()} method with the code you would
 * like to run after the service has been shut down. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 * @see DataCapturingService#pauseAsync(ShutDownFinishedHandler)
 * @see DataCapturingService#stopAsync(ShutDownFinishedHandler)
 */
public abstract class ShutDownFinishedHandler extends BroadcastReceiver {
    /**
     * The tag used to identify Logcat messages from objects of this class.
     */
    private static final String TAG = "de.cyface.capturing";

    /**
     * This is set to <code>true</code> if either a <code>MessageCodes.BROADCAST_SERVICE_STOPPED</code> broadcast has
     * been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code> otherwise.
     */
    private boolean receivedServiceStopped;

    /**
     * Method called if shutdown has been finished.
     */
    abstract public void shutDownFinished();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Start/Stop Synchronizer received an intent with action " + intent.getAction() + ".");
        if (intent.getAction() == null) {
            throw new IllegalStateException("Received broadcast with null action.");
        }
        switch (intent.getAction()) {
            case MessageCodes.BROADCAST_SERVICE_STOPPED:
                Log.v(TAG, "Received Service stopped broadcast!");
                receivedServiceStopped = true;
                shutDownFinished();
                break;
            default:
                throw new IllegalStateException("Received undefined broadcast " + intent.getAction());
        }

        context.unregisterReceiver(this);

    }

    /**
     * @return This is set to <code>true</code> if either a <code>MessageCodes.BROADCAST_SERVICE_STOPPED</code>
     *         broadcast has
     *         been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code>
     *         otherwise.
     */
    public boolean receivedServiceStopped() {
        return receivedServiceStopped;
    }
}
