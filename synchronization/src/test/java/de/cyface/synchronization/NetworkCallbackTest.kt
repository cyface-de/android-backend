/*
 * Copyright 2026 Cyface GmbH
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

import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Covers [NetworkCallback.onCapabilitiesChanged] and [NetworkCallback.onLost] after the BIK-445
 * fix that made the callback trust the `(network, capabilities)` handed to it instead of
 * re-querying [android.net.ConnectivityManager.activeNetwork].
 *
 * The cases worth pinning down:
 * - unmetered wifi with internet while we default to "unmetered only" → syncable established
 * - unmetered wifi without internet → not syncable (e.g. captive portal hasn't resolved yet)
 * - metered network when opted in via `setSyncOnUnMeteredNetworkOnly(false)` → syncable
 * - a syncable connection that becomes not-syncable on a capability change → disconnected
 * - an already-connected surveyor that sees another unmetered callback → no redundant flip
 * - `onLost` → disconnected
 * - defensive: metered caps delivered while we filter for unmetered-only → require() trips
 *   (OS filter should prevent this, but the belt-and-braces check stays)
 */
class NetworkCallbackTest {

    private lateinit var surveyor: WiFiSurveyor
    private lateinit var callback: NetworkCallback
    private lateinit var network: Network

    @Before
    fun setUp() {
        surveyor = mock(WiFiSurveyor::class.java)
        network = mock(Network::class.java)
        callback = NetworkCallback(surveyor)
    }

    @Test
    fun `onCapabilitiesChanged on unmetered wifi with internet - not yet connected - sets connected`() {
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(true)
        `when`(surveyor.isConnected).thenReturn(false)
        val caps = capabilities(unMetered = true, hasInternet = true)

        callback.onCapabilitiesChanged(network, caps)

        verify(surveyor).setConnected(true)
    }

    @Test
    fun `onCapabilitiesChanged on unmetered wifi without internet - no state change`() {
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(true)
        `when`(surveyor.isConnected).thenReturn(false)
        val caps = capabilities(unMetered = true, hasInternet = false)

        callback.onCapabilitiesChanged(network, caps)

        verify(surveyor, never()).setConnected(true)
        verify(surveyor, never()).setConnected(false)
    }

    @Test
    fun `onCapabilitiesChanged on metered network - opted in - sets connected`() {
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(false)
        `when`(surveyor.isConnected).thenReturn(false)
        val caps = capabilities(unMetered = false, hasInternet = true)

        callback.onCapabilitiesChanged(network, caps)

        verify(surveyor).setConnected(true)
    }

    @Test
    fun `onCapabilitiesChanged flips to lost when syncable caps disappear - was connected`() {
        // e.g. the same wifi loses NET_CAPABILITY_INTERNET (captive portal) while we stay attached
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(true)
        `when`(surveyor.isConnected).thenReturn(true)
        val caps = capabilities(unMetered = true, hasInternet = false)

        callback.onCapabilitiesChanged(network, caps)

        verify(surveyor).setConnected(false)
    }

    @Test
    fun `onCapabilitiesChanged does not flip when already connected and still syncable`() {
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(true)
        `when`(surveyor.isConnected).thenReturn(true)
        val caps = capabilities(unMetered = true, hasInternet = true)

        callback.onCapabilitiesChanged(network, caps)

        verify(surveyor, never()).setConnected(true)
        verify(surveyor, never()).setConnected(false)
    }

    @Test
    fun `onLost sets connected to false`() {
        callback.onLost(network)

        verify(surveyor).setConnected(false)
    }

    @Test
    fun `onCapabilitiesChanged with metered caps while unmetered-only is on - require trips`() {
        // The OS filter (NetworkRequest NET_CAPABILITY_NOT_METERED) should prevent this entirely;
        // the require() is a defensive check that we intentionally keep.
        `when`(surveyor.isSyncOnUnMeteredNetworkOnly()).thenReturn(true)
        val caps = capabilities(unMetered = false, hasInternet = true)

        assertThrows(IllegalArgumentException::class.java) {
            callback.onCapabilitiesChanged(network, caps)
        }
    }

    private fun capabilities(unMetered: Boolean, hasInternet: Boolean): NetworkCapabilities {
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(unMetered)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(hasInternet)
        return caps
    }
}
