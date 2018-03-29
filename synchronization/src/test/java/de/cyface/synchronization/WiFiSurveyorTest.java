package de.cyface.synchronization;

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
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(RobolectricTestRunner.class)
// @LargeTest
// @FlakyTest
@Config(constants = BuildConfig.class)
public class WiFiSurveyorTest {

    private Context context;
    private ShadowConnectivityManager shadowConnectivityManager;
    private WiFiSurveyor oocut;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        ConnectivityManager connectivityManager = getConnectivityManager();
        shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
        oocut = new WiFiSurveyor(context, connectivityManager);
    }

    @Test
    public void testWifiConnectivity() throws InterruptedException, SynchronisationException {
        switchWiFiConnection(false);
        assertThat(oocut.isConnected(), is(equalTo(false)));
        Account account = oocut.getOrCreateAccount("test");
        oocut.startSurveillance(account);
        switchWiFiConnection(true);
        assertThat(oocut.isConnected(), is(equalTo(true)));
        assertThat(oocut.synchronizationIsActive(), is(equalTo(true)));
    }

    @Test
    public void testMobileConnectivity() throws SynchronisationException {
        switchMobileConnection(false);

        switchWiFiConnection(false);
        oocut.syncOnWiFiOnly(false);
        assertThat(oocut.isConnected(), is(equalTo(false)));
        switchMobileConnection(true);
        assertThat(oocut.isConnected(), is(equalTo(true)));
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager)RuntimeEnvironment.application.getSystemService(context.CONNECTIVITY_SERVICE);
    }

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
