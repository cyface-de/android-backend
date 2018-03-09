package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
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
public class WiFiSurveyorTest {

    private Context context;
    private ShadowConnectivityManager shadowConnectivityManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application.getApplicationContext();
        ConnectivityManager connectivityManager = getConnectivityManager();
        shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
    }

    @Test
    public void test() throws InterruptedException, SynchronisationException {
        setWiFiDisconnected();

        WiFiSurveyor surveyor = new WiFiSurveyor(context);
        assertThat(surveyor.isConnectedToWifi(), is(equalTo(false)));

        Account account = surveyor.getOrCreateAccount("test");
        surveyor.startSurveillance(account);

        setWiFiConnected();
        assertThat(surveyor.isConnectedToWifi(), is(equalTo(true)));
        assertThat(surveyor.synchronizationIsActive(), is(equalTo(true)));
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager)RuntimeEnvironment.application.getSystemService(context.CONNECTIVITY_SERVICE);
    }

    private void setWiFiConnected() {
        NetworkInfo connectedNetworkInfo = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true);
        shadowConnectivityManager.setNetworkInfo(ConnectivityManager.TYPE_WIFI, connectedNetworkInfo);
        Intent broadcastIntent = new Intent("android.net.wifi.supplicant.CONNECTION_CHANGE");
        broadcastIntent.putExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, true);
        RuntimeEnvironment.application.sendBroadcast(broadcastIntent);
    }

    private void setWiFiDisconnected() {
        NetworkInfo networkInfoShadow = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, false);
        shadowConnectivityManager.setNetworkInfo(ConnectivityManager.TYPE_WIFI, networkInfoShadow);

        Intent broadcastIntent = new Intent("android.net.wifi.supplicant.CONNECTION_CHANGE");
        broadcastIntent.putExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
        RuntimeEnvironment.application.sendBroadcast(broadcastIntent);
    }
}
