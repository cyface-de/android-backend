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
import de.cyface.utils.Validate.notNull
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
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
 * Tests the correct functionality of the `WiFiSurveyor` class.
 *
 *
 * We execute these test on multiple SDKs as we have different production code depending on the SDK:
 * - MARSHMALLOW (i.e. SDK < OREO) to test [NetworkCallback] with
 * `NetworkCapabilities#NET_WIFI_TRANSPORT`. Adding this should prevent bug MOV-650 from reoccurring.
 * - PIE (i.e. SDK >= OREO) to test [NetworkCallback] with `NetworkCapabilities#NET_CAPABILITY_NOT_METERED`.
 * TODO [MOV-699]: add this as soon as ShadowNetworkCapabilities support adding NET_CAPABILITY_NOT_METERED
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.1
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [VERSION_CODES.M /* , P */])
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
     * @throws SynchronisationException This should not happen in the test environment. Occurs if no Android
     * `Context` is available.
     */
    @Test
    @Throws(SynchronisationException::class)
    fun testWifiConnectivity() {
        // Arrange

        val account = oocut!!.createAccount("test", null)
        oocut!!.startSurveillance(account)
        // PeriodicSync and syncAutomatically should be disabled by default
        // Checking getPeriodicSyncs only works without waiting in robolectric as addPeriodicSync seems to be async
        isTrue(ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY).size == 0)
        isTrue(!ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY))

        // Scenario 1: with active mobile connection
        setMobileConnectivity(true, oocut!!)

        // Act & Assert 1a - don't change the order within this block
        setWiFiConnectivity(false, oocut!!)
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(false))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(false)))

        // Act & Assert 1b - don't change the order within this block
        setWiFiConnectivity(true, oocut!!)
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(true))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(true)))

        // Scenario 2: with inactive mobile connection
        setMobileConnectivity(true, oocut!!)

        // Act & Assert 2a - don't change the order within this block
        setWiFiConnectivity(false, oocut!!)
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(false))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(false)))

        // Act & Assert 2b - don't change the order within this block
        setWiFiConnectivity(true, oocut!!)
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(true))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(true)))

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
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(false))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(false)))

        // Act & Assert 2 - don't change the order within this block
        setMobileConnectivity(true, oocut!!)
        MatcherAssert.assertThat(
            oocut!!.isConnectedToSyncableNetwork,
            Matchers.`is`(Matchers.equalTo(true))
        )
        MatcherAssert.assertThat(oocut!!.isConnected, Matchers.`is`(Matchers.equalTo(true)))

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
            if (wifiNotMobile) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE
        val transportType = if (wifiNotMobile) NetworkCapabilities.TRANSPORT_WIFI
        else NetworkCapabilities.TRANSPORT_CELLULAR
        val networkState =
            if (connected) NetworkInfo.State.CONNECTED else NetworkInfo.State.DISCONNECTED
        val detailedState = if (connected) DetailedState.CONNECTED
        else DetailedState.DISCONNECTED

        // Set NetworkInfo and ActiveNetworkInfo with the correct connectionType and networkState
        val testNetworkInfo = ShadowNetworkInfo.newInstance(
            detailedState, connectionType, 0, true,
            networkState
        )
        shadowConnectivityManager!!.setNetworkInfo(connectionType, testNetworkInfo)
        shadowConnectivityManager!!.setActiveNetworkInfo(if (connected) testNetworkInfo else null)
        if (connected) {
            val activeInfo = connectivityManager!!.activeNetworkInfo
            notNull(activeInfo)
            // noinspection ConstantConditions - for semantics / readability
            MatcherAssert.assertThat(
                activeInfo!!.isConnected,
                Matchers.`is`(Matchers.equalTo(connected))
            )
            MatcherAssert.assertThat(
                activeInfo.type,
                Matchers.`is`(Matchers.equalTo(connectionType))
            )
        }

        // Send NetworkCallbacks
        // We need to set the transportType (for SDK M) and networkCapability (not_metered) for SDK P
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        val shadowNetworkCapabilities = Shadows.shadowOf(networkCapabilities)
        shadowNetworkCapabilities.addTransportType(transportType)
        val testNetId = 123
        val testNetwork = ShadowNetwork.newInstance(testNetId)
        shadowConnectivityManager!!.setNetworkCapabilities(testNetwork, networkCapabilities)
        val loadedNetworkCapabilities = connectivityManager!!.getNetworkCapabilities(testNetwork)
        MatcherAssert.assertThat(
            loadedNetworkCapabilities,
            Matchers.`is`(Matchers.equalTo(networkCapabilities))
        )

        // Now we can call the NetworkCallbacks with the correct networkCapabilities (registration is ok)
        // Only call the networkCallback for wifi connections as we only register those in production code
        if (wifiNotMobile || !surveyor.isSyncOnUnMeteredNetworkOnly()) {
            val isAccountRegistered = oocut!!.currentSynchronizationAccount != null
            MatcherAssert.assertThat(
                shadowConnectivityManager!!.networkCallbacks.size,
                Matchers.`is`(Matchers.equalTo(if (isAccountRegistered) 1 else 0))
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
