/*
 * Copyright 2017-2024 Cyface GmbH
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
package de.cyface.synchronization

import android.accounts.Account
import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.Validate.isTrue
import de.cyface.utils.Validate.notNull
import java.lang.ref.WeakReference

/**
 * An instance of this class is responsible for surveying the state of the devices WiFi connection. If WiFi is active,
 * data is going to be synchronized continuously.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
class WiFiSurveyor(
    context: Context,
    /**
     * The Android `ConnectivityManager` used to check the device's current connection status.
     */
    val connectivityManager: ConnectivityManager,
    /**
     * The `ContentProvider` authority used by this service to store and read data. See the
     * [Android documentation](https://developer.android.com/guide/topics/providers/content-providers.html)
     * for further information.
     */
    private val authority: String,
    /**
     * A `String` identifying the account type of the accounts to use for data synchronization.
     */
    private val accountType: String
) : BroadcastReceiver() {
    /**
     * The `Account` currently used for data synchronization or `null` if no such
     * `Account` has been set.
     */
    var currentSynchronizationAccount: Account? = null

    /**
     * The current Android context (i.e. Activity or Service).
     */
    private val context = WeakReference(context)

    /**
     * If `true` the `MovebisDataCapturingService` synchronizes data only if
     * connected to a WiFi network; if `false` it synchronizes as soon as a data connection is
     * available. The second option might use up the users data plan rapidly so use it sparingly.
     */
    private var syncOnUnMeteredNetworkOnly = true

    /**
     * A callback which handles changes on the connectivity
     */
    private var networkCallback: NetworkCallback? = null

    /**
     * Starts the connection status surveillance. If a syncable connection is active data synchronization is started.
     * If the connection goes back down synchronization is deactivated.
     *
     *
     * You can allow metered connections as syncable by setting [.setSyncOnUnMeteredNetworkOnly] to
     * false. The default value is true.
     *
     *
     * The method also schedules an immediate synchronization run after the syncable connection has been connected.
     *
     *
     * **ATTENTION:** If you use this method do not forget to call [.stopSurveillance], at some time in the
     * future or you will waste system resources.
     *
     *
     * **ATTENTION:** Starting at [.MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG] we use
     * `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` instead of `NetworkCapabilities#TRANSPORT_WIFI`
     * to determine if a connection is syncable.
     *
     * @param account Starts surveillance of the WiFi connection status for this account.
     * @throws SynchronisationException If no current Android `Context` is available.
     */
    @Throws(SynchronisationException::class)
    fun startSurveillance(account: Account) {
        if (context.get() == null) {
            throw SynchronisationException("No valid context available!")
        }
        currentSynchronizationAccount = account

        // To make sure the state is correct before we start listening to network changes
        if (!isConnectedToSyncableNetwork) {
            Log.v(TAG, "startSurveillance: not connected to syncable network")
            isTrue(setConnected(false))
        } else {
            Log.v(TAG, "startSurveillance: connected to syncable network")
            isTrue(setConnected(true))
            scheduleSyncNow() // Needs to be called after currentSynchronizationAccount is set
        }

        // Robolectric is currently only testing the deprecated code, see class documentation
        val requestBuilder = NetworkRequest.Builder()
        if (syncOnUnMeteredNetworkOnly) {
            val isUsingUnMeteredCheckInsteadOfWifi =
                Build.VERSION.SDK_INT >= MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG

            if (isUsingUnMeteredCheckInsteadOfWifi) {
                requestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } else {
                requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            }
            Log.v(
                TAG,
                "startSurveillance for " + (if (isUsingUnMeteredCheckInsteadOfWifi) "unMetered" else "wifi")
                        + " networks only"
            )
        }
        networkCallback = NetworkCallback(this)
        connectivityManager.registerNetworkCallback(requestBuilder.build(), networkCallback!!)
    }

    /**
     * Stops surveillance of the devices connection status. This frees up all used system resources.
     *
     *
     * PeriodicSync does not have to be removed in here.
     * - setSyncOnUnMeteredNetworkOnly removes the periodic sync itself
     * - UI.onDestroyView does not expect periodic sync to be removed (tested in MOV-619).
     * This way synchronization also works after onDestroyView was called when there is still syncable connection.
     * If the syncable connection is lost after onDestroyView is called sync does not happen.
     *
     * @throws SynchronisationException If no current Android `Context` is available.
     */
    @Suppress("unused") // Used by CyfaceDataCapturingService
    @Throws(SynchronisationException::class)
    fun stopSurveillance() {
        if (context.get() == null) {
            throw SynchronisationException("No valid context available!")
        }

        if (networkCallback == null) {
            Log.w(TAG, "Unable to unregister NetworkCallback because it's null.")
            return
        }
        connectivityManager.unregisterNetworkCallback(networkCallback!!)
        networkCallback = null

        // This interrupts ongoing synchronization or else it may crash because required data is missing (token,
        // account)
        ContentResolver.cancelSync(currentSynchronizationAccount, authority)
    }

    /**
     * Schedules data synchronization for right now. This does not mean synchronization is going to start immediately.
     * The Android system still decides when it is convenient.
     */
    fun scheduleSyncNow() {
        if (currentSynchronizationAccount == null) {
            Log.w(TAG, "scheduleSyncNow aborted, not account available.")
            return
        }

        if (!isConnectedToSyncableNetwork) {
            Log.d(TAG, "scheduleSyncNow aborted, not connected to syncable network")
            return
        }

        Log.v(TAG, "scheduleSyncNow")
        val params = Bundle()
        params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        ContentResolver.requestSync(currentSynchronizationAccount, authority, params)
    }

    override fun onReceive(context: Context, intent: Intent) {
        notNull(intent.action)
        val connectivityChanged = intent.action == ConnectivityManager.CONNECTIVITY_ACTION
        isTrue(connectivityChanged) // We registered only this action so this should always be true

        if (currentSynchronizationAccount == null) {
            Log.e(
                TAG,
                "No account for data synchronization registered with this service. Aborting synchronization."
            )
            return
        }

        // Syncable ("not metered") filter is already included
        val syncableConnectionLost = isConnected && !isConnectedToSyncableNetwork
        val syncableConnectionEstablished = !isConnected && isConnectedToSyncableNetwork

        if (syncableConnectionEstablished) {
            Log.v(TAG, "connectionEstablished: setConnected to true")
            setConnected(true)
        } else if (syncableConnectionLost) {
            Log.v(TAG, "connectionLost: setConnected to false.")
            setConnected(false)
        }
    }

    /**
     * Deletes a Cyface account from the Android `Account` system. Does silently nothing if no such
     * `Account` exists.
     *
     *
     * **Attention:** You need to call [.stopSurveillance] before calling this method as the
     * [WiFiSurveyor] surveillance expects a registered account to work.
     *
     *
     * **ATTENTION:** SDK implementing apps which cannot use this method to remove an account need to call
     * `ContentResolver#removePeriodicSync()` themselves.
     *
     * @param username The username of the account to delete.
     */
    @Suppress("unused") // {@link MovebisDataCapturingService} uses this to deregister a token
    fun deleteAccount(username: String) {
        val accountManager = AccountManager.get(context.get())
        val account = Account(username, accountType)

        ContentResolver.removePeriodicSync(account, authority, Bundle.EMPTY)

        synchronized(this) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccount(account, null, null)
            } else {
                accountManager.removeAccountExplicitly(account)
            }
            currentSynchronizationAccount = null
        }
    }

    /**
     * Creates a new `Account` which is required for the [WiFiSurveyor] to work.
     *
     * @param username The username of the account to be created.
     * @param password The password of the account to be created. May be null if a custom [CyfaceAuthenticator] is
     * used instead of a LoginActivity to return tokens as in `MovebisDataCapturingService`.
     * @return The created `Account`
     */
    @Suppress("unused") // Is used by MovebisDataCapturingService
    fun createAccount(username: String, password: String?): Account {
        val accountManager = AccountManager.get(context.get())
        val newAccount = Account(username, accountType)

        synchronized(this) {
            // When the account already exists this softly ignores this to support MovebisDataCapturingService
            accountManager.addAccountExplicitly(newAccount, password, Bundle.EMPTY)
            isTrue(accountManager.getAccountsByType(accountType).size == 1)
            Log.v(TAG, "New account added")
            makeAccountSyncable(newAccount, true)
        }

        return newAccount
    }

    /**
     * Sets up an already existing `Account` to work with the [WiFiSurveyor].
     *
     *
     * **Attention:** SDK implementing apps need to use this method if they cannot use
     * [WiFiSurveyor.createAccount].
     *
     *
     * **Attention:** Read the following before you change how we mark accounts as syncable or how we mark that a
     * syncable connection is available!
     *
     *
     * **Mark connection as syncable**
     * - Both, `ContentResolver#addPeriodicSync()` and `ContentResolver#setSyncAutomatically()`,
     * are automatically added or removed via [NetworkCallback] or
     * [WiFiSurveyor.onReceive], depending on the API level.
     * - The state of `ContentResolver#addPeriodicSync()` and `ContentResolver#setSyncAutomatically()`
     * define if a syncable connection is available (depending on [.setSyncOnUnMeteredNetworkOnly]).
     * - never *update only _one_ of both*, `ContentResolver#addPeriodicSync()` and
     * `ContentResolver#setSyncAutomatically()`, as this produced MOV-535, MOV-609 and MOV-635.
     * - `WiFiSurveyorTest#testSetConnected()` showed that addPeriodicSync does not happen instantly
     * which is why that test checks that both flags are set identically and for the same reason we can only
     * check `ContentResolver#getSyncAutomatically()` in [.isConnected].
     *
     *
     * **Disabled synchronization completely**
     * - Synchronization is enabled by default.
     * - To disable synchronization *completely*, use [.setSyncEnabled]} which uses
     * the `ContentResolver#setIsSyncable()` flag.
     *
     * @param account The `Account` to be used for synchronization
     * @param enabled True if the synchronization should be enabled
     */
    @Suppress("unused") // Used by CyfaceDataCapturingService
    fun makeAccountSyncable(account: Account, enabled: Boolean) {
        ContentResolver.setSyncAutomatically(account, authority, false)
        setSyncEnabled(account, enabled)

        // We cannot validate here synchronously that the periodicSync flag was set as the setter is async.
        // For this reason we have the SetConnectedTest cases.
    }

    val account: Account
        /**
         * This method retrieves an `Account` from the Android account system. If the `Account`
         * does not exist it throws an IllegalStateException as we require the default SDK using apps to have exactly one
         * account created in advance.
         *
         * @return The only `Account` existing
         */
        get() {
            val accountManager = AccountManager.get(context.get())
            val cyfaceAccounts = accountManager.getAccountsByType(accountType)
            check(cyfaceAccounts.size != 0) { "No cyface account exists." }
            check(cyfaceAccounts.size <= 1) { "More than one cyface account exists." }
            return cyfaceAccounts[0]
        }

    val isConnectedToSyncableNetwork: Boolean
        /**
         * This method must only be used internally from the [NetworkCallback] and
         * [WiFiSurveyor.onReceive] on connection status changes and when instant synchronization is
         * requested using [.scheduleSyncNow] .
         *
         *
         * Depending on the result of this method those callers allow and schedule or disallow and de-schedule
         * synchronization.
         *
         *
         * All other interested parties must use [.isConnected] instead.
         *
         * @return `true` if a "syncable" connection is available, depending on the
         * [.setSyncOnUnMeteredNetworkOnly] settings.
         */
        get() {
            notNull(connectivityManager)
            val activeNetworkInfo = connectivityManager.activeNetworkInfo

            // We use the new code only on Android 8 and above as Wifi networks are seen as metered in 6.0.1 (MOV-568)
            val isUnMeteredNetwork: Boolean
            if (Build.VERSION.SDK_INT >= MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG) {
                val activeNetwork = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (networkCapabilities == null) {
                    Log.w(
                        TAG,
                        "isConnectedToSyncableNetwork: returning false as networkCapabilities is null"
                    )
                    // This happened on Xiaomi Mi A2 Android 9.0 in the morning after capturing during the night
                    return false
                }
                isUnMeteredNetwork =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } else {
                isUnMeteredNetwork = (activeNetworkInfo != null
                        && activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI)
            }

            val isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected
            val isSyncableConnection =
                isConnected && (isUnMeteredNetwork || !syncOnUnMeteredNetworkOnly)

            var networkType = "unconnected"
            if (isConnected) {
                val isUsingUnMeteredCheckInsteadWifi =
                    Build.VERSION.SDK_INT >= MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG
                networkType = if (isUnMeteredNetwork) {
                    if (isUsingUnMeteredCheckInsteadWifi) "unMetered" else "wifi"
                } else {
                    if (isUsingUnMeteredCheckInsteadWifi) "metered" else "mobileData"
                }
            }
            Log.v(
                TAG,
                "isConnectedToSyncableNetwork: " + isSyncableConnection + " (" + networkType + ", "
                        + (if (syncOnUnMeteredNetworkOnly) "with" else "without") + " syncOnUnMeteredNetworkOnly)"
            )
            return isSyncableConnection
        }

    /**
     * Sets whether synchronization should happen only on
     * `android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED`
     * networks or on all networks.
     *
     *
     * **ATTENTION:** Starting at version [.MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG] we use the
     * `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` flag instead of
     * `NetworkCapabilities#TRANSPORT_WIFI` as suggested by Android.
     *
     *
     * This method must be called after [.startSurveillance] is called.
     *
     * @param newState If `true` the [WiFiSurveyor] synchronizes data only if connected to a
     * `android.net.NetworkCapabilities#NET_CAPABILITY_NOT_METERED` network; if
     * `false` it synchronizes as soon as a data connection is available. The second option might use
     * up the users data plan rapidly so use it sparingly. The default value is `true`.
     * @throws SynchronisationException If no current Android `Context` is available.
     */
    @Throws(SynchronisationException::class)
    fun setSyncOnUnMeteredNetworkOnly(newState: Boolean) {
        // In case the restrictions got hardened (disallow metered networks) remove activated syncs

        val mobileDataIsNotAllowedAnymore = !syncOnUnMeteredNetworkOnly && newState
        if (mobileDataIsNotAllowedAnymore) {
            Log.d(
                TAG,
                "setSyncOnUnMeteredNetworkOnly: mobileDataIsNotAllowedAnymore, setConnected to false"
            )
            setConnected(false)
        }

        syncOnUnMeteredNetworkOnly = newState
        // This is required to update the NetworkCallback filter
        stopSurveillance()
        startSurveillance(currentSynchronizationAccount!!)
    }

    /**
     * Updates the settings for the sync account to indicate if there is currently a syncable connections or not.
     *
     *
     * Do not call this method before [.startSurveillance] linked a currentSynchronizationAccount.
     *
     *
     * **Attention:** Before you change the account flags usage, read [.makeAccountSyncable].
     *
     * @param enable True if `ContentResolver#addPeriodicSync()` should be activated or false if it
     * should be removed from the sync account.
     * @return `True` if the sync setting were changed successfully, `False` if there is currently no
     * `#currentSynchronizationAccount` registered.
     */
    fun setConnected(enable: Boolean): Boolean {
        // For some reasons callers such as NetworkCallback.onLost can still called even though we unregister
        // NetworkCallbacks before we call WifiSurveyor.deleteAccount(). So theoretically currentSynchronizationAccount
        // should not be null, but it happened anyway (MOV-764). Thus, we catch this softly.
        if (currentSynchronizationAccount == null) {
            Log.w(TAG, "setConnected ignored as currentSynchronizationAccount is null")
            return false
        }

        if (enable) {
            ContentResolver.addPeriodicSync(
                currentSynchronizationAccount,
                authority,
                Bundle.EMPTY,
                SYNC_INTERVAL
            )
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, true)
        } else {
            ContentResolver.removePeriodicSync(
                currentSynchronizationAccount,
                authority,
                Bundle.EMPTY
            )
            ContentResolver.setSyncAutomatically(currentSynchronizationAccount, authority, false)
        }
        Log.v(TAG, "setConnected to $enable")
        return true

        // We cannot instantly check weather addPeriodicSync did it's job as this seems to be async.
        // For this reason we have a test to ensure this works: WifiSurveyorTest.testSetConnected()
    }

    val isConnected: Boolean
        /**
         * This method must not be called before [.startSurveillance] linked a currentSynchronizationAccount.
         *
         *
         * If you change the implementation of this method, make sure you adjust
         * `SyncAdapter#isConnected(Account, String)` accordingly.
         *
         *
         * This method allows implementing apps (CY) to only trigger sync manually when connected or else show an info.
         *
         * @return True if the device is connected to a syncable connection.
         */
        get() =// We cannot instantly check addPeriodicSync as this seems to be async. For this reason we have a test to ensure
            // it's set to the same state as syncAutomatically: WifiSurveyorTest.testSetConnected()
            ContentResolver.getSyncAutomatically(currentSynchronizationAccount, authority)

    @get:Suppress("unused")
    @set:Suppress("unused")
    var isSyncEnabled: Boolean
        /**
         * Checks if the synchronization is enabled or disabled *completely* for the sync account.
         *
         *
         * **Attention:**
         * If you want to check if periodic ("auto") sync is enabled which is automatically set when the network state
         * changes, see [.isConnected].
         *
         *
         * This method must be called after [.startSurveillance] is called.
         *
         * @return True if synchronization is enabled
         */
        get() = ContentResolver.getIsSyncable(currentSynchronizationAccount, authority) == 1
        /**
         * Allows to enable or disable synchronization completely.
         *
         *
         * This method must not be called before [.startSurveillance] was called. You can also use the
         * [.setSyncEnabled] which does not have this requirement.
         *
         *
         * Make sure you have read the documentation of [.makeAccountSyncable].
         *
         * @param enabled True if synchronization should be enabled
         */
        set(enabled) {
            setSyncEnabled(currentSynchronizationAccount!!, enabled)
        }

    /**
     * Allows to enable or disable synchronization completely.
     *
     *
     * This second interface for setSyncEnabled() with the account parameter is required as
     * `MovebisDataCapturingService#registerJwtAuthToken()` just activate the account before
     * the account is linked as currentSynchronizationAccount by [.startSurveillance].
     *
     * @param account The `Account` to update.
     * @param enabled True if synchronization should be enabled
     */
    fun setSyncEnabled(account: Account, enabled: Boolean) {
        ContentResolver.setIsSyncable(account, authority, if (enabled) 1 else 0)
    }

    fun isSyncOnUnMeteredNetworkOnly(): Boolean {
        return syncOnUnMeteredNetworkOnly
    }

    companion object {
        /**
         * Logging TAG to identify logs associated with the [WiFiSurveyor].
         *
         *
         * SuppressWarnings because SDK implementing app (CY) uses this.
         */
        @Suppress("unused")
        val TAG: String = Constants.TAG + ".surveyor"

        /**
         * The number of seconds in one minute. This value is used to calculate the data synchronisation interval.
         */
        private const val SECONDS_PER_MINUTE = 60L

        /**
         * The data synchronisation interval in minutes.
         *
         *
         * **Attention:** Before you change this make sure you don't use a value lower than the minimum defined by
         * `ContentResolver#addPeriodicSync()`. So far the highest minimum of all APIs was 60 minutes
         * which is why we did choose this default value.
         */
        private const val SYNC_INTERVAL_IN_MINUTES = 60L

        /**
         * Since we need to specify the sync interval in seconds, this constant transforms the interval in minutes to
         * seconds using [.SECONDS_PER_MINUTE].
         */
        const val SYNC_INTERVAL: Long = SYNC_INTERVAL_IN_MINUTES * SECONDS_PER_MINUTE

        /**
         * A flag which shows if the code is using the `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` flag instead
         * of `NetworkCapabilities#TRANSPORT_WIFI` to determine if we are connected to a syncable network.
         *
         *
         * This should work starting with `Build.VERSION_CODES#M` but in our tests wifi networks where identifies as
         * "metered" on lower APIs (e.g. Android 6.0.1).
         */
        const val MINIMUM_VERSION_TO_USE_NOT_METERED_FLAG: Int = Build.VERSION_CODES.O
    }
}
