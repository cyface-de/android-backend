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
package de.cyface.datacapturing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.utils.Validate.isTrue
import de.cyface.utils.Validate.notNull

/**
 * Handler for shutdown finished events. Just implement the [.shutDownFinished] method with the code you
 * would like to run after the service has been shut down. This class is used for asynchronous calls to
 * `DataCapturingService` lifecycle methods.
 *
 *
 * To work properly you must register this object as an Android `BroadcastReceiver`.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.3
 * @since 2.0.0
 * @see DataCapturingService.pause
 * @see DataCapturingService.stop
 */
abstract class ShutDownFinishedHandler
/**
 * Constructs a fully initialized instance of this class.
 *
 * @param serviceStoppedActionId An app-wide unique identifier. Each service needs to use a different id
 * so that only the service in question receives the expected ping-back.
 */(
    /**
     * An app-wide unique identifier. Each service needs to use a different id so that only the
     * service in question receives the expected ping-back.
     */
    private val serviceStoppedActionId: String
) : BroadcastReceiver() {
    /**
     * This is set to `true` if either a `MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED` broadcast
     * has been received or a `MessageCodes.SERVICE_STOPPED` was issued. It is `false` otherwise.
     */
    private var receivedServiceStopped = false

    /**
     * Method called if shutdown has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement, that was captured by the stopped capturing
     * service.
     */
    abstract fun shutDownFinished(measurementIdentifier: Long)

    override fun onReceive(context: Context, intent: Intent) {
        Log.v(
            Constants.TAG,
            "Start/Stop Synchronizer received an intent with action " + intent.action + "."
        )
        val action = intent.action
        notNull(action, "Received broadcast with null action.")
        isTrue(
            serviceStoppedActionId == intent.action,
            "Received undefined broadcast " + intent.action
        )

        Log.v(Constants.TAG, "Received Service stopped broadcast!")
        receivedServiceStopped = true
        val measurementIdentifier = intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1)
        // The measurement id should always be set, especially if `STOPPED_SUCCESSFULLY` is false,
        // which happens when stopping a paused measurement [STAD-333].
        // Even if the background service stopped itself (low space warning), the id is set.
        isTrue(
            measurementIdentifier != -1L,
            "No measurement identifier provided for stopped service!"
        )
        shutDownFinished(measurementIdentifier)

        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            Log.w(
                Constants.TAG,
                "Probably tried to deregister shut down finished broadcast receiver twice.",
                e
            )
        }
    }

    /**
     * @return This is set to `true` if either a `MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED`
     * broadcast has
     * been received or a `MessageCodes.SERVICE_STOPPED` was issued. It is `false`
     * otherwise.
     */
    fun receivedServiceStopped(): Boolean {
        return receivedServiceStopped
    }
}
