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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Covers [WiFiSurveyor.isConnectedToSyncableNetwork] after the BIK-445 fix that added an
 * `allNetworks` fallback for the window in which [ConnectivityManager.getActiveNetwork] is still
 * null while the system is promoting a new default route.
 *
 * The metered-network policy must survive the new fallback: when the surveyor is configured for
 * unmetered-only, the fallback must never accept a metered cellular even when it is the only
 * network with internet.
 */
class WiFiSurveyorSyncableCheckTest {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var surveyor: WiFiSurveyor

    @Before
    fun setUp() {
        val context = mock(Context::class.java)
        connectivityManager = mock(ConnectivityManager::class.java)
        surveyor = WiFiSurveyor(
            context,
            connectivityManager,
            TestUtils.AUTHORITY,
            TestUtils.ACCOUNT_TYPE
        )
        // surveyor defaults to syncOnUnMeteredNetworkOnly = true, matching production default.
    }

    // --- happy path: activeNetwork is populated ---------------------------------------------------

    @Test
    fun `happy path - active wifi is unmetered and has internet - syncable`() {
        val wifi = mock(Network::class.java)
        val wifiCaps = capabilities(unMetered = true, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(wifi)
        `when`(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(true))
    }

    @Test
    fun `happy path - active network is metered while unmetered-only - not syncable`() {
        val cellular = mock(Network::class.java)
        val cellularCaps = capabilities(unMetered = false, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(cellular)
        `when`(connectivityManager.getNetworkCapabilities(cellular)).thenReturn(cellularCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(false))
    }

    @Test
    fun `happy path - active network has no internet capability - not syncable`() {
        val wifi = mock(Network::class.java)
        val wifiCaps = capabilities(unMetered = true, hasInternet = false)
        `when`(connectivityManager.activeNetwork).thenReturn(wifi)
        `when`(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(false))
    }

    // --- fallback: activeNetwork null, scan allNetworks ------------------------------------------

    @Test
    fun `fallback - activeNetwork null with no networks at all - not syncable`() {
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(emptyArray())

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(false))
    }

    @Test
    fun `fallback - activeNetwork null with only a metered cellular - not syncable when unmetered-only`() {
        // This is the exact case the user worried about: the system is mid-handoff, default route
        // is null, and the only network visible to us is a metered cellular. We must refuse it
        // because syncOnUnMeteredNetworkOnly is true (the default).
        val cellular = mock(Network::class.java)
        val cellularCaps = capabilities(unMetered = false, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(cellular))
        `when`(connectivityManager.getNetworkCapabilities(cellular)).thenReturn(cellularCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(false))
    }

    @Test
    fun `fallback - activeNetwork null with metered cellular plus unmetered wifi - syncable via wifi`() {
        // Handoff in progress: both networks exist but neither is the default route yet. We want
        // the wifi to be picked, never the cellular.
        val cellular = mock(Network::class.java)
        val wifi = mock(Network::class.java)
        val cellularCaps = capabilities(unMetered = false, hasInternet = true)
        val wifiCaps = capabilities(unMetered = true, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(cellular, wifi))
        `when`(connectivityManager.getNetworkCapabilities(cellular)).thenReturn(cellularCaps)
        `when`(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(true))
    }

    @Test
    fun `fallback - activeNetwork null with unmetered wifi alone - syncable`() {
        val wifi = mock(Network::class.java)
        val wifiCaps = capabilities(unMetered = true, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(wifi))
        `when`(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(true))
    }

    @Test
    fun `fallback - unmetered wifi without internet should not qualify`() {
        // Captive portal / association-only wifi: has NOT_METERED but hasn't earned INTERNET yet.
        val wifi = mock(Network::class.java)
        val wifiCaps = capabilities(unMetered = true, hasInternet = false)
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(wifi))
        `when`(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(false))
    }

    @Test
    fun `fallback - opted-in to metered - cellular alone is syncable`() {
        // Flip the private flag directly: the public setter calls stopSurveillance() +
        // startSurveillance(currentSynchronizationAccount!!) which can't run in a unit test
        // (no registered account, no real ConnectivityManager).
        setPrivateSyncOnUnMeteredNetworkOnly(surveyor, false)
        val cellular = mock(Network::class.java)
        val cellularCaps = capabilities(unMetered = false, hasInternet = true)
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        @Suppress("DEPRECATION")
        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(cellular))
        `when`(connectivityManager.getNetworkCapabilities(cellular)).thenReturn(cellularCaps)

        assertThat(surveyor.isConnectedToSyncableNetwork, `is`(true))
    }

    private fun setPrivateSyncOnUnMeteredNetworkOnly(target: WiFiSurveyor, value: Boolean) {
        val field = WiFiSurveyor::class.java.getDeclaredField("syncOnUnMeteredNetworkOnly")
        field.isAccessible = true
        field.setBoolean(target, value)
    }

    private fun capabilities(unMetered: Boolean, hasInternet: Boolean): NetworkCapabilities {
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(unMetered)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(hasInternet)
        return caps
    }
}
