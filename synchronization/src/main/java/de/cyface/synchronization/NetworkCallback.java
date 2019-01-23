package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.TAG;
import static de.cyface.synchronization.WiFiSurveyor.SYNC_INTERVAL;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;

/**
 * This callback handles status changes of the {@link Network} connectivity, e.g. to determine if synchronization should
 * be enabled depending on the {@link android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED} capabilities of the
 * newly connected network.
 *
 * FIXME: We need to test this on API >= 21 devices !!
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NetworkCallback extends ConnectivityManager.NetworkCallback {

    /**
     * The {@link WiFiSurveyor} which registered this callback used to access some of it's methods.
     */
    private final WiFiSurveyor surveyor;
    /**
     * The <code>Account</code> currently used for data synchronization or <code>null</code> if no such
     * <code>Account</code> has been set.
     */
    private Account currentSynchronizationAccount;
    /**
     * The <code>ContentProvider</code> authority used by this service to store and read data. See the
     * <a href="https://developer.android.com/guide/topics/providers/content-providers.html">Android documentation</a>
     * for further information.
     */
    private final String authority;

    NetworkCallback(@NonNull final WiFiSurveyor wiFiSurveyor, @NonNull final Account currentSynchronizationAccount,
            @NonNull final String authority) {
        this.surveyor = wiFiSurveyor;
        this.authority = authority;
        this.currentSynchronizationAccount = currentSynchronizationAccount;
    }

    @Override
    public void onCapabilitiesChanged(@NonNull final Network network, @NonNull final NetworkCapabilities capabilities) {
        Log.d(TAG, "NetworkCapabilities changed");
        if (currentSynchronizationAccount == null) {
            Log.e(TAG, "No account for data synchronization registered with this service. Aborting synchronization.");
            return;
        }

        if (surveyor.isConnected()) {
            // Try synchronization periodically
            boolean cyfaceAccountSyncIsEnabled = ContentResolver.getSyncAutomatically(currentSynchronizationAccount,
                    authority);
            boolean masterAccountSyncIsEnabled = ContentResolver.getMasterSyncAutomatically();

            if (cyfaceAccountSyncIsEnabled && masterAccountSyncIsEnabled) {
                Log.d(TAG, "Enabling periodic sync.");
                ContentResolver.addPeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY, SYNC_INTERVAL);
            }
            surveyor.setSynchronizationIsActive(true);
        } else {
            // wifi connection was lost
            Log.d(TAG, "Disabling periodic sync.");
            ContentResolver.removePeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY);
            surveyor.setSynchronizationIsActive(false);
        }
    }
}
