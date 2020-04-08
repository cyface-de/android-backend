/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.synchronization;

import static android.os.Build.VERSION_CODES.M;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the <code>WiFiSurveyor</code> class.
 * <p>
 * We execute these test on multiple SDKs as we have different production code depending on the SDK:
 * - MARSHMALLOW (i.e. SDK < OREO) to test {@link NetworkCallback} with
 * {@code NetworkCapabilities#NET_WIFI_TRANSPORT}. Adding this should prevent bug MOV-650 from reoccurring.
 * - PIE (i.e. SDK >= OREO) to test {@link NetworkCallback} with {@code NetworkCapabilities#NET_CAPABILITY_NOT_METERED}.
 * TODO [MOV-699]: add this as soon as ShadowNetworkCapabilities support adding NET_CAPABILITY_NOT_METERED
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {M/* , P */})
public class WiFiSurveyorTest {

    /**
     * The Robolectric shadow used for the Android <code>ConnectivityManager</code>.
     */
    private ShadowConnectivityManager shadowConnectivityManager;
    /**
     * An object of the class under test.
     */
    private WiFiSurveyor oocut;
    /**
     * The Android test <code>Context</code> to use for testing.
     */
    private Context context;
    /**
     * The {@code ConnectivityManager} required for the {@link WiFiSurveyor}.
     */
    private ConnectivityManager connectivityManager;

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        connectivityManager = getConnectivityManager();
        shadowConnectivityManager = shadowOf(connectivityManager);

        oocut = new WiFiSurveyor(context, connectivityManager, AUTHORITY, ACCOUNT_TYPE);
    }

    /**
     * Tests that WiFi connectivity is detected correctly.
     *
     * @throws SynchronisationException This should not happen in the test environment. Occurs if no Android
     *             <code>Context</code> is available.
     */
    @Test
    public void testWifiConnectivity() throws SynchronisationException {

        // Arrange
        Account account = oocut.createAccount("test", null);
        oocut.startSurveillance(account);
        // PeriodicSync and syncAutomatically should be disabled by default
        // Checking getPeriodicSyncs only works without waiting in robolectric as addPeriodicSync seems to be async
        Validate.isTrue(ContentResolver.getPeriodicSyncs(account, AUTHORITY).size() == 0);
        Validate.isTrue(!ContentResolver.getSyncAutomatically(account, AUTHORITY));

        // Scenario 1: with active mobile connection
        setMobileConnectivity(true, oocut);

        // Act & Assert 1a - don't change the order within this block
        setWiFiConnectivity(false, oocut);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(false)));
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 1b - don't change the order within this block
        setWiFiConnectivity(true, oocut);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(true)));
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Scenario 2: with inactive mobile connection
        setMobileConnectivity(true, oocut);

        // Act & Assert 2a - don't change the order within this block
        setWiFiConnectivity(false, oocut);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(false)));
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 2b - don't change the order within this block
        setWiFiConnectivity(true, oocut);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(true)));
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Cleanup
        ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);
    }

    /**
     * Tests if mobile and WiFi connectivity is detected correctly if both are allowed.
     */
    @Test
    public void testMobileConnectivity() throws SynchronisationException {

        // Arrange
        Account account = oocut.createAccount("test", null);
        oocut.startSurveillance(account);
        // PeriodicSync and syncAutomatically should be disabled by default
        Validate.isTrue(ContentResolver.getPeriodicSyncs(account, AUTHORITY).size() == 0);
        Validate.isTrue(!ContentResolver.getSyncAutomatically(account, AUTHORITY));

        // Act & Assert 1 - don't change the order within this block
        setMobileConnectivity(false, oocut);
        setWiFiConnectivity(false, oocut);
        oocut.setSyncOnUnMeteredNetworkOnly(false);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(false)));
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 2 - don't change the order within this block
        setMobileConnectivity(true, oocut);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(true)));
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Cleanup
        ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);
    }

    /**
     * @return An appropriate <code>ConnectivityManager</code> from Robolectric.
     */
    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Switches the simulated state of the active network connection to either WiFi on or off.
     *
     * @param connected True if the connection is set to connected
     * @param surveyor To check the {@link WiFiSurveyor#isSyncOnUnMeteredNetworkOnly()} state to decide weather the test
     *            should trigger a {@code NetworkCallback} for {@code ConnectivityManager#TYPE_MOBILE} connections
     */
    private void setWiFiConnectivity(final boolean connected, @NonNull final WiFiSurveyor surveyor) {

        setConnectivity(connected, true, surveyor);
    }

    /**
     * Switches the simulated state of the active network connection to either mobile on or off.
     *
     * @param connected True if the connection is set to connected
     * @param surveyor To check the {@link WiFiSurveyor#isSyncOnUnMeteredNetworkOnly()} state to decide weather the test
     *            should trigger a {@code NetworkCallback} for {@code ConnectivityManager#TYPE_MOBILE} connections
     */
    private void setMobileConnectivity(final boolean connected, @NonNull final WiFiSurveyor surveyor) {

        setConnectivity(connected, false, surveyor);
    }

    /**
     * Switches the simulated state of the active network connection to on or off.
     *
     * @param connected True if the connection is set to connected
     * @param wifiNotMobile True if the simulated connected to be switched is of type
     *            {@code ConnectivityManager.TYPE_WIFI}, false if it's of type {@code ConnectivityManager.TYPE_MOBILE}
     * @param surveyor To check the {@link WiFiSurveyor#isSyncOnUnMeteredNetworkOnly()} state to decide weather the test
     *            should trigger a {@code NetworkCallback} for {@code ConnectivityManager#TYPE_MOBILE} connections
     */
    private void setConnectivity(final boolean connected, final boolean wifiNotMobile,
            @NonNull final WiFiSurveyor surveyor) {

        // Determine Parameters
        final int connectionType = wifiNotMobile ? ConnectivityManager.TYPE_WIFI : ConnectivityManager.TYPE_MOBILE;
        final int transportType = wifiNotMobile ? NetworkCapabilities.TRANSPORT_WIFI
                : NetworkCapabilities.TRANSPORT_CELLULAR;
        final NetworkInfo.State networkState = connected ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED;
        final NetworkInfo.DetailedState detailedState = connected ? NetworkInfo.DetailedState.CONNECTED
                : NetworkInfo.DetailedState.DISCONNECTED;

        // Set NetworkInfo and ActiveNetworkInfo with the correct connectionType and networkState
        final NetworkInfo testNetworkInfo = ShadowNetworkInfo.newInstance(detailedState, connectionType, 0, true,
                networkState);
        shadowConnectivityManager.setNetworkInfo(connectionType, testNetworkInfo);
        shadowConnectivityManager.setActiveNetworkInfo(connected ? testNetworkInfo : null);
        if (connected) {
            final NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();
            Validate.notNull(activeInfo);
            // noinspection ConstantConditions - for semantics / readability
            assertThat(activeInfo.isConnected(), is(equalTo(connected)));
            assertThat(activeInfo.getType(), is(equalTo(connectionType)));
        }

        // Send NetworkCallbacks
        // We need to set the transportType (for SDK M) and networkCapability (not_metered) for SDK P
        final NetworkCapabilities networkCapabilities = ShadowNetworkCapabilities.newInstance();
        final ShadowNetworkCapabilities shadowNetworkCapabilities = shadowOf(networkCapabilities);
        shadowNetworkCapabilities.addTransportType(transportType);
        final int testNetId = 123;
        final Network testNetwork = ShadowNetwork.newInstance(testNetId);
        shadowConnectivityManager.setNetworkCapabilities(testNetwork, networkCapabilities);
        final NetworkCapabilities loadedNetworkCapabilities = connectivityManager.getNetworkCapabilities(testNetwork);
        assertThat(loadedNetworkCapabilities, is(equalTo(networkCapabilities)));

        // Now we can call the NetworkCallbacks with the correct networkCapabilities (registration is ok)
        // Only call the networkCallback for wifi connections as we only register those in production code
        if (wifiNotMobile || !surveyor.isSyncOnUnMeteredNetworkOnly()) {
            final boolean isAccountRegistered = oocut.currentSynchronizationAccount != null;
            assertThat(shadowConnectivityManager.getNetworkCallbacks().size(),
                    is(equalTo(isAccountRegistered ? 1 : 0)));

            if (!isAccountRegistered) {
                return; // Without accounts, no network callbacks must be updated
            }

            final ConnectivityManager.NetworkCallback networkCallback = shadowConnectivityManager.getNetworkCallbacks()
                    .iterator().next();
            networkCallback.onCapabilitiesChanged(testNetwork, loadedNetworkCapabilities);
            if (!connected) {
                networkCallback.onLost(testNetwork);
            }
        }
    }
}
