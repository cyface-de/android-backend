package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.Vehicle;
import de.cyface.utils.Validate;

/**
 * Handler for start up finished events. Just implement the {@link #startUpFinished(long)} method with the code you
 * would like to run after the service has been started. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>..
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.1
 * @since 2.0.0
 * @see DataCapturingService#resume(StartUpFinishedHandler)
 * @see DataCapturingService#start(Vehicle, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler extends BroadcastReceiver {

    /**
     * This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has
     * been received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;
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
    public StartUpFinishedHandler(@NonNull final String appId) {
        this.appId = appId;
    }

    /**
     * Method called if start up has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement that is captured by the started capturing process.
     */
    public abstract void startUpFinished(final long measurementIdentifier);

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        Validate.notNull(intent.getAction());
        if (intent.getAction().equals(MessageCodes.getServiceStartedActionId(appId))) {
            receivedServiceStarted = true;
            long measurementIdentifier = intent.getLongExtra(MEASUREMENT_ID, -1L);
            Log.v(TAG, "Received Service started broadcast, mid: " + measurementIdentifier);
            if (measurementIdentifier == -1) {
                throw new IllegalStateException("No measurement identifier provided on service started message.");
            }
            startUpFinished(measurementIdentifier);
        }

        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Probably tried to deregister start up finished broadcast receiver twice.", e);
        }
    }

    /**
     * @return This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code>
     *         broadcast has been received and is <code>false</code> otherwise.
     */
    public boolean receivedServiceStarted() {
        return receivedServiceStarted;
    }
}
