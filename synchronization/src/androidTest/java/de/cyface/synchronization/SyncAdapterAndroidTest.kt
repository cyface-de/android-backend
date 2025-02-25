/*
 * Copyright 2017 Cyface GmbH
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
import android.content.ContentResolver
import android.content.Context
import android.content.SyncStatusObserver
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.synchronization.TestUtils.ACCOUNT_TYPE
import de.cyface.synchronization.TestUtils.AUTHORITY
import de.cyface.synchronization.TestUtils.TAG
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests that the sync adapter implemented by this component gets called. This test is not so much
 * about transmitting data, but focuses on whether the sync adapter was implemented correctly.
 *
 * Currently the test calls the actual data transmission code and thus depends on a running server
 * instance. This makes the test large and flaky. Future implementation will hopefully remove this
 * dependency.
 *
 * Currently, Wi-Fi must be activated for this test to run through.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.7
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncAdapterAndroidTest {
    private var context: Context? = null
    private var accountManager: AccountManager? = null
    private var account: Account? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Ensure reproducibility
        accountManager = AccountManager.get(context)
        cleanupOldAccounts(accountManager!!, ACCOUNT_TYPE, AUTHORITY)

        // Add new sync account (usually done by DataCapturingService and WifiSurveyor)
        account = Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE)
        accountManager!!.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null)
    }

    @After
    fun tearDown() {
        val oldAccounts = accountManager!!.getAccountsByType(ACCOUNT_TYPE)
        if (oldAccounts.isNotEmpty()) {
            for (oldAccount in oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY)
                require(accountManager!!.removeAccountExplicitly(oldAccount))
            }
        }

        context = null
    }

    /**
     * This test case tests whether the sync adapter is called after a request for a direct synchronization.
     *
     * @throws InterruptedException Thrown if waiting for the sync adapter to report back is interrupted.
     */
    @Test
    @Ignore("This test is flaky on the local emulator")
    @Throws(InterruptedException::class)
    fun testRequestSync() {
        // Enable auto sync

        ContentResolver.setIsSyncable(account, AUTHORITY, 1)
        ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, 1)
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true)

        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()

        val observer = TestSyncStatusObserver(account!!, lock, condition)

        val statusChangeListenerHandle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
            observer
        )
        val params = Bundle()
        params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        ContentResolver.requestSync(account, AUTHORITY, params)

        lock.lock()
        try {
            if (!condition.await(10, TimeUnit.SECONDS)) {
                Assert.fail("Sync did not happen within the timeout time of 10 seconds.")
            }
        } finally {
            lock.unlock()
            ContentResolver.removeStatusChangeListener(statusChangeListenerHandle)
        }
        MatcherAssert.assertThat(observer.didSync(), CoreMatchers.`is`(CoreMatchers.equalTo(true)))
    }

    /**
     * A `SyncStatusObserver` used to get information about the synchronization adapter. This observer waits
     * for the sync adapter to start synchronization and stop it again before waking up the actual test case.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private class TestSyncStatusObserver
    /**
     * Creates a new completely initialized `TestSYncStatusObserver`.
     *
     * @param account An account required to identify the correct synchronization adapter call.
     * @param lock The lock used to synchronize the synchronization adapter with the calling test case.
     * @param syncCondition The condition under which to wake up the calling test case.
     */(
        /**
         * An account required to identify the correct synchronization adapter call.
         */
        private val account: Account,
        /**
         * The lock used to synchronize the synchronization adapter with the calling test case.
         */
        private val lock: Lock,
        /**
         * The condition under which to wake up the calling test case.
         */
        private val syncCondition: Condition
    ) : SyncStatusObserver {
        /**
         * The state of the synchronization. This is `true` if synchronization has been called;
         * `false` otherwise.
         */
        private var didSync = false

        override fun onStatusChanged(which: Int) {
            // Print synchronization info for debugging purposes.
            Log.d(TAG, "Sync Status changed! $which")
            val syncs = ContentResolver.getCurrentSyncs()
            Log.d(TAG, "Syncs: " + syncs.size)
            for (syncInfo in syncs) {
                Log.d(
                    TAG,
                    "Sync: ${syncInfo.account.name},${syncInfo.account.type}," +
                            "${syncInfo.authority},${syncInfo.startTime}"
                )
            }

            // Print synchronizing accounts for debugging purposes.
            val am = AccountManager.get(InstrumentationRegistry.getInstrumentation().targetContext)
            val accounts = am.getAccountsByType(ACCOUNT_TYPE)
            for (account in accounts) {
                Log.d(
                    TAG,
                    "Account: $account/active: " +
                            "${ContentResolver.isSyncActive(account, AUTHORITY)}/" +
                            "pending: ${ContentResolver.isSyncPending(account, AUTHORITY)}"
                )
            }

            // Actual check for test account
            didSync = didSync || ContentResolver.isSyncActive(account, AUTHORITY)
            // Calling thread will only be called if content resolver has been active but is not anymore.
            if (didSync && !ContentResolver.isSyncActive(account, AUTHORITY)) {
                lock.lock()
                try {
                    syncCondition.signal()
                } finally {
                    lock.unlock()
                }
            }
        }

        fun didSync(): Boolean {
            return didSync
        }
    }
}
