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
package de.cyface.datacapturing.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.cyface.datacapturing.BuildConfig
import de.cyface.datacapturing.PongReceiver
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.utils.Validate.notNull

/**
 * A `BroadcastReceiver` that receives ping messages send to the `DataCapturingBackgroundService`.
 * This can be used to check if the service is alive.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.2
 * @since 2.0.0
 */
class PingReceiver
/**
 * @param pingActionId An app and device-wide unique identifier. Each service of this app needs to use
 * a different id so that only the service in question "replies" to the ping request.
 * @param pongActionId An app and device-wide unique identifier. Each service of this app needs to use
 * a different id so that only the service in question "replies" to the ping request.
 */(
    /**
     * An app-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private val pingActionId: String,
    /**
     * An app-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private val pongActionId: String
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        notNull(intent.action)
        Log.v(TAG, "PingReceiver.onReceive()")

        if (intent.action == pingActionId) {
            val pongIntent = Intent(pongActionId)
            // Binding the intent to the package of the app which runs this SDK [DAT-1509].
            intent.setPackage(context.packageName)
            if (BuildConfig.DEBUG) {
                val pingPongIdentifier = intent.getStringExtra(BundlesExtrasCodes.PING_PONG_ID)
                Log.v(
                    TAG,
                    "PingReceiver.onReceive(): Received Ping with identifier " + pingPongIdentifier
                            + ". Sending Pong."
                )
                pongIntent.putExtra(BundlesExtrasCodes.PING_PONG_ID, pingPongIdentifier)
            }
            context.sendBroadcast(pongIntent)
        }
    }

    companion object {
        /**
         * Logging TAG to identify logs associated with the [PingReceiver] or [PongReceiver].
         */
        private const val TAG = PongReceiver.TAG
    }
}
