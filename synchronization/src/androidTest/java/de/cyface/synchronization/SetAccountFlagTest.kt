/*
 * Copyright 2019-2025 Cyface GmbH
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
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.testutils.SharedTestUtils.cleanupOldAccounts
import de.cyface.utils.Validate.isTrue
import de.cyface.utils.Validate.notNull
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
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
 * Tests the correct functionality of the [WiFiSurveyor] class.
 *
 * The tests in this class require an emulator or a real device.
 *
 * @author Armin Schnabel
 * @version 2.0.4
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4::class)
@Ignore("This test is flaky on a local emulator and physical device")
class SetAccountFlagTest {
    /**
     * An object of the class under test.
     */
    private var oocut: WiFiSurveyor? = null

    /**
     * The [AccountManager] to check which accounts are registered.
     */
    private var accountManager: AccountManager? = null

    /**
     * The `HandlerThread` which can be used wait for the `Account` flags to change while the main
     * test thread is locked and waits. **Attention:** Make sure you call `HandlerThread#quit()`
     * when you are done using it.
     */
    private var handlerThread: HandlerThread? = null

    /**
     * The `Handler` used to wait for the `Account` flags to change while the main test thread is
     * locked and waits.
     */
    private var handler: Handler? = null

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        notNull(connectivityManager)

        oocut =
            WiFiSurveyor(context, connectivityManager, TestUtils.AUTHORITY, TestUtils.ACCOUNT_TYPE)

        // Ensure reproducibility
        accountManager = AccountManager.get(context)
        cleanupOldAccounts(accountManager!!, TestUtils.ACCOUNT_TYPE, TestUtils.AUTHORITY)

        // Create a thread to wait for the {@code Account} flags to change while the main test
        // thread is locked
        handlerThread = HandlerThread("de.cyface.sync.test.AccountFlagsChangeChecker")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @After
    fun tearDown() {
        val oldAccounts = accountManager!!.getAccountsByType(TestUtils.ACCOUNT_TYPE)
        if (oldAccounts.isNotEmpty()) {
            for (oldAccount in oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, TestUtils.AUTHORITY, Bundle.EMPTY)
                isTrue(accountManager!!.removeAccountExplicitly(oldAccount))
            }
        }
        oocut = null
        handlerThread!!.quit()
    }

    /**
     * Tests that marking the connection as syncable using the account flags works.
     *
     * This test reproduced MOV-635 where the periodic sync flag did not change because
     * syncAutomatically was not set. This bug was only reproducible in integration environment
     * (device and emulator) but not as robolectric test.
     *
     * This test may be flaky on a **real** device when the network changes during the test.
     *
     * This test is flaky on the Github CI emulator, but work on local emulators [STAD-425].
     */
    @Test
    @FlakyTest
    @Throws(InterruptedException::class)
    fun testSetConnected() {
        // Arrange - nothing to do

        // Act 1: Create new Account
        val account = oocut!!.createAccount(TestUtils.DEFAULT_USERNAME, null)
        val checkerParameters1 = createAccountFlagCheckerParameters(
            account = account,
            syncAutomaticallyEnabled = false,
            periodicSyncEnabled = false,
            actionName = "createAccount()",
        )

        // Instead of calling startSurveillance as in production we directly call it's
        // implementation. Without the networkCallback or networkConnectivity BroadcastReceiver as
        // this would make this test flaky when the network changes during the test
        oocut!!.currentSynchronizationAccount = account
        oocut!!.scheduleSyncNow()

        // Assert 1: Ensure the new account and the isConnected() is in the expected default state
        assertThatAccountFlagsChangeAsExpected(
            account = account,
            syncAutomaticallyEnabled = false,
            periodicSyncEnabled = false,
            actionName = "createAccount()",
            checkerParameters = checkerParameters1,
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(false)))

        // Act 2: setConnected to true
        oocut!!.setConnected(true)
        val checkerParameters2 = createAccountFlagCheckerParameters(
            account = account,
            syncAutomaticallyEnabled = true,
            periodicSyncEnabled = true,
            actionName = "setConnected(true)",
        )

        // Assert 2: Ensure the flags are set as expected
        assertThatAccountFlagsChangeAsExpected(
            account = account,
            syncAutomaticallyEnabled = true,
            periodicSyncEnabled = true,
            actionName = "setConnected(true)",
            checkerParameters = checkerParameters2,
        )
        assertThat(oocut!!.isConnected, `is`(equalTo(true)))

        // Act 3: setConnected to false
        oocut!!.setConnected(false)
        val checkerParameters3 = createAccountFlagCheckerParameters(
            account = account,
            syncAutomaticallyEnabled = false,
            periodicSyncEnabled = false,
            actionName = "setConnected(false)",
        )
        assertThatAccountFlagsChangeAsExpected(
            account = account,
            syncAutomaticallyEnabled = false,
            periodicSyncEnabled = false,
            actionName = "setConnected(false)",
            checkerParameters = checkerParameters3,
        )
        assertThat(oocut!!.isConnected,`is`(equalTo(false)))
    }

    /**
     * Creates a new `Runnable` which sleeps and waits a couple of times ([TIMEOUT_TIME] long)
     * until the `Account` flags are in the expected state or else fails. It also creates new a
     * `Lock` and `Condition` which is used by the checker.
     *
     * @param account The `Account` to watch for account flag updates
     * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
     * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
     * @param actionName String that identifies the action after which we now wait for changes,
     * e.g. "createAccount()"
     * @return The `Runnable`
     */
    private fun createAccountFlagCheckerParameters(
        account: Account,
        syncAutomaticallyEnabled: Boolean, periodicSyncEnabled: Boolean,
        actionName: String
    ): CheckerParameters {
        val checkerParameters = CheckerParameters()
        val accountFlagChecker = Runnable {
            val iterations = 10 // random number of iterations
            for (checks in 0 until iterations) {
                val tagPrefix = actionName + " (iteration " + (checks + 1) + "): "
                try {
                    Thread.sleep(TIMEOUT_TIME * 1000 / iterations)
                } catch (e: InterruptedException) {
                    throw IllegalStateException(e)
                }

                // Check if the account flags are in the expected state
                val isFlagsAlreadySet = isAccountFlagsSet(
                    account,
                    syncAutomaticallyEnabled,
                    periodicSyncEnabled,
                )
                if (!isFlagsAlreadySet) {
                    Log.d(
                        TAG,
                        "$tagPrefix Account flags changed but are still not in the expected state, continue waiting."
                    )
                    continue
                }

                Log.d(
                    TAG,
                    "$tagPrefix Account flags are now in the expected state, sending signal to release lock."
                )
                checkerParameters.lock.lock()
                try {
                    checkerParameters.condition.signal()
                } finally {
                    checkerParameters.lock.unlock()
                }
                return@Runnable
            }
            Assert.fail(
                "$actionName: Account flag did not change to the expected state within $TIMEOUT_TIME seconds"
            )
        }
        checkerParameters.runnable = accountFlagChecker
        return checkerParameters
    }

    /**
     * Ensures that the `Account` flags are in the expected state or, if not, locks and waits until
     * the change happens asynchronously.
     *
     * Asserts that this actually happens.
     *
     * @param account The `Account` to watch for account flag updates
     * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
     * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
     * @param actionName String that identifies the action after which we now wait for changes,
     * e.g. "createAccount()"
     * @param checkerParameters The [CheckerParameters] used to wait for and signal when the
     * `Account` flags are in the expected state.
     */
    @Throws(InterruptedException::class)
    private fun assertThatAccountFlagsChangeAsExpected(
        account: Account,
        syncAutomaticallyEnabled: Boolean,
        periodicSyncEnabled: Boolean,
        actionName: String,
        checkerParameters: CheckerParameters
    ) {
        val isFlagsAlreadySet =
            isAccountFlagsSet(account, syncAutomaticallyEnabled, periodicSyncEnabled)
        if (isFlagsAlreadySet) {
            Log.v(TAG, "$actionName: Account flags in the expected state")
            return
        }

        Log.v(
            TAG,
            "$actionName: Account flags are not in the expected state, waiting for Account flags to change."
        )
        // OnAccountsUpdatesListener is not called on Account flag changes (probably only when
        // accounts are added or deleted) for this reason we implemented a simple sleep and check
        // runnable until we find a better way
        handler!!.post(checkerParameters.runnable!!)
        lockAndWaitForConditionSignal(checkerParameters)
    }

    /**
     * Locks the thread and waits for the `Condition` signal to be sent.
     *
     * Asserts that this actually happens.
     *
     * @param checkerParameters The [CheckerParameters] used to wait for and signal when the
     * `Account` flags are in the expected state.
     */
    @Throws(InterruptedException::class)
    private fun lockAndWaitForConditionSignal(checkerParameters: CheckerParameters) {
        // Now lock and wait for the signal

        checkerParameters.lock.lock()
        try {
            // Ensure the signal arrives within the timeout (or else it will probably never happen)
            assertThat(
                checkerParameters.condition.await(TIMEOUT_TIME, TimeUnit.SECONDS),
                `is`(equalTo(true))
            )
        } finally {
            checkerParameters.lock.unlock()
        }
    }

    /**
     * A class which holds parameters so that they can be returned as one object by a method.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 4.0.0
     */
    private class CheckerParameters {
        /**
         * Lock used to synchronize the test case with the account manager.
         */
        val lock: Lock = ReentrantLock()

        /**
         * Condition waiting for the account manager listener to inform this test that the account
         * changed.
         */
        val condition: Condition = lock.newCondition()

        /**
         * A `Runnable` which sleeps and waits a couple of times ([TIMEOUT_TIME] long) to checks
         * for `Account` flags to be in the expected state or else fails.
         */
        var runnable: Runnable? = null
    }

    companion object {
        /**
         * The number of seconds to wait for the account flag to be set.
         */
        private const val TIMEOUT_TIME = 5L

        /**
         * Logging TAG to identify logs associated with this test.
         */
        private const val TAG = TestUtils.TAG

        /**
         * Checks synchronously weather the account flags used by [WiFiSurveyor.isConnected] are in
         * the expected state.
         *
         * See [WiFiSurveyor.makeAccountSyncable] for details.
         *
         * **Attention:** Never use this method in production as the periodicSync flags are set
         * async by the system so we can never be sure if they are already set or not. For this
         * reason we have the [testSetConnected] test.
         *
         * @param account The `Account` to be checked.
         * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
         * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
         * @return True if the flags are in the expected state.
         */
        private fun isAccountFlagsSet(
            account: Account, syncAutomaticallyEnabled: Boolean,
            periodicSyncEnabled: Boolean
        ): Boolean {
            val periodicSyncRegisteredState =
                ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY)
                    .size > 0
            val autoSyncEnabledState =
                ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY)

            return autoSyncEnabledState == syncAutomaticallyEnabled &&
                    periodicSyncRegisteredState == periodicSyncEnabled
        }
    }
}
