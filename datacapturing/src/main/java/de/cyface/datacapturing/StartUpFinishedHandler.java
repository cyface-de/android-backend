package de.cyface.datacapturing;

import static de.cyface.datacapturing.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.datacapturing.Constants.TAG;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.ui.Reason;

/**
 * Handler for start up finished events. Just implement the {@link #startUpFinished(long)} method with the code you
 * would like to run after the service has been started. This class is used for asynchronous calls to
 * <code>DataCapturingService</code> lifecycle methods.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code>..
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 * @see DataCapturingService#resumeAsync(StartUpFinishedHandler)
 * @see DataCapturingService#startAsync(DataCapturingListener, Vehicle, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler implements DataCapturingListener {

    /**
     * This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has
     * been received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;

    /**
     * Method called if start up has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement that is captured by the started capturing process.
     */
    public abstract void startUpFinished(final long measurementIdentifier);

    @Override
    public void onCapturingStarted(final long measurementIdentifier) {
        Log.v(TAG, "Received Service started message, mid: " + measurementIdentifier);
        receivedServiceStarted = true;
        startUpFinished(measurementIdentifier);
        try {
            //unregisterReceiver(this); FIXME
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

    @Override
    public void onFixAcquired() {
        // Nothing to do
    }

    @Override
    public void onFixLost() {
        // Nothing to do
    }

    @Override
    public void onNewGeoLocationAcquired(GeoLocation position) {
        // Nothing to do
    }

    @Override
    public void onNewSensorDataAcquired(CapturedData data) {
        // Nothing to do
    }

    @Override
    public void onLowDiskSpace(DiskConsumption allocation) {
        // Nothing to do
    }

    @Override
    public void onSynchronizationSuccessful() {
        // Nothing to do
    }

    @Override
    public void onErrorState(Exception e) {
        // Nothing to do
    }

    @Override
    public boolean onRequiresPermission(String permission, Reason reason) {
        return false; // Nothing to do
    }

    @Override
    public void onCapturingStopped() {
        // Nothing to do
    }
}
