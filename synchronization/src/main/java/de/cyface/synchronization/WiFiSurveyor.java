package de.cyface.synchronization;

import java.lang.ref.WeakReference;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * An instance of this class is responsible for surveying the state of the devices WiFi connection. If WiFi is active,
 * data is going to be synchronized continuously.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class WiFiSurveyor extends BroadcastReceiver {
    /**
     * The <code>ContentProvider</code> authority used by this service to store and read data. See the
     * <a href="https://developer.android.com/guide/topics/providers/content-providers.html">Android documentation</a>
     * for further information.
     */
    final static String AUTHORITY = de.cyface.persistence.BuildConfig.provider;
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
    private static final long SYNC_INTERVAL = SYNC_INTERVAL_IN_MINUTES * SECONDS_PER_MINUTE;
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
     * Creates a new completely initialized <code>WiFiSurveyor</code> within the current Android context.
     *
     * @param context The current Android context (i.e. Activity or Service).
     */
    public WiFiSurveyor(final @NonNull Context context) {
        this.context = new WeakReference<>(context);
    }

    /**
     * Starts the WiFi connection status surveillance. If a WiFi connection is active data synchronization is started.
     * If the WiFi goes back down synchronization is deactivated.
     * <p>
     * The method also schedules an immediate synchronization run after the WiFi has been connected.
     *
     * @param account
     * @throws SynchronisationException
     */
    public void startSurveillance(final @NonNull Account account) throws SynchronisationException {
        if (context.get() == null) {
            throw new SynchronisationException("No valid context available!");
        }

        if (isConnectedToWifi()) {
            ContentResolver.requestSync(account, AUTHORITY, Bundle.EMPTY);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        currentSynchronizationAccount = account;
        context.get().registerReceiver(this, intentFilter);
    }

    /**
     * Schedules data synchronization with the provided account for right now. This does not mean synchronization is
     * going to start immediately. The Android system still decides when it is convenient.
     *
     * @param account The <code>Account</code> to use or synchronization.
     * @throws SynchronisationException If current network state is not accessible.
     */
    public void scheduleSyncNow(final @NonNull Account account) throws SynchronisationException {
        if (isConnectedToWifi()) {
            ContentResolver.requestSync(account, AUTHORITY, Bundle.EMPTY);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (currentSynchronizationAccount == null) {
            throw new IllegalStateException("No account for data synchronization registered with this service.");
        }

        final String action = intent.getAction();
        if (action != null && action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
            if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                // do stuff
                // Try synchronization periodically
                boolean cyfaceAccountSyncIsEnabled = ContentResolver.getSyncAutomatically(currentSynchronizationAccount,
                        AUTHORITY);
                boolean masterAccountSyncIsEnabled = ContentResolver.getMasterSyncAutomatically();

                if (cyfaceAccountSyncIsEnabled && masterAccountSyncIsEnabled) {
                    ContentResolver.addPeriodicSync(currentSynchronizationAccount, AUTHORITY, Bundle.EMPTY,
                            SYNC_INTERVAL);
                }
                synchronizationIsActive = true;
            } else {
                // wifi connection was lost
                ContentResolver.removePeriodicSync(currentSynchronizationAccount, AUTHORITY, Bundle.EMPTY);
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
        Account account = new Account(username, StubAuthenticator.ACCOUNT_TYPE);

        if (!ContentResolver.getPeriodicSyncs(account, AUTHORITY).isEmpty()) {
            ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);
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
        Account[] cyfaceAccounts = am.getAccountsByType(StubAuthenticator.ACCOUNT_TYPE);
        if (cyfaceAccounts.length == 0) {
            synchronized (this) {
                Account newAccount = new Account(username, StubAuthenticator.ACCOUNT_TYPE);
                boolean newAccountAdded = am.addAccountExplicitly(newAccount, null, Bundle.EMPTY);
                if (!newAccountAdded) {
                    throw new SynchronisationException("Unable to add dummy account!");
                }
                ContentResolver.setIsSyncable(newAccount, AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(newAccount, AUTHORITY, true);
                return newAccount;
            }
        } else {
            return cyfaceAccounts[0];
        }
    }

    /**
     * Checks whether the device is connected with a WiFi Network or not.
     *
     * @return <code>true</code> if WiFi is available; <code>false</code> otherwise.
     */
    public boolean isConnectedToWifi() throws SynchronisationException {
        ConnectivityManager connMgr = (ConnectivityManager)context.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            throw new SynchronisationException("Unable to get access to the connectivity information.");
        }
        NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

    /**
     * @return A flag that might be queried to see whether synchronization is active or not. This is <code>true</code>
     *         if
     *         synchronization is active and <code>false</code> otherwise.
     */
    public boolean synchronizationIsActive() {
        return synchronizationIsActive;
    }
}
