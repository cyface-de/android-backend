package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.STOPPED_SUCCESSFULLY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import de.cyface.utils.Validate;

/**
 * Handler for shutdown finished events. Just implement the {@link #shutDownFinished(long)} method with the code you
 * would like to run after the service has been shut down. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.1
 * @since 2.0.0
 * @see DataCapturingService#pause(ShutDownFinishedHandler)
 * @see DataCapturingService#stop(ShutDownFinishedHandler)
 */
public abstract class ShutDownFinishedHandler extends BroadcastReceiver {

    /**
     * This is set to <code>true</code> if either a <code>MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED</code> broadcast
     * has
     * been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code> otherwise.
     */
    private boolean receivedServiceStopped;
    /**
     * An app and device-wide unique identifier. Each service needs to use a different id so that only the
     * service in question receives the expected ping-back.
     */
    private final String serviceStoppedActionId;

    /**
     * @param serviceStoppedActionId An app and device-wide unique identifier. Each service needs to use a different id
     *            so that only the service in question receives the expected ping-back.
     */
    public ShutDownFinishedHandler(@NonNull final String serviceStoppedActionId) {
        this.serviceStoppedActionId = serviceStoppedActionId;
    }

    /**
     * Method called if shutdown has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement, that was captured by the stopped capturing
     *            service.
     */
    public abstract void shutDownFinished(final long measurementIdentifier);

    @Override
    public void onReceive(@NonNull final Context context, @NonNull final Intent intent) {
        Log.v(TAG, "Start/Stop Synchronizer received an intent with action " + intent.getAction() + ".");
        final String action = intent.getAction();
        Validate.notNull("Received broadcast with null action.", action);
        Validate.isTrue(serviceStoppedActionId.equals(intent.getAction()),
                "Received undefined broadcast " + intent.getAction());

        Log.v(TAG, "Received Service stopped broadcast!");
        receivedServiceStopped = true;
        final long measurementIdentifier = intent.getLongExtra(MEASUREMENT_ID, -1);
        // The measurement id should always be set, especially if `STOPPED_SUCCESSFULLY` is false,
        // which happens when stopping a paused measurement [STAD-333].
        // Even if the background service stopped itself (low space warning), the id is set.
        Validate.isTrue(measurementIdentifier != -1, "No measurement identifier provided for stopped service!");
        shutDownFinished(measurementIdentifier);

        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Probably tried to deregister shut down finished broadcast receiver twice.", e);
        }
    }

    /**
     * @return This is set to <code>true</code> if either a <code>MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED</code>
     *         broadcast has
     *         been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code>
     *         otherwise.
     */
    public boolean receivedServiceStopped() {
        return receivedServiceStopped;
    }
}
