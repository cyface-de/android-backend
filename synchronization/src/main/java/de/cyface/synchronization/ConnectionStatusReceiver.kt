/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.synchronization

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import de.cyface.synchronization.CyfaceConnectionStatusListener
import de.cyface.utils.Validate.isTrue
import de.cyface.utils.Validate.notNull

/**
 * A [BroadcastReceiver] for the [CyfaceConnectionStatusListener] events. We use this receiver
 * to populate received broadcasts about synchronization events to registered [ConnectionStatusListener]s.
 *
 * @author Armin Schnabel
 * @version 1.1.2
 * @since 2.5.0
 * @param context The [Context] to use to register this [BroadcastReceiver].
 */
class ConnectionStatusReceiver(context: Context) : BroadcastReceiver() {
    /**
     * The interested parties for synchronization events.
     */
    private val connectionStatusListener: MutableCollection<ConnectionStatusListener> =
        HashSet()

    /**
     * Registers this `BroadcastReceiver` to `CyfaceConnectionStatusListener` events.
     * Don't forget to call the `ConnectionStatusReceiver#shutdown()` method at some point
     * in the future.
     */
    init {
        val filter = IntentFilter()
        filter.addAction(CyfaceConnectionStatusListener.SYNC_FINISHED)
        filter.addAction(CyfaceConnectionStatusListener.SYNC_PROGRESS)
        filter.addAction(CyfaceConnectionStatusListener.SYNC_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        notNull(intent.action)
        if (intent.action == CyfaceConnectionStatusListener.SYNC_STARTED) {
            for (listener in connectionStatusListener) {
                listener.onSyncStarted()
            }
        } else if (intent.action == CyfaceConnectionStatusListener.SYNC_FINISHED) {
            for (listener in connectionStatusListener) {
                listener.onSyncFinished()
            }
        } else if (intent.action == CyfaceConnectionStatusListener.SYNC_PROGRESS) {
            val percent = intent.getFloatExtra(BundlesExtrasCodes.SYNC_PERCENTAGE_ID, -1.0f)
            val measurementId =
                intent.getLongExtra(CyfaceConnectionStatusListener.SYNC_MEASUREMENT_ID, -1L)
            isTrue(percent >= 0.0f)
            isTrue(measurementId > 0L)

            for (listener in connectionStatusListener) {
                listener.onProgress(percent, measurementId)
            }
        }
    }

    fun addListener(connectionStatusListener: ConnectionStatusListener) {
        this.connectionStatusListener.add(connectionStatusListener)
    }

    fun removeListener(connectionStatusListener: ConnectionStatusListener) {
        this.connectionStatusListener.remove(connectionStatusListener)
    }

    /**
     * Call this to unregister the [BroadcastReceiver] from the [CyfaceConnectionStatusListener] events.
     */
    fun shutdown(context: Context) {
        context.unregisterReceiver(this)
    }
}
