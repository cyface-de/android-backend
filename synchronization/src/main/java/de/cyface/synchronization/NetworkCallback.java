/*
 * Copyright 2018 Cyface GmbH
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

import static de.cyface.synchronization.WiFiSurveyor.MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG;
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
 * be enabled depending on the {@code NetworkCapabilities#NET_CAPABILITY_NOT_METERED} capabilities of the
 * newly connected network.
 *
 * @author Armin Schnabel
 * @version 2.1.1
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
    public void onLost(@NonNull final Network network) {

        // This is required for < MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG or else we are not informed about a lost wifi
        // connection e.g. on Android 6.0.1 (MOV-650, possibly also MOV-645)

        Log.v(TAG, "NetworkCallback.onLost: setConnected to false.");
        surveyor.setConnected(false);
    }

    @Override
    public void onCapabilitiesChanged(@NonNull final Network network, @NonNull final NetworkCapabilities capabilities) {

        // Ensure this event is only triggered for not metered connections when syncOnUnMeteredNetworkOnly
        if (surveyor.isSyncOnUnMeteredNetworkOnly()) {

            final boolean isUsingUnMeteredCheckInsteadOfWifi = Build.VERSION.SDK_INT >= MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG;
            if (isUsingUnMeteredCheckInsteadOfWifi) {
                Validate.isTrue(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
            } else {
                Validate.isTrue(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
            }
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
            Log.v(TAG, "onCapabilitiesChanged.connectionEstablished: setConnected to true");
            surveyor.setConnected(true);

        } else if (syncableConnectionLost) {
            // This should not be necessary as we have onLost() but we keep it as a safety net for now
            Log.v(TAG, "onCapabilitiesChanged.connectionLost: setConnected to false.");
            surveyor.setConnected(false);
        }
    }
}
