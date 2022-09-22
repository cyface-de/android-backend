/*
 * Copyright 2017-2022 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.PongReceiver;
import de.cyface.synchronization.BundlesExtrasCodes;
import de.cyface.utils.Validate;

/**
 * A <code>BroadcastReceiver</code> that receives ping messages send to the <code>DataCapturingBackgroundService</code>.
 * This can be used to check if the service is alive.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.2
 * @since 2.0.0
 */
public class PingReceiver extends BroadcastReceiver {

    /**
     * Logging TAG to identify logs associated with the {@link PingReceiver} or {@link PongReceiver}.
     */
    private static final String TAG = PongReceiver.TAG;
    /**
     * An app-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private final String pingActionId;
    /**
     * An app-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private final String pongActionId;

    /**
     * @param pingActionId An app and device-wide unique identifier. Each service of this app needs to use
     *            a different id so that only the service in question "replies" to the ping request.
     * @param pongActionId An app and device-wide unique identifier. Each service of this app needs to use
     *            a different id so that only the service in question "replies" to the ping request.
     */
    public PingReceiver(@NonNull final String pingActionId, @NonNull final String pongActionId) {
        this.pingActionId = pingActionId;
        this.pongActionId = pongActionId;
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Validate.notNull(intent.getAction());
        Log.v(TAG, "PingReceiver.onReceive()");

        if (intent.getAction().equals(pingActionId)) {
            final Intent pongIntent = new Intent(pongActionId);
            // Binding the intent to the package of the app which runs this SDK [DAT-1509].
            intent.setPackage(context.getPackageName());
            if (BuildConfig.DEBUG) {
                final String pingPongIdentifier = intent.getStringExtra(BundlesExtrasCodes.PING_PONG_ID);
                Log.v(TAG, "PingReceiver.onReceive(): Received Ping with identifier " + pingPongIdentifier
                        + ". Sending Pong.");
                pongIntent.putExtra(BundlesExtrasCodes.PING_PONG_ID, pingPongIdentifier);
            }
            context.sendBroadcast(pongIntent);
        }
    }
}
