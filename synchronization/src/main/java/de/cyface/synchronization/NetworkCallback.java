/*
 * Copyright 2018 Cyface GmbH
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

import static de.cyface.synchronization.WiFiSurveyor.TAG;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.utils.Validate;

/**
 * This callback handles status changes of the {@link Network} connectivity, e.g. to determine if synchronization should
 * be enabled depending on the {@link android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED} capabilities of the
 * newly connected network.
 *
 * @author Armin Schnabel
 * @version 2.0.1
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

    NetworkCallback(@NonNull final WiFiSurveyor wiFiSurveyor, @NonNull final Account currentSynchronizationAccount) {
        this.surveyor = wiFiSurveyor;
        this.currentSynchronizationAccount = currentSynchronizationAccount;
    }

    @Override
    public void onCapabilitiesChanged(@NonNull final Network network, @NonNull final NetworkCapabilities capabilities) {

        // Ensure this event is only triggered for not metered connections when syncOnUnMeteredNetworkOnly
        if (surveyor.isSyncOnUnMeteredNetworkOnly()) {
            final boolean unMeteredNetwork = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            Validate.isTrue(unMeteredNetwork);
        }

        if (currentSynchronizationAccount == null) {
            Log.e(TAG, "No account for data synchronization registered with this service. Aborting synchronization.");
            return;
        }

        // Syncable ("not metered") filter is already included
        final boolean syncableConnectionLost = surveyor.isConnected() && !surveyor.isConnectedToSyncableNetwork();
        final boolean syncableConnectionEstablished = !surveyor.isConnected()
                && surveyor.isConnectedToSyncableNetwork();

        if (syncableConnectionEstablished) {
            Log.v(TAG, "connectionEstablished: setConnected to true");
            surveyor.setConnected(true);

        } else if (syncableConnectionLost) {
            Log.v(TAG, "connectionLost: setConnected to false.");
            surveyor.setConnected(false);
        }
    }
}
