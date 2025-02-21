/*
 * Copyright 2017-2025 Cyface GmbH
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

import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkInfo.DetailedState
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.Validate.isTrue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Tests the correct functionality of [WiFiSurveyor].
 *
 * Disabled: When this test was written, it tested code written for now unsupported Android
 * versions. The code used in SDK >= OREO was not tested at the time when this test was written as
 * `ShadowNetworkCapabilities` did not yet support `NET_CAPABILITY_NOT_METERED`. [MOV-699]
 * FIXME: remove the comment above when this works
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 2.0.0
 */
// FIXME: Try if we can get this test to work with >= Android 8. If not this is okay, too, as we
//  did not run this until now, but we should then add a manual test and document what needs to be
//  tested and how
@Ignore("Temporarily disabled")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [VERSION_CODES.O]) // Using minSdkVersion for now // FIXME: See if we really need to define a version
class WiFiSurveyorTest {
    /**
     * The Robolectric shadow used for the Android `ConnectivityManager`.
     */
    private var shadowConnectivityManager: ShadowConnectivityManager? = null

    /**
     * An object of the class under test.
     */
    private var oocut: WiFiSurveyor? = null

    /**
     * The Android test `Context` to use for testing.
     */
    private var context: Context? = null

    /**
     * The `ConnectivityManager` required for the [WiFiSurveyor].
     */
    private var connectivityManager: ConnectivityManager? = null

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        connectivityManager = getConnectivityManager()
        shadowConnectivityManager = Shadows.shadowOf(connectivityManager)

        oocut = WiFiSurveyor(
            context!!,
            connectivityManager!!,
            TestUtils.AUTHORITY,
            TestUtils.ACCOUNT_TYPE
        )
    }

    /**
     * Tests that WiFi connectivity is detected correctly.
     *
     * @throws SynchronisationException This should not happen in the test environment. Occurs if
     * no Android `Context` is available.
     */
    @Test
    @Throws(SynchronisationException::class)
    fun testWifiConnectivity() {
        // Arrange
        val account = oocut!!.createAccount("test", null)
        oocut!!.startSurveillance(account)
        // `PeriodicSync` and `syncAutomatically` should be disabled by default.
        // Checking `getPeriodicSyncs` only works without waiting in robolectric as
        // `addPeriodicSync` seems to be async
        require(ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY).size == 0)
        require(!ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY))

        // Scenario 1: with active mobile connection
        setMobileConnectivity(true, oocut!!)

        // Act & Assert 1a - don't change the order within this block
        setWiFiConnectivity(false, oocut!!)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(false))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(false)))

        // Act & Assert 1b - don't change the order within this block
        setWiFiConnectivity(true, oocut!!)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(true))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(true)))

        // Scenario 2: with inactive mobile connection
        setMobileConnectivity(true, oocut!!)

        // Act & Assert 2a - don't change the order within this block
        setWiFiConnectivity(false, oocut!!)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(false))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(false)))

        // Act & Assert 2b - don't change the order within this block
        setWiFiConnectivity(true, oocut!!)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(true))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(true)))

        // Cleanup
        ContentResolver.removePeriodicSync(account, TestUtils.AUTHORITY, Bundle.EMPTY)
    }

    /**
     * Tests if mobile and WiFi connectivity is detected correctly if both are allowed.
     */
    @Test
    @Throws(SynchronisationException::class)
    fun testMobileConnectivity() {
        // Arrange

        val account = oocut!!.createAccount("test", null)
        oocut!!.startSurveillance(account)
        // PeriodicSync and syncAutomatically should be disabled by default
        isTrue(ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY).size == 0)
        isTrue(!ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY))

        // Act & Assert 1 - don't change the order within this block
        setMobileConnectivity(false, oocut!!)
        setWiFiConnectivity(false, oocut!!)
        oocut!!.setSyncOnUnMeteredNetworkOnly(false)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(false))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(false)))

        // Act & Assert 2 - don't change the order within this block
        setMobileConnectivity(true, oocut!!)
        assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            `is`(equalTo(true))
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(true)))

        // Cleanup
        ContentResolver.removePeriodicSync(account, TestUtils.AUTHORITY, Bundle.EMPTY)
    }

    /**
     * @return An appropriate `ConnectivityManager` from Robolectric.
     */
    private fun getConnectivityManager(): ConnectivityManager {
        return context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Switches the simulated state of the active network connection to either WiFi on or off.
     *
     * @param connected True if the connection is set to connected
     * @param surveyor To check the [WiFiSurveyor.isSyncOnUnMeteredNetworkOnly] state to decide weather the test
     * should trigger a `NetworkCallback` for `ConnectivityManager#TYPE_MOBILE` connections
     */
    private fun setWiFiConnectivity(connected: Boolean, surveyor: WiFiSurveyor) {
        setConnectivity(connected, true, surveyor)
    }

    /**
     * Switches the simulated state of the active network connection to either mobile on or off.
     *
     * @param connected True if the connection is set to connected
     * @param surveyor To check the [WiFiSurveyor.isSyncOnUnMeteredNetworkOnly] state to decide weather the test
     * should trigger a `NetworkCallback` for `ConnectivityManager#TYPE_MOBILE` connections
     */
    private fun setMobileConnectivity(connected: Boolean, surveyor: WiFiSurveyor) {
        setConnectivity(connected, false, surveyor)
    }

    /**
     * Switches the simulated state of the active network connection to on or off.
     *
     * @param connected True if the connection is set to connected
     * @param wifiNotMobile True if the simulated connected to be switched is of type
     * `ConnectivityManager.TYPE_WIFI`, false if it's of type `ConnectivityManager.TYPE_MOBILE`
     * @param surveyor To check the [WiFiSurveyor.isSyncOnUnMeteredNetworkOnly] state to decide weather the test
     * should trigger a `NetworkCallback` for `ConnectivityManager#TYPE_MOBILE` connections
     */
    private fun setConnectivity(
        connected: Boolean, wifiNotMobile: Boolean,
        surveyor: WiFiSurveyor
    ) {
        // Determine Parameters
        val connectionType =
            if (wifiNotMobile) ConnectivityManager.TYPE_WIFI
            else ConnectivityManager.TYPE_MOBILE
        val transportType =
            if (wifiNotMobile) NetworkCapabilities.TRANSPORT_WIFI
            else NetworkCapabilities.TRANSPORT_CELLULAR
        val networkState =
            if (connected) NetworkInfo.State.CONNECTED
            else NetworkInfo.State.DISCONNECTED
        val detailedState =
            if (connected) DetailedState.CONNECTED
            else DetailedState.DISCONNECTED

        // Set NetworkInfo and ActiveNetworkInfo with the correct connectionType and networkState
        val testNetworkInfo = ShadowNetworkInfo.newInstance(
            detailedState,
            connectionType,
            0,
            true,
            networkState,
        )
        shadowConnectivityManager!!.setNetworkInfo(connectionType, testNetworkInfo)
        if (connected) {
            shadowConnectivityManager!!.setActiveNetworkInfo(testNetworkInfo)
            val activeInfo = connectivityManager!!.activeNetworkInfo
            requireNotNull(activeInfo)
            assertThat(activeInfo.isConnected, `is`(equalTo(connected)))
            assertThat(activeInfo.type, `is`(equalTo(connectionType)))
        } else {
            shadowConnectivityManager!!.setActiveNetworkInfo(null)
        }

        // Send NetworkCallbacks
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        val shadowNetworkCapabilities = Shadows.shadowOf(networkCapabilities)
        shadowNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadowNetworkCapabilities.addTransportType(transportType)
        val testNetId = 123
        val testNetwork = ShadowNetwork.newInstance(testNetId)
        shadowConnectivityManager!!.setNetworkCapabilities(testNetwork, networkCapabilities)
        val loadedNetworkCapabilities = connectivityManager!!.getNetworkCapabilities(testNetwork)
        assertThat(
            loadedNetworkCapabilities,
            `is`(equalTo(networkCapabilities))
        )

        // Now we can call the NetworkCallbacks with the correct networkCapabilities (registration is ok)
        // Only call the networkCallback for wifi connections as we only register those in production code
        if (wifiNotMobile || !surveyor.isSyncOnUnMeteredNetworkOnly()) {
            val isAccountRegistered = oocut!!.currentSynchronizationAccount != null
            assertThat(
                shadowConnectivityManager!!.networkCallbacks.size,
                `is`(equalTo(if (isAccountRegistered) 1 else 0))
            )

            if (!isAccountRegistered) {
                return  // Without accounts, no network callbacks must be updated
            }

            val networkCallback = shadowConnectivityManager!!.networkCallbacks
                .iterator().next()
            networkCallback.onCapabilitiesChanged(testNetwork, loadedNetworkCapabilities!!)
            if (!connected) {
                networkCallback.onLost(testNetwork)
            }
        }
    }
}
