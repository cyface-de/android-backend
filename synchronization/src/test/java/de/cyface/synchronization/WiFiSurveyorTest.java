/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static android.os.Build.VERSION_CODES.KITKAT;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the <code>WiFiSurveyor</code> class.
 * <p>
 * Robolectric is used to emulate the Android context. The tests are currently executed explicitly with KITKAT SDK
 * as we did not yet port the tests to the newer Robolectric version (or we failed to).
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.4
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = KITKAT) // Because these Roboelectric tests don't run on newer SDKs
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
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ConnectivityManager connectivityManager = getConnectivityManager();
        shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
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

        // Act & Assert 1 - don't change the order within this block
        switchWiFiConnection(false);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(false)));
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 2 - don't change the order within this block
        switchWiFiConnection(true);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(true)));
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Cleanup
        ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);
    }

    /**
     * Tests if mobile and WiFi connectivity is detected correctly if both are allowed.
     *
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
        switchMobileConnection(false);
        switchWiFiConnection(false);
        oocut.setSyncOnUnMeteredNetworkOnly(false);
        assertThat(oocut.isConnectedToSyncableNetwork(), is(equalTo(false)));
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 2 - don't change the order within this block
        switchMobileConnection(true);
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
     * @param enabled If <code>true</code>, the connection is switched to on; if <code>false</code> it is switched to
     *            off.
     */
    private void switchWiFiConnection(final boolean enabled) {
        NetworkInfo networkInfoShadow = ShadowNetworkInfo.newInstance(
                enabled ? NetworkInfo.DetailedState.CONNECTED : NetworkInfo.DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true,
                enabled ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED);
        shadowConnectivityManager.setNetworkInfo(ConnectivityManager.TYPE_WIFI, networkInfoShadow);
        if (enabled) {
            shadowConnectivityManager.setActiveNetworkInfo(networkInfoShadow);
        } else {
            shadowConnectivityManager.setActiveNetworkInfo(null);
        }
        Intent broadcastIntent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcastIntent.putExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, enabled);
        context.sendBroadcast(broadcastIntent);
    }

    /**
     * Switches the simulated state of the active network connection to either mobile on or off.
     *
     * @param enabled If <code>true</code>, the connection is switched to on; if <code>false</code> it is switched to
     *            off.
     */
    private void switchMobileConnection(final boolean enabled) {
        NetworkInfo networkInfoShadow = ShadowNetworkInfo.newInstance(
                enabled ? NetworkInfo.DetailedState.CONNECTED : NetworkInfo.DetailedState.DISCONNECTED,
                ConnectivityManager.TYPE_MOBILE, 0, true,
                enabled ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED);
        shadowConnectivityManager.setNetworkInfo(ConnectivityManager.TYPE_MOBILE, networkInfoShadow);
        if (enabled) {
            shadowConnectivityManager.setActiveNetworkInfo(networkInfoShadow);
        } else {
            shadowConnectivityManager.setActiveNetworkInfo(null);
        }
        Intent broadcastIntent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
        context.sendBroadcast(broadcastIntent);
    }
}
