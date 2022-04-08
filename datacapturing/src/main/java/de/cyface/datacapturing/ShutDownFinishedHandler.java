/*
 * Copyright 2021-2022 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;

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
 * @version 3.0.2
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
        Validate.notNull(action, "Received broadcast with null action.");
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
