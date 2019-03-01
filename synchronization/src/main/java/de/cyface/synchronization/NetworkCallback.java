package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.TAG;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * This callback handles status changes of the {@link Network} connectivity, e.g. to determine if synchronization should
 * be enabled depending on the {@link android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED} capabilities of the
 * newly connected network.
 *
 * @author Armin Schnabel
 * @version 1.1.3
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
        if (currentSynchronizationAccount == null) {
            Log.e(TAG, "No account for data synchronization registered with this service. Aborting synchronization.");
            return;
        }

        final boolean connectionLost = surveyor.synchronizationIsActive() && !surveyor.isConnected();
        final boolean connectionEstablished = !surveyor.synchronizationIsActive() && surveyor.isConnected();

        if (connectionEstablished) {
            if (!ContentResolver.getMasterSyncAutomatically()) {
                Log.d(TAG, "onCapabilitiesChanged: master sync is disabled. Aborting.");
                return;
            }

            // Enable auto-synchronization - periodic flag is always pre set for all account by us
            Log.v(TAG, "onCapabilitiesChanged: setSyncAutomatically.");
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, true);
            surveyor.setSynchronizationIsActive(true);

        } else if (connectionLost) {

            Log.v(TAG, "onCapabilitiesChanged: setSyncAutomatically to false.");
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, false);
            surveyor.setSynchronizationIsActive(false);
        }
    }
}
