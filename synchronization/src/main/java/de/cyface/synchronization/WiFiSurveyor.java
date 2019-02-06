package de.cyface.synchronization;

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static de.cyface.synchronization.Constants.TAG;

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
import de.cyface.utils.Validate;

/**
 * An instance of this class is responsible for surveying the state of the devices WiFi connection. If WiFi is active,
 * data is going to be synchronized continuously.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.2.2
 * @since 2.0.0
 */
public class WiFiSurveyor extends BroadcastReceiver {

    /**
     * The number of seconds in one minute. This value is used to calculate the data synchronisation interval.
     */
    private static final long SECONDS_PER_MINUTE = 60L;
    /**
     * The data synchronisation interval in minutes.
     */
    private static final long SYNC_INTERVAL_IN_MINUTES = 60L; // There is no particular reason for choosing 60 minutes.
    // It seems reasonable and can be changed in the future.
    /**
     * Since we need to specify the sync interval in seconds, this constant transforms the interval in minutes to
     * seconds using {@link #SECONDS_PER_MINUTE}.
     */
    static final long SYNC_INTERVAL = SYNC_INTERVAL_IN_MINUTES * SECONDS_PER_MINUTE;
    /**
     * The <code>Account</code> currently used for data synchronization or <code>null</code> if no such
     * <code>Account</code> has been set.
     */
    private Account currentSynchronizationAccount;
    /**
     * The current Android context (i.e. Activity or Service).
     */
    private WeakReference<Context> context;
    /**
     * A flag that might be queried to see whether synchronization is active or not. This is <code>true</code> if
     * synchronization is active and <code>false</code> otherwise.
     */
    private boolean synchronizationIsActive;
    /**
     * If <code>true</code> the <code>MovebisDataCapturingService</code> synchronizes data only if
     * connected to a WiFi network; if <code>false</code> it synchronizes as soon as a data connection is
     * available. The second option might use up the users data plan rapidly so use it sparingly.
     */
    private boolean syncOnWiFiOnly;
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
        this.syncOnWiFiOnly = true;
        this.authority = authority;
        this.accountType = accountType;
    }

    /**
     * Starts the WiFi* connection status surveillance. If a WiFi connection is active data synchronization is started.
     * If the WiFi goes back down synchronization is deactivated.
     * <p>
     * The method also schedules an immediate synchronization run after the WiFi has been connected.
     * <p>
     * ATTENTION: If you use this method do not forget to call {@link #stopSurveillance()}, at some time in the future
     * or you will waste system resources.
     * <p>
     * ATTENTION: Starting at version {@link Build.VERSION_CODES#LOLLIPOP} and higher instead of expecting only "WiFi"
     * connections as "not metered" we use the {@link NetworkCapabilities#NET_CAPABILITY_NOT_METERED} as synonym as
     * suggested by Android.
     *
     * @param account Starts surveillance of the WiFi connection status for this account.
     * @throws SynchronisationException If no current Android <code>Context</code> is available.
     */
    public void startSurveillance(final @NonNull Account account) throws SynchronisationException {
        if (context.get() == null) {
            throw new SynchronisationException("No valid context available!");
        }

        if (isConnected()) {
            ContentResolver.requestSync(account, authority, Bundle.EMPTY);
        }
        currentSynchronizationAccount = account;

        // FIXME: We want to test this on newer devices if we want to leave this in!
        // Roboelectric is currently only testing the deprecated code, see class documentation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
            if (syncOnWiFiOnly) {
                // Cleaner is "NET_CAPABILITY_NOT_METERED" but this is not yet available on the client (unclear why)
                requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            }
            networkCallback = new NetworkCallback(this, currentSynchronizationAccount, authority);
            connectivityManager.registerNetworkCallback(requestBuilder.build(), networkCallback);
        } else {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            context.get().registerReceiver(this, intentFilter);
        }
    }

    /**
     * Stops surveillance of the devices connection status. This frees up all used system resources.
     *
     * @throws SynchronisationException If no current Android <code>Context</code> is available.
     */
    @SuppressWarnings({"unused", "WeakerAccess"}) // TODO: because ...?
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
     * Schedules data synchronization with the provided account for right now. This does not mean synchronization is
     * going to start immediately. The Android system still decides when it is convenient.
     *
     * @param account The <code>Account</code> to use or synchronization.
     */
    public void scheduleSyncNow(final @NonNull Account account) {
        if (isConnected()) {
            ContentResolver.requestSync(account, authority, Bundle.EMPTY);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (currentSynchronizationAccount == null) {
            Log.e(TAG, "No account for data synchronization registered with this service. Aborting synchronization.");
            return;
        }

        final String action = intent.getAction();
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            if (isConnected()) {
                // Try synchronization periodically
                boolean cyfaceAccountSyncIsEnabled = ContentResolver.getSyncAutomatically(currentSynchronizationAccount,
                        authority);
                boolean masterAccountSyncIsEnabled = ContentResolver.getMasterSyncAutomatically();

                if (cyfaceAccountSyncIsEnabled && masterAccountSyncIsEnabled) {
                    ContentResolver.addPeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY,
                            SYNC_INTERVAL);
                }
                synchronizationIsActive = true;
            } else {
                // wifi connection was lost
                ContentResolver.removePeriodicSync(currentSynchronizationAccount, authority, Bundle.EMPTY);
                synchronizationIsActive = false;
            }
        }
    }

    /**
     * Deletes a Cyface account from the Android <code>Account</code> system. Does silently nothing if no such
     * <code>Account</code> exists.
     *
     * @param username The username of the account to delete.
     */
    public void deleteAccount(final @NonNull String username) {
        AccountManager accountManager = AccountManager.get(context.get());
        Account account = new Account(username, accountType);

        if (!ContentResolver.getPeriodicSyncs(account, authority).isEmpty()) {
            ContentResolver.removePeriodicSync(account, authority, Bundle.EMPTY);
        }

        synchronized (this) {
            if (Build.VERSION.SDK_INT < 22) {
                accountManager.removeAccount(account, null, null);
            } else {
                accountManager.removeAccountExplicitly(account);
            }
            currentSynchronizationAccount = null;
        }
    }

    /**
     * This method retrieves an <code>Account</code> from the Android account system. If the <code>Account</code>
     * does
     * not exist it is created before returning it.
     *
     * @param username The username of the account you would like to get.
     * @return The requested <code>Account</code>
     */
    public Account getOrCreateAccount(final @NonNull String username) throws SynchronisationException {
        AccountManager am = AccountManager.get(context.get());
        Account[] cyfaceAccounts = am.getAccountsByType(accountType);
        if (cyfaceAccounts.length == 0) {
            synchronized (this) {
                Account newAccount = new Account(username, accountType);
                boolean newAccountAdded = am.addAccountExplicitly(newAccount, null, Bundle.EMPTY);
                if (!newAccountAdded) {
                    throw new SynchronisationException("Unable to add dummy account!");
                }
                ContentResolver.setIsSyncable(newAccount, authority, 1);
                ContentResolver.setSyncAutomatically(newAccount, authority, true);
                return newAccount;
            }
        } else {
            return cyfaceAccounts[0];
        }
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
     * Checks whether the device is connected with a WiFi Network or not.
     *
     * @return <code>true</code> if WiFi is available; <code>false</code> otherwise.
     */
    @SuppressWarnings("WeakerAccess") // TODO: because?
    public boolean isConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Validate.notNull(connectivityManager); // for testing
            final Network activeNetwork = connectivityManager.getActiveNetwork();
            final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            final NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (networkCapabilities == null) {
                // This happened on Xiaomi Mi A2 Android 9.0 in the morning after capturing during the night
                return false;
            }

            final boolean isNotMeteredNetwork = networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED);
            final boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            final boolean result = isConnected && (isNotMeteredNetwork || !syncOnWiFiOnly);
            Log.d(TAG, "allowSync: " + result + " (" + (isNotMeteredNetwork ? "not" : "") + "metered)");

            return result;
        } else {
            final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            final boolean isWifiNetwork = activeNetworkInfo != null
                    && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            final boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            return isConnected && (isWifiNetwork || !syncOnWiFiOnly);
        }
    }

    /**
     * @return A flag that might be queried to see whether synchronization is active or not. This is <code>true</code>
     *         if
     *         synchronization is active and <code>false</code> otherwise.
     */
    @SuppressWarnings("WeakerAccess") // TODO because?
    public boolean synchronizationIsActive() {
        return synchronizationIsActive;
    }

    /**
     * Sets whether this <code>MovebisDataCapturingService</code> should synchronize data only on WiFi or on all data
     * connections.
     *
     * @param state If <code>true</code> the <code>MovebisDataCapturingService</code> synchronizes data only if
     *            connected to a WiFi network; if <code>false</code> it synchronizes as soon as a data connection is
     *            available. The second option might use up the users data plan rapidly so use it sparingly. Default
     *            value is <code>true</code>.
     */
    public void syncOnWiFiOnly(boolean state) {
        syncOnWiFiOnly = state;
    }

    void setSynchronizationIsActive(boolean synchronizationIsActive) {
        this.synchronizationIsActive = synchronizationIsActive;
    }
}
