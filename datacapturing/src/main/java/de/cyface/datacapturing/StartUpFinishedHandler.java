/*
 * Copyright 2017-2021 Cyface GmbH
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

import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.Modality;
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
 * @version 5.0.2
 * @since 2.0.0
 * @see DataCapturingService#resume(StartUpFinishedHandler)
 * @see DataCapturingService#start(Modality, StartUpFinishedHandler)
 */
public abstract class StartUpFinishedHandler extends BroadcastReceiver {

    /**
     * Logging TAG to identify logs associated with the {@link StartUpFinishedHandler}.
     */
    public static final String TAG = Constants.TAG + ".sfh";
    /**
     * This is set to <code>true</code> if a <code>MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED</code> broadcast has
     * been received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted = false;
    /**
     * An app and device-wide unique identifier. Each service needs to use a different id so that only the
     * service in question receives the expected ping-back.
     */
    private final String serviceStartedActionId;

    /**
     */
    public StartUpFinishedHandler(@NonNull final String serviceStartedActionId) {
        this.serviceStartedActionId = serviceStartedActionId;
    }

    /**
     * Method called if start up has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement that is captured by the started capturing process.
     */
    public abstract void startUpFinished(final long measurementIdentifier);

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        final String action = intent.getAction();
        Validate.notNull(action);
        Validate.isTrue(action.equals(serviceStartedActionId));

        receivedServiceStarted = true;
        final long measurementIdentifier = intent.getLongExtra(MEASUREMENT_ID, -1L);
        Log.d(TAG, "Received Service started broadcast, mid: " + measurementIdentifier);
        if (measurementIdentifier == -1) {
            throw new IllegalStateException("No measurement identifier provided on service started message.");
        }
        startUpFinished(measurementIdentifier);

        try {
            context.unregisterReceiver(this);
        } catch (final IllegalArgumentException e) {
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
