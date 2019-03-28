/*
 * Copyright 2019 Cyface GmbH
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

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.Validate;

/**
 * Tests the correct functionality of the {@link WiFiSurveyor} class.
 * <p>
 * The tests in this class require an emulator or a real device.
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 4.0.0
 */
@RunWith(AndroidJUnit4.class)
public class WifiSurveyorTest {

    /**
     * The time to wait for the account flag to be changed.
     */
    public static final long TIMEOUT_TIME = 10L;
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
     * The {@code OnAccountsUpdateListener} used to wait for the account flags to be set.
     */
    private OnAccountsUpdateListener listener;
    /**
     * Lock used to synchronize the test case with the account manager.
     */
    private Lock lock;
    /**
     * Condition waiting for the account manager listener to inform this test that the account changed.
     */
    private Condition condition;

    /**
     * Initializes the properties for each test case individually.
     */
    @Before
    public void setUp() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        Validate.notNull(connectivityManager);

        oocut = new WiFiSurveyor(context, connectivityManager, AUTHORITY, ACCOUNT_TYPE);

        // Ensure reproducibility
        accountManager = AccountManager.get(context);
        SharedTestUtils.cleanupOldAccounts(accountManager, ACCOUNT_TYPE, AUTHORITY);
    }

    @After
    public void tearDown() {
        if (listener != null) {
            accountManager.removeOnAccountsUpdatedListener(listener);
        }

        final Account[] oldAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (oldAccounts.length > 0) {
            for (Account oldAccount : oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY);
                Validate.isTrue(accountManager.removeAccountExplicitly(oldAccount));
            }
        }
        oocut = null;
    }

    /**
     * Tests that marking the connection as syncable using the account flags works.
     * <p>
     * This test reproduced MOV-635 where the periodic sync flag did not change because syncAutomatically was not set.
     * This bug was only reproducible in integration environment (device and emulator) but not as roboelectric test.
     * <p>
     * This test may be flaky on a read device when the network changes during the test.
     */
    @Test
    public void testSetConnected() throws InterruptedException {

        // Arrange
        final TestCallback testCallback = new TestCallback("testStartDataCapturing", lock, condition);
        Account account = oocut.createAccount(TestUtils.DEFAULT_USERNAME, null);

        // Instead of calling startSurveillance as in production we directly call it's implementation
        // Without the networkCallback or networkConnectivity BroadcastReceiver as this would make this test
        // flaky when the network changes during the test
        oocut.currentSynchronizationAccount = account;
        oocut.scheduleSyncNow();
        // Make sure the new account is in the expected default state
        boolean isFlagsAlreadySet = isAccountFlagsSet(account, false, false);
        if (!isFlagsAlreadySet) {
            Log.v(TAG, "Account flags are not yet set after createAccount(), waiting for account changes.");
            waitForAccountFlagUpdates(account);
            Thread.sleep(20); // CI emulator seems to be too slow for less FIXME
            validateAccountFlagsAreSet(account, false, false);
        }
        // Ensure default state after startSurveillance
        assertThat(oocut.isConnected(), is(equalTo(false)));

        // Act & Assert 1
        oocut.setConnected(true);
        isFlagsAlreadySet = isAccountFlagsSet(account, true, true);
        if (!isFlagsAlreadySet) {
            Log.v(TAG, "Account flags are not yet set after setConnected(true), waiting for account changes.");
            Thread.sleep(20); // CI emulator seems to be too slow for less FIXME
            validateAccountFlagsAreSet(account, true, true);
        }
        assertThat(oocut.isConnected(), is(equalTo(true)));

        // Act & Assert 2
        oocut.setConnected(false);
        isFlagsAlreadySet = isAccountFlagsSet(account, false, false);
        if (!isFlagsAlreadySet) {
            Log.v(TAG, "Account flags are not yet set after setConnected(false), waiting for account changes.");
            Thread.sleep(20); // CI emulator seems to be too slow for less FIXME
            validateAccountFlagsAreSet(account, false, false);
        }
        assertThat(oocut.isConnected(), is(equalTo(false)));
    }

    /**
     * Locks the thread and waits for the {@code Account} flags to be set.
     *
     * @param account The {@code Account} to wait for updates.
     */
    private void waitForAccountFlagUpdates(@NonNull final Account account) {
        this.listener = new OnAccountsUpdateListener() {
            @Override
            public void onAccountsUpdated(Account[] accounts) {
            }
        };
        accountManager.addOnAccountsUpdatedListener(listener, null, false, new String[] {ACCOUNT_TYPE});
    }

    /**
     * Checks synchronously weather the account flags used by {@link WiFiSurveyor#isConnected()} are set.
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
     */
    private void validateAccountFlagsAreSet(@NonNull final Account account, final boolean syncAutomaticallyEnabled,
            boolean periodicSyncEnabled) {
        final boolean periodicSyncRegisteredState = ContentResolver.getPeriodicSyncs(account, TestUtils.AUTHORITY)
                .size() > 0;
        final boolean autoSyncEnabledState = ContentResolver.getSyncAutomatically(account, TestUtils.AUTHORITY);

        assertThat(autoSyncEnabledState, is(equalTo(syncAutomaticallyEnabled)));
        assertThat(periodicSyncRegisteredState, is(equalTo(periodicSyncEnabled)));
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
     * A listener for events from the account manager, only used by tests.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 4.0.0
     */
    class TestListener implements OnAccountsUpdateListener {

        @Override
        public void onAccountsUpdated(Account[] accounts) {
            Log.e(TAG, "BLAAAA");
        }
    }
}
