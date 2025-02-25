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
package de.cyface.datacapturing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.cyface.synchronization.BundlesExtrasCodes

/**
 * Handler for start up finished events. Just implement the [.startUpFinished] method with the code you
 * would like to run after the service has been started. This class is used for asynchronous calls to
 * `DataCapturingService` lifecycle methods.
 *
 * To work properly you must register this object as an Android `BroadcastReceiver`..
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.2
 * @since 2.0.0
 * @param serviceStartedActionId An app-wide unique identifier. Each service needs to use a different id
 * so that only the service in question receives the expected ping-back.
 * @see DataCapturingService.resume
 * @see DataCapturingService.start
 */
abstract class StartUpFinishedHandler(
    private val serviceStartedActionId: String
) : BroadcastReceiver() {
    /**
     * This is set to `true` if a `MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED` broadcast has
     * been received and is `false` otherwise.
     */
    private var receivedServiceStarted = false

    /**
     * Method called if start up has been finished.
     *
     * @param measurementIdentifier The identifier of the measurement that is captured by the
     * started capturing process.
     */
    abstract fun startUpFinished(measurementIdentifier: Long)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        requireNotNull(action)
        require(action == serviceStartedActionId)

        receivedServiceStarted = true
        val measurementIdentifier = intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1L)
        Log.d(TAG, "Received Service started broadcast, mid: $measurementIdentifier")
        check(measurementIdentifier != -1L) {
            "No measurement identifier provided on service started message."
        }
        startUpFinished(measurementIdentifier)

        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            Log.w(
                TAG,
                "Probably tried to deregister start up finished broadcast receiver twice.",
                e
            )
        }
    }

    /**
     * @return This is set to `true` if a `MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED`
     * broadcast has been received and is `false` otherwise.
     */
    fun receivedServiceStarted(): Boolean {
        return receivedServiceStarted
    }

    companion object {
        /**
         * Logging TAG to identify logs associated with the [StartUpFinishedHandler].
         */
        const val TAG: String = Constants.TAG + ".sfh"
    }
}
