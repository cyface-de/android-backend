package de.cyface.datacapturing;

import static de.cyface.datacapturing.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.datacapturing.Constants.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import de.cyface.datacapturing.model.Vehicle;

/**
 * Handler for start up finished events. Just implement the {@link #startUpFinished(long)} method with the code you
 * would like to run after the service has been started. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>..
 *
 * @author Klemens Muthmann
 * @version 2.0.3
 * @since 2.0.0
 * @see DataCapturingService#resumeAsync(StartUpFinishedHandler)
 * @see DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler extends BroadcastReceiver {

    /**
     * This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has been
     * received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;

    /**
     * Method called if start up has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement that is captured by the started capturing process.
     */
    public abstract void startUpFinished(final long measurementIdentifier);

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Log.v(TAG, "Start/Stop Synchronizer received an intent with action " + intent.getAction() + ".");
        if (intent.getAction() == null) {
            throw new IllegalStateException("Received broadcast with null action.");
        }
        switch (intent.getAction()) {
            case MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED:
                Log.v(TAG, "Received Service started broadcast!");
                receivedServiceStarted = true;
                long measurementIdentifier = intent.getLongExtra(MEASUREMENT_ID, -1L);
                if (measurementIdentifier == -1) {
                    throw new IllegalStateException("No measurement identifier provided on service started message.");
                }
                startUpFinished(measurementIdentifier);
                break;
            default:
                throw new IllegalStateException("Received undefined broadcast " + intent.getAction());
        }
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Probably tried to deregister start up finished broadcast receiver twice.", e);
        }
    }

    /**
     * @return This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has
     *         been
     *         received and is <code>false</code> otherwise.
     */
    public boolean receivedServiceStarted() {
        return receivedServiceStarted;
    }
}
