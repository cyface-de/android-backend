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

        // Trust the (network, capabilities) we were handed, do NOT re-query
        // ConnectivityManager.activeNetwork here. activeNetwork can still be null (or point to the
        // previous default) while Android is handing off the default route, so the old code could
        // miss the transition entirely and stay "disconnected" until another callback fires. With
        // weak / flapping Wi-Fi on kiosk devices (BIK-445, Digural trucks) the next callback may
        // never arrive, leaving sync disabled for hours.
        val isSyncableNow = isSyncable(capabilities)
        val isConnected = surveyor.isConnected
        if (isSyncableNow && !isConnected) {
            Log.v(TAG, "onCapabilitiesChanged.connectionEstablished (network=$network): setConnected to true")
            surveyor.setConnected(true)
        } else if (!isSyncableNow && isConnected) {
            // onLost() is the primary path for disconnects; keep this as a safety net.
            Log.v(TAG, "onCapabilitiesChanged.connectionLost (network=$network): setConnected to false.")
            surveyor.setConnected(false)
        }
    }

    private fun isSyncable(capabilities: NetworkCapabilities): Boolean {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false
        val isUnMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return isUnMetered || !surveyor.isSyncOnUnMeteredNetworkOnly()
    }
}
