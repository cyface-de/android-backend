/*
 * Copyright 2018-2025 Cyface GmbH
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

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import de.cyface.synchronization.WiFiSurveyor.Companion.TAG

/**
 * This callback handles status changes of the [Network] connectivity, e.g. to determine if synchronization should
 * be enabled depending on the `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` capabilities of the
 * newly connected network.
 *
 * @author Armin Schnabel
 * @version 3.0.1
 * @since 3.0.0
 * @property surveyor The object which registered this callback, to access some of it's methods.
 */
class NetworkCallback internal constructor(
    private val surveyor: WiFiSurveyor
) : ConnectivityManager.NetworkCallback() {
    override fun onLost(network: Network) {
        // This was required for < MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG(= Build.VERSION_CODES.O)
        // or else we were not informed about a lost wifi connection on old Android versions,
        // e.g. on Android 6.0.1 (MOV-650, and maybe MOV-645). This might not be necessary anymore.

        Log.v(TAG, "NetworkCallback.onLost: setConnected to false.")
        surveyor.setConnected(false)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        // Ensures event is only triggered for un-metered connections (syncOnUnMeteredNetworkOnly)
        if (surveyor.isSyncOnUnMeteredNetworkOnly()) {
            require(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        }

        // Syncable ("not metered") filter is already included
        val isConnected = surveyor.isConnected
        val isConnectedToSyncableNetwork = surveyor.isConnectedToSyncableNetwork
        val syncableConnectionLost = isConnected && !isConnectedToSyncableNetwork
        val syncableConnectionEstablished = !isConnected && isConnectedToSyncableNetwork
        if (syncableConnectionEstablished) {
            Log.v(TAG, "onCapabilitiesChanged.connectionEstablished: setConnected to true")
            surveyor.setConnected(true)
        } else if (syncableConnectionLost) {
            // This should not be necessary as we have onLost() but we keep it as a safety net for now
            Log.v(TAG, "onCapabilitiesChanged.connectionLost: setConnected to false.")
            surveyor.setConnected(false)
        }
    }
}
