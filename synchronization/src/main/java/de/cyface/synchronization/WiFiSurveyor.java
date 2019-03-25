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

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;

import java.lang.ref.WeakReference;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.cyface.utils.Validate;

/**
 * An instance of this class is responsible for surveying the state of the devices WiFi connection. If WiFi is active,
 * data is going to be synchronized continuously.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 2.0.0
 */
public class WiFiSurveyor extends BroadcastReceiver {

    /**
     * Logging TAG to identify logs associated with the {@link WiFiSurveyor}.
     */
    @SuppressWarnings({"FieldCanBeLocal", "WeakerAccess", "unused"}) // SDK implementing app (CY) uses this
    public static final String TAG = Constants.TAG + ".surveyor";
    /**
     * The number of seconds in one minute. This value is used to calculate the data synchronisation interval.
     */
    private static final long SECONDS_PER_MINUTE = 60L;
    /**
     * The data synchronisation interval in minutes.
     * <p>
     * <b>Attention:</b> Before you change this make sure you don't use a value lower than the minimum defined by
     * {@code ContentResolver#addPeriodicSync()}. So far the highest minimum of all APIs was 60 minutes
     * which is why we did choose this default value.
     */
    private static final long SYNC_INTERVAL_IN_MINUTES = 60L;
    /**
     * Since we need to specify the sync interval in seconds, this constant transforms the interval in minutes to
     * seconds using {@link #SECONDS_PER_MINUTE}.
     */
    public static final long SYNC_INTERVAL = SYNC_INTERVAL_IN_MINUTES * SECONDS_PER_MINUTE;
    /**
     * The <code>Account</code> currently used for data synchronization or <code>null</code> if no such
     * <code>Account</code> has been set.
     */
    Account currentSynchronizationAccount;
    /**
     * The current Android context (i.e. Activity or Service).
     */
    private WeakReference<Context> context;
    /**
     * If <code>true</code> the <code>MovebisDataCapturingService</code> synchronizes data only if
     * connected to a WiFi network; if <code>false</code> it synchronizes as soon as a data connection is
     * available. The second option might use up the users data plan rapidly so use it sparingly.
     */
    private boolean syncOnUnMeteredNetworkOnly;
    /**
     * The <code>ContentProvider</code> authority used by this service to store and read data. See the
     * <a href="https://developer.android.com/guide/topics/providers/content-providers.html">Android documentation</a>
     * for further information.
     */
    private final String authority;
    /**
     * A <code>String</code> identifying the account type of the accounts to use for data synchronization.
     */
    private final String accountType;
    /**
     * The Android <code>ConnectivityManager</code> used to check the device's current connection status.
     */
    final ConnectivityManager connectivityManager;
    /**
     * A callback which handles changes on the connectivity starting at API {@link Build.VERSION_CODES#LOLLIPOP}
     */
    private NetworkCallback networkCallback;

    /**
     * Creates a new completely initialized <code>WiFiSurveyor</code> within the current Android context.
     *
     * @param context The current Android context (i.e. Activity or Service).
     * @param connectivityManager The Android <code>ConnectivityManager</code> used to check the device's current
     *            connection status.
     * @param authority The <code>ContentProvider</code> authority used by this service to store and read data. See the
     *            <a href="https://developer.android.com/guide/topics/providers/content-providers.html">Android
     *            documentation</a>
     *            for further information.
     * @param accountType A <code>String</code> identifying the account type of the accounts to use for data
     *            synchronization.
     */
    public WiFiSurveyor(final @NonNull Context context, final @NonNull ConnectivityManager connectivityManager,
            final @NonNull String authority, final @NonNull String accountType) {
        this.context = new WeakReference<>(context);
        this.connectivityManager = connectivityManager;
        this.syncOnUnMeteredNetworkOnly = true;
        this.authority = authority;
        this.accountType = accountType;
    }

    /**
     * Starts the connection status surveillance. If a syncable connection is active data synchronization is started.
     * If the connection goes back down synchronization is deactivated.
     * <p>
     * You can allow metered connections as syncable by setting {@link #setSyncOnUnMeteredNetworkOnly(boolean)} to
     * false. The default value is true.
     * <p>
     * The method also schedules an immediate synchronization run after the syncable connection has been connected.
     * <p>
     * <b>ATTENTION:</b> If you use this method do not forget to call {@link #stopSurveillance()}, at some time in the
     * future or you will waste system resources.
     * <p>
     * <b>ATTENTION:</b> Starting at version {@code Build.VERSION_CODES.O} and higher instead of
     * treating only "WiFi" connections as "not metered" we use the
     * {@code NetworkCapabilities#NET_CAPABILITY_NOT_METERED} as synonym as suggested by Android.
     *
     * @param account Starts surveillance of the WiFi connection status for this account.
     * @throws SynchronisationException If no current Android <code>Context</code> is available.
     */
    public void startSurveillance(final @NonNull Account account) throws SynchronisationException {
        if (context.get() == null) {
            throw new SynchronisationException("No valid context available!");
        }

        currentSynchronizationAccount = account;
        scheduleSyncNow(); // Needs to be called after currentSynchronizationAccount is set

        // Roboelectric is currently only testing the deprecated code, see class documentation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

            final NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
            if (syncOnUnMeteredNetworkOnly) {
                Log.v(TAG, "startSurveillance for wifi networks only");
                // Cleaner is "NET_CAPABILITY_NOT_METERED" but this is not yet available on the client (unclear why)
                requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            }
            networkCallback = new NetworkCallback(this, currentSynchronizationAccount);
            connectivityManager.registerNetworkCallback(requestBuilder.build(), networkCallback);

        } else {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            context.get().registerReceiver(this, intentFilter);
        }
    }

    /**
     * Stops surveillance of the devices connection status. This frees up all used system resources.
     * <p>
     * PeriodicSync does not have to be removed in here.
     * - setSyncOnUnMeteredNetworkOnly removes the periodic sync itself
     * - UI.onDestroyView does not expect periodic sync to be removed (tested in MOV-619).
     * This way synchronization also works after onDestroyView was called when there is still syncable connection.
     * If the syncable connection is lost after onDestroyView is called sync does not happen.
     * 
     * @throws SynchronisationException If no current Android <code>Context</code> is available.
     */
    @SuppressWarnings({"unused", "WeakerAccess"}) // Used by CyfaceDataCapturingService
    public void stopSurveillance() throws SynchronisationException {
        if (context.get() == null) {
            throw new SynchronisationException("No valid context available!");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (networkCallback == null) {
                Log.w(TAG, "Unable to unregister NetworkCallback because it's null.");
                return;
            }
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } else {
            try {
                context.get().unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                throw new SynchronisationException(e);
            }
        }
    }

    /**
     * Schedules data synchronization for right now. This does not mean synchronization is going to start immediately.
     * The Android system still decides when it is convenient.
     */
    public void scheduleSyncNow() {
        if (currentSynchronizationAccount == null) {
            Log.w(TAG, "scheduleSyncNow aborted, not account available.");
            return;
        }

        if (isConnectedToSyncableNetwork()) {
            final Bundle params = new Bundle();
            params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(currentSynchronizationAccount, authority, params);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Validate.notNull(intent.getAction());
        final boolean connectivityChanged = intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION);
        Validate.isTrue(connectivityChanged); // We registered only this action so this should always be true

        if (currentSynchronizationAccount == null) {
            Log.e(TAG, "No account for data synchronization registered with this service. Aborting synchronization.");
            return;
        }

        // Syncable ("not metered") filter is already included
        final boolean syncableConnectionLost = isConnected() && !isConnectedToSyncableNetwork();
        final boolean syncableConnectionEstablished = !isConnected() && isConnectedToSyncableNetwork();

        if (syncableConnectionEstablished) {
            Log.v(TAG, "connectionEstablished: setConnected to true");
            setConnected(true);

        } else if (syncableConnectionLost) {
            Log.v(TAG, "connectionLost: setConnected to false.");
            setConnected(false);
        }
    }

    /**
     * Deletes a Cyface account from the Android {@code Account} system. Does silently nothing if no such
     * <code>Account</code> exists.
     * <p>
     * <b>ATTENTION:</b> SDK implementing apps which cannot use this method to remove an account need to call
     * {@code ContentResolver#removePeriodicSync()} themselves.
     *
     * @param username The username of the account to delete.
     */
    @SuppressWarnings("unused") // {@link MovebisDataCapturingService} uses this to deregister a token
    public void deleteAccount(final @NonNull String username) {
        final AccountManager accountManager = AccountManager.get(context.get());
        final Account account = new Account(username, accountType);

        ContentResolver.removePeriodicSync(account, authority, Bundle.EMPTY);

        synchronized (this) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccount(account, null, null);
            } else {
                accountManager.removeAccountExplicitly(account);
            }
            currentSynchronizationAccount = null;
        }
    }

    /**
     * Creates a new {@code Account} which is required for the {@link WiFiSurveyor} to work.
     *
     * @param username The username of the account to be created.
     * @param password The password of the account to be created. May be null if a custom {@link CyfaceAuthenticator} is
     *            used instead of a LoginActivity to return tokens as in {@code MovebisDataCapturingService}.
     * @return The created {@code Account}
     */
    @NonNull
    @SuppressWarnings("unused") // Is used by MovebisDataCapturingService
    public Account createAccount(@NonNull final String username, @Nullable final String password) {

        final AccountManager accountManager = AccountManager.get(context.get());
        final Account newAccount = new Account(username, accountType);

        synchronized (this) {
            // When the account already exists this softly ignores this to support MovebisDataCapturingService
            accountManager.addAccountExplicitly(newAccount, password, Bundle.EMPTY);
            Validate.isTrue(accountManager.getAccountsByType(accountType).length == 1);
            Log.v(TAG, "New account added");

            makeAccountSyncable(newAccount, true);
        }

        return newAccount;
    }

    /**
     * Sets up an already existing {@code Account} to work with the {@link WiFiSurveyor}.
     * <p>
     * <b>Attention:</b> SDK implementing apps need to use this method if they cannot use
     * {@link WiFiSurveyor#createAccount(String, String)}.
     * <p>
     * <b>Attention:</b> Read the following before you change how we mark accounts as syncable or how we mark that a
     * syncable connection is available!
     * <p>
     * <b>Mark connection as syncable</b>
     * - Both, {@code ContentResolver#addPeriodicSync()} and {@code ContentResolver#setSyncAutomatically()},
     * are automatically added or removed via {@link NetworkCallback} or
     * {@link WiFiSurveyor#onReceive(Context, Intent)}, depending on the API level.
     * - The state of {@code ContentResolver#addPeriodicSync()} and {@code ContentResolver#setSyncAutomatically()}
     * define if a syncable connection is available (depending on {@link #setSyncOnUnMeteredNetworkOnly(boolean)}).
     * - never *update only _one_ of both*, {@code ContentResolver#addPeriodicSync()} and
     * {@code ContentResolver#setSyncAutomatically()}, as this produced MOV-535, MOV-609 and MOV-635.
     * - {@code WiFiSurveyorTest#testSetConnected()} showed that addPeriodicSync does not happen instantly
     * which is why that test checks that both flags are set identically and for the same reason we can only
     * check {@code ContentResolver#getSyncAutomatically()} in {@link #isConnected()}.
     * <p>
     * <b>Disabled synchronization completely</b>
     * - Synchronization is enabled by default.
     * - To disable synchronization *completely*, use {@link #setSyncEnabled(Account, boolean)}} which uses
     * the {@code ContentResolver#setIsSyncable()} flag.
     *
     * @param account The {@code Account} to be used for synchronization
     * @param enabled True if the synchronization should be enabled
     */
    @SuppressWarnings("unused") // Used by CyfaceDataCapturingService
    public void makeAccountSyncable(@NonNull final Account account, boolean enabled) {

        ContentResolver.setSyncAutomatically(account, authority, false);
        setSyncEnabled(account, enabled);

            // Do not use validateAccountFlags in production code as periodicSync flags are set async
    }

    /**
     * This method retrieves an <code>Account</code> from the Android account system. If the <code>Account</code>
     * does not exist it throws an IllegalStateException as we require the default SDK using apps to have exactly one
     * account created in advance.
     *
     * @return The only <code>Account</code> existing
     */
    public Account getAccount() {
        final AccountManager accountManager = AccountManager.get(context.get());
        final Account[] cyfaceAccounts = accountManager.getAccountsByType(accountType);
        if (cyfaceAccounts.length == 0) {
            throw new IllegalStateException("No cyface account exists.");
        }
        if (cyfaceAccounts.length > 1) {
            throw new IllegalStateException("More than one cyface account exists.");
        }
        return cyfaceAccounts[0];
    }

    /**
     * This method must only be used internally from the {@link NetworkCallback} and
     * {@link WiFiSurveyor#onReceive(Context, Intent)} on connection status changes and when instant synchronization is
     * requested using {@link #scheduleSyncNow()} .
     * <p>
     * Depending on the result of this method those callers allow and schedule or disallow and de-schedule
     * synchronization.
     * <p>
     * All other interested parties must use {@link #isConnected()} instead.
     *
     * @return <code>true</code> if a "syncable" connection is available, depending on the
     *         {@link #setSyncOnUnMeteredNetworkOnly(boolean)} settings.
     */
    boolean isConnectedToSyncableNetwork() {
        Validate.notNull(connectivityManager);
        final boolean isNotMeteredNetwork;
        final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        // We use the new code only on Android 8 and above as Wifi networks are seen as metered in 6.0.1 (MOV-568)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            final Network activeNetwork = connectivityManager.getActiveNetwork();
            final NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (networkCapabilities == null) {
                // This happened on Xiaomi Mi A2 Android 9.0 in the morning after capturing during the night
                return false;
            }
            isNotMeteredNetwork = networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED);

        } else {
            isNotMeteredNetwork = activeNetworkInfo != null
                    && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }

        final boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
        final boolean isSyncableConnection = isConnected && (isNotMeteredNetwork || !syncOnUnMeteredNetworkOnly);

        Log.v(TAG,
                "isConnectedToSyncableNetwork: " + isSyncableConnection + " ("
                        + (isNotMeteredNetwork ? "notMetered" : "metered") + ", "
                        + (syncOnUnMeteredNetworkOnly ? "with" : "without") + " syncOnUnMeteredNetworkOnly)");
        return isSyncableConnection;
    }

    /**
     * Sets whether synchronization should happen only on
     * {@code android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED}
     * networks or on all networks.
     * <p>
     * <b>ATTENTION:</b> Starting at version {@code Build.VERSION_CODES.O} and higher instead of
     * treating only "WiFi" connections as "not metered" we use the
     * {@code NetworkCapabilities#NET_CAPABILITY_NOT_METERED} as synonym as suggested by Android.
     * <p>
     * This method must be called after {@link #startSurveillance(Account)} is called.
     *
     * @param newState If {@code true} the {@link WiFiSurveyor} synchronizes data only if connected to a
     *            {@code android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED} network; if
     *            {@code false} it synchronizes as soon as a data connection is available. The second option might use
     *            up the users data plan rapidly so use it sparingly. The default value is {@code true}.
     * @throws SynchronisationException If no current Android <code>Context</code> is available.
     */
    public void setSyncOnUnMeteredNetworkOnly(final boolean newState) throws SynchronisationException {

        // In case the restrictions got hardened (disallow metered networks) remove activated syncs
        final boolean mobileDataIsNotAllowedAnymore = !syncOnUnMeteredNetworkOnly && newState;
        if (mobileDataIsNotAllowedAnymore) {
            Log.d(TAG, "setSyncOnUnMeteredNetworkOnly: mobileDataIsNotAllowedAnymore, setConnected to false");
            setConnected(false);
        }

        syncOnUnMeteredNetworkOnly = newState;
        // This is required to update the NetworkCallback filter
        stopSurveillance();
        startSurveillance(currentSynchronizationAccount);
    }

    /**
     * Updates the settings for the sync account to indicate if there is currently a syncable connections or not.
     * <p>
     * Do not call this method before {@link #startSurveillance(Account)} linked a currentSynchronizationAccount.
     * <p>
     * <b>Attention:</b> Before you change the account flags usage, read {@link #makeAccountSyncable(Account, boolean)}.
     *
     * @param enable True if {@code ContentResolver#addPeriodicSync()} should be activated or false if it
     *            should be removed from the sync account.
     */
    void setConnected(final boolean enable) {
        Validate.notNull(currentSynchronizationAccount);

        if (enable) {
            ContentResolver.addPeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY, SYNC_INTERVAL);
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, true);

        } else {
            ContentResolver.removePeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY);
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, false);
        }

        // We cannot instantly check weather addPeriodicSync did it's job as this seems to be async.
        // For this reason we have a test to ensure this works: WifiSurveyorTest.testSetConnected()
    }

    /**
     * This method must not be called before {@link #startSurveillance(Account)} linked a currentSynchronizationAccount.
     * <p>
     * If you change the implementation of this method, make sure you adjust
     * {@link SyncAdapter#isConnected(Account, String)} accordingly.
     * <p>
     * This method allows implementing apps (CY) to only trigger sync manually when connected or else show an info.
     *
     * @return True if the device is connected to a syncable connection.
     */
    public boolean isConnected() {

        // We cannot instantly check addPeriodicSync as this seems to be async. For this reason we have a test to ensure
        // it's set to the same state as syncAutomatically: WifiSurveyorTest.testSetConnected()

        return ContentResolver.getSyncAutomatically(currentSynchronizationAccount, authority);
    }

    /**
     * Checks if the synchronization is enabled or disabled *completely* for the sync account.
     * <p>
     * <b>Attention:</b>
     * If you want to check if periodic ("auto") sync is enabled which is automatically set when the network state
     * changes, see {@link #isConnected()}.
     * <p>
     * This method must be called after {@link #startSurveillance(Account)} is called.
     *
     * @return True if synchronization is enabled
     */
    @SuppressWarnings("unused") // SDK implementing apps may check weather sync is disabled completely
    public boolean isSyncEnabled() {
        return ContentResolver.getIsSyncable(currentSynchronizationAccount, authority) == 1;
    }

    /**
     * Allows to enable or disable synchronization completely.
     * <p>
     * This method must not be called before {@link #startSurveillance(Account)} was called. You can also use the
     * {@link #setSyncEnabled(Account, boolean)} which does not have this requirement.
     * <p>
     * Make sure you have read the documentation of {@link #makeAccountSyncable(Account, boolean)}.
     *
     * @param enabled True if synchronization should be enabled
     */
    @SuppressWarnings("unused") // SDK implementing apps may want to disable synchronization completely
    public void setSyncEnabled(final boolean enabled) {
        setSyncEnabled(currentSynchronizationAccount, enabled);
    }

    /**
     * Allows to enable or disable synchronization completely.
     * <p>
     * This second interface for setSyncEnabled() with the account parameter is required as
     * {@code MovebisDataCapturingService#registerJwtAuthToken()} just activate the account before
     * the account is linked as currentSynchronizationAccount by {@link #startSurveillance(Account)}.
     *
     * @param account The {@code Account} to update.
     * @param enabled True if synchronization should be enabled
     */
    public void setSyncEnabled(@NonNull final Account account, final boolean enabled) {
        ContentResolver.setIsSyncable(account, authority, enabled ? 1 : 0);
    }

    public boolean isSyncOnUnMeteredNetworkOnly() {
        return syncOnUnMeteredNetworkOnly;
    }
}
