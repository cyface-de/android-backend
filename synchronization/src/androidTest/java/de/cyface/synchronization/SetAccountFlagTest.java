/*
 * Copyright 2019 Cyface GmbH
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

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the {@link WiFiSurveyor} class.
 * <p>
 * The tests in this class require an emulator or a real device.
 *
 * @author Armin Schnabel
 * @version 2.0.2
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4.class)
public class SetAccountFlagTest {

    /**
     * The number of seconds to wait for the account flag to be set.
     */
    private static final long TIMEOUT_TIME = 5L;
    /**
     * Logging TAG to identify logs associated with this test.
     */
    private final static String TAG = TestUtils.TAG;
    /**
     * An object of the class under test.
     */
    private WiFiSurveyor oocut;
    /**
     * The {@link AccountManager} to check which accounts are registered.
     */
    private AccountManager accountManager;
    /**
     * The {@code HandlerThread} which can be used wait for the {@code Account} flags to change while the main test
     * thread is locked and waits. <b>Attention:</b> Make sure you call {@code HandlerThread#quit()} when you are
     * done using it.
     */
    private HandlerThread handlerThread;
    /**
     * The {@code Handler} used to wait for the {@code Account} flags to change while the main test thread is locked and
     * waits.
     */
    private Handler handler;

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        Validate.notNull(connectivityManager);

        oocut = new WiFiSurveyor(context, connectivityManager, AUTHORITY, ACCOUNT_TYPE);

        // Ensure reproducibility
        accountManager = AccountManager.get(context);
        SharedTestUtils.cleanupOldAccounts(accountManager, ACCOUNT_TYPE, AUTHORITY);

        // Create a thread to wait for the {@code Account} flags to change while the main test thread is locked
        handlerThread = new HandlerThread("de.cyface.sync.test.AccountFlagsChangeChecker");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @After
    public void tearDown() {

        final Account[] oldAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (oldAccounts.length > 0) {
            for (Account oldAccount : oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY);
                Validate.isTrue(accountManager.removeAccountExplicitly(oldAccount));
            }
        }
        oocut = null;
        handlerThread.quit();
    }

    /**
     * Tests that marking the connection as syncable using the account flags works.
     * <p>
     * This test reproduced MOV-635 where the periodic sync flag did not change because syncAutomatically was not set.
     * This bug was only reproducible in integration environment (device and emulator) but not as robolectric test.
     * <p>
     * This test may be flaky on a <b>real</b> device when the network changes during the test.
     * <p>
     * This test is flaky on the Github CI emulator, but work on local emulators [STAD-425].
     */
    @Test
    @FlakyTest
    public void testSetConnected() throws InterruptedException {

        // Arrange - nothing to do

        // Act 1: Create new Account
        final Account account = oocut.createAccount(TestUtils.DEFAULT_USERNAME, null);
        final CheckerParameters checkerParameters1 = createAccountFlagCheckerParameters(account, false, false,
                "createAccount()");

        // Instead of calling startSurveillance as in production we directly call it's implementation
        // Without the networkCallback or networkConnectivity BroadcastReceiver as this would make this test
        // flaky when the network changes during the test
        oocut.currentSynchronizationAccount = account;
        oocut.scheduleSyncNow();

        // Assert 1: Ensure the new account and the isConnected() is in the expected default state
        assertThatAccountFlagsChangeAsExpected(account, false, false, "createAccount()", checkerParameters1);
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act 2: setConnected to true
        oocut.setConnected(true);
        final CheckerParameters checkerParameters2 = createAccountFlagCheckerParameters(account, true, true,
                "setConnected(true)");

        // Assert 2: Ensure the flags are set as expected
        assertThatAccountFlagsChangeAsExpected(account, true, true, "setConnected(true)", checkerParameters2);
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Act 3: setConnected to false
        oocut.setConnected(false);
        final CheckerParameters checkerParameters3 = createAccountFlagCheckerParameters(account, false, false,
                "setConnected(false)");
        assertThatAccountFlagsChangeAsExpected(account, false, false, "setConnected(false)", checkerParameters3);
        assertThat(oocut.isConnected(), is(equalTo(false)));
    }

    /**
     * Creates a new {@code Runnable} which sleeps and waits a couple of times ({@link #TIMEOUT_TIME} long) until
     * the {@code Account} flags are in the expected state or else fails. It also creates new a {@code Lock} and
     * {@code Condition} which is used by the checker.
     *
     * @param account The {@code Account} to watch for account flag updates
     * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
     * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
     * @param actionName String that identifies the action after which we now wait for changes, e.g. "createAccount()"
     * @return The {@code Runnable}
     */
    private CheckerParameters createAccountFlagCheckerParameters(@NonNull final Account account,
            final boolean syncAutomaticallyEnabled, final boolean periodicSyncEnabled,
            @NonNull final String actionName) {

        final CheckerParameters checkerParameters = new CheckerParameters();
        final Runnable accountFlagChecker = new Runnable() {
            @Override
            public void run() {
                final int iterations = 10; // random number of iterations

                for (int checks = 0; checks < iterations; checks++) {
                    final String tagPrefix = actionName + " (iteration " + (checks + 1) + "): ";
                    try {
                        Thread.sleep(TIMEOUT_TIME * 1000 / iterations);
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException(e);
                    }

                    // Check if the account flags are in the expected state
                    final boolean isFlagsAlreadySet = isAccountFlagsSet(account, syncAutomaticallyEnabled,
                            periodicSyncEnabled);
                    if (!isFlagsAlreadySet) {
                        Log.d(TAG, tagPrefix
                                + "Account flags changed but are still not in the expected state, continue waiting.");
                        continue;
                    }

                    Log.d(TAG,
                            tagPrefix + "Account flags are now in the expected state, sending signal to release lock.");
                    checkerParameters.lock.lock();
                    try {
                        checkerParameters.condition.signal();
                    } finally {
                        checkerParameters.lock.unlock();
                    }
                    return;
                }
                fail(actionName + ": Account flag did not change to the expected state within " + TIMEOUT_TIME
                        + " seconds");
            }
        };
        checkerParameters.setRunnable(accountFlagChecker);
        return checkerParameters;
    }

    /**
     * Ensures that the {@code Account} flags are in the expected state or, if not, locks and waits until the change
     * happens asynchronously.
     * <p>
     * Asserts that this actually happens.
     *
     * @param account The {@code Account} to watch for account flag updates
     * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
     * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
     * @param actionName String that identifies the action after which we now wait for changes, e.g. "createAccount()"
     * @param checkerParameters The {@link CheckerParameters} used to wait for and signal when the {@code Account} flags
     *            are in the expected state.
     */
    private void assertThatAccountFlagsChangeAsExpected(@NonNull final Account account,
            final boolean syncAutomaticallyEnabled, final boolean periodicSyncEnabled, @NonNull final String actionName,
            @NonNull final CheckerParameters checkerParameters) throws InterruptedException {

        final boolean isFlagsAlreadySet = isAccountFlagsSet(account, syncAutomaticallyEnabled, periodicSyncEnabled);
        if (isFlagsAlreadySet) {
            Log.v(TAG, actionName + ": Account flags in the expected state");
            return;
        }

        Log.v(TAG, actionName + ": Account flags are not in the expected state, waiting for Account flags to change.");
        // OnAccountsUpdatesListener is not called on Account flag changes (probably only when accounts are added or
        // deleted) for this reason we implemented a simple sleep and check runnable until we find a better way
        handler.post(checkerParameters.runnable);
        lockAndWaitForConditionSignal(checkerParameters);
    }

    /**
     * Checks synchronously weather the account flags used by {@link WiFiSurveyor#isConnected()} are in the expected
     * state.
     * <p>
     * See {@link WiFiSurveyor#makeAccountSyncable(Account, boolean)} for details.
     * <p>
     * <b>Attention:</b> Never use this method in production as the periodicSync flags are set async
     * by the system so we can never be sure if they are already set or not. For this reason we have the
     * {@link #testSetConnected()} test.
     *
     * @param account The {@code Account} to be checked.
     * @param syncAutomaticallyEnabled True if the expected state is that this flag is enabled.
     * @param periodicSyncEnabled True if the expected state is that a periodicSync is registered.
     * @return True if the flags are in the expected state.
     */
    private static boolean isAccountFlagsSet(@NonNull final Account account, final boolean syncAutomaticallyEnabled,
            final boolean periodicSyncEnabled) {

        final boolean periodicSyncRegisteredState = ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY)
                .size() > 0;
        final boolean autoSyncEnabledState = ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY);

        return autoSyncEnabledState == syncAutomaticallyEnabled && periodicSyncRegisteredState == periodicSyncEnabled;
    }

    /**
     * Locks the thread and waits for the {@code Condition} signal to be sent.
     * <p>
     * Asserts that this actually happens.
     *
     * @param checkerParameters The {@link CheckerParameters} used to wait for and signal when the {@code Account} flags
     *            are in the expected state.
     */
    private void lockAndWaitForConditionSignal(@NonNull final CheckerParameters checkerParameters)
            throws InterruptedException {

        // Now lock and wait for the signal
        checkerParameters.lock.lock();
        try {
            // Ensure the signal arrives within the timeout (or else it will probably never happen)
            assertThat(checkerParameters.condition.await(TIMEOUT_TIME, TimeUnit.SECONDS), is(equalTo(true)));
        } finally {
            checkerParameters.lock.unlock();
        }
    }

    /**
     * A class which holds parameters so that they can be returned as one object by a method.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 4.0.0
     */
    private static class CheckerParameters {

        /**
         * Lock used to synchronize the test case with the account manager.
         */
        private final Lock lock;
        /**
         * Condition waiting for the account manager listener to inform this test that the account changed.
         */
        private final Condition condition;
        /**
         * A {@code Runnable} which sleeps and waits a couple of times ({@link #TIMEOUT_TIME} long) to checks for
         * {@code Account} flags to be in the expected state or else fails.
         */
        private Runnable runnable;

        CheckerParameters() {
            this.lock = new ReentrantLock();
            this.condition = lock.newCondition();
            this.runnable = null;
        }

        void setRunnable(@NonNull final Runnable runnable) {
            this.runnable = runnable;
        }
    }
}
