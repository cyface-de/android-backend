package de.cyface.datacapturing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.cyface.datacapturing.model.Vehicle;

/**
 * Handler for start up finished events. Just implement the {@link #startUpFinished()} method with the code you would
 * like to run after the service has been started. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>..
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 * @see DataCapturingService#resumeAsync(StartUpFinishedHandler)
 * @see DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler extends BroadcastReceiver {
    /**
     * The tag used to identify Logcat messages from objects of this class.
     */
    private static final String TAG = "de.cyface.capturing";
    /**
     * This is set to <code>true</code> if a <code>MessageCodes.BROADCAST_SERVICE_STARTED</code> broadcast has been
     * received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;

    /**
     * Method called if start up has been finished.
     */
    abstract public void startUpFinished();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Start/Stop Synchronizer received an intent with action " + intent.getAction() + ".");
        if (intent.getAction() == null) {
            throw new IllegalStateException("Received broadcast with null action.");
        }
        switch (intent.getAction()) {
            case MessageCodes.BROADCAST_SERVICE_STARTED:
                Log.v(TAG, "Received Service started broadcast!");
                receivedServiceStarted = true;
                startUpFinished();
                break;
            default:
                throw new IllegalStateException("Received undefined broadcast " + intent.getAction());
        }

        context.unregisterReceiver(this);
    }

    /**
     * @return This is set to <code>true</code> if a <code>MessageCodes.BROADCAST_SERVICE_STARTED</code> broadcast has been
     * received and is <code>false</code> otherwise.
     */
    public boolean receivedServiceStarted() {
        return receivedServiceStarted;
    }
}
