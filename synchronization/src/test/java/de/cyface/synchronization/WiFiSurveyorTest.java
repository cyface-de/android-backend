package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * Tests the correct functionality of the <code>WiFiSurveyor</code> class. This test requires an active WiFi connection
 * and thus is a <code>FlakyTest</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
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
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        /*
         * The Android test <code>Context</code> to use for testing.
         */
        Context context = RuntimeEnvironment.application;
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
        switchWiFiConnection(false);
        assertThat(oocut.isConnected(), is(equalTo(false)));
        Account account = oocut.getOrCreateAccount("test");
        oocut.startSurveillance(account);
        switchWiFiConnection(true);
        assertThat(oocut.isConnected(), is(equalTo(true)));
        assertThat(oocut.synchronizationIsActive(), is(equalTo(true)));
    }

    /**
     * Tests if mobile and WiFi connectivity is detected correctly if both are allowed.
     *
     * @throws SynchronisationException This should not happen in the test environment. Occurs if no Android
     *             <code>Context</code> is available.
     */
    @Test
    public void testMobileConnectivity() throws SynchronisationException {
        switchMobileConnection(false);

        switchWiFiConnection(false);
        oocut.syncOnWiFiOnly(false);
        assertThat(oocut.isConnected(), is(equalTo(false)));
        switchMobileConnection(true);
        assertThat(oocut.isConnected(), is(equalTo(true)));
    }

    /**
     * @return An appropriate <code>ConnectivityManager</code> from Robolectric.
     */
    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager)RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
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
        RuntimeEnvironment.application.sendBroadcast(broadcastIntent);
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
        RuntimeEnvironment.application.sendBroadcast(broadcastIntent);
    }

}
