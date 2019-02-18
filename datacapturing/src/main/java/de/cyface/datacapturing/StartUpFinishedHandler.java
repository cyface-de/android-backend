package de.cyface.datacapturing;

import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.datacapturing.Constants.TAG;

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
 * @version 3.0.0
 * @since 2.0.0
 * @see DataCapturingService#resume(StartUpFinishedHandler)
 * @see DataCapturingService#start(DataCapturingListener, Vehicle, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler extends BroadcastReceiver {

    /**
     * This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has
     * been received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;
    /**
     * The device id is used to ensure unique broadcast ids between different SDK implementing apps.
     * For more details see {@link MessageCodes#getServiceStartedActionId(String)}
     */
    private final String deviceId;

    /**
     * @param deviceId The device id used to make global broadcast ids unique
     */
    public StartUpFinishedHandler(String deviceId) {
        this.deviceId = deviceId;
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
        if (intent.getAction().equals(MessageCodes.getServiceStartedActionId(deviceId))) {
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
