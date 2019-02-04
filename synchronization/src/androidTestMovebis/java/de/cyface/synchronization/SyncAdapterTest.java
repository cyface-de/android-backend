package de.cyface.synchronization;

import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SyncInfo;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Tests that the sync adapter implemented by this component gets called. This test is not so much about transmitting
 * data, but focuses on whether the sync adapter was implemented correctly.
 * <p>
 * Currently the test calls the actual data transmission code and thus depends on a running server instance. This makes
 * the test large and flaky. Future implementation will hopefully remove this dependency.
 * <p>
 * Currently, Wi-Fi must be activated for this test to run through.
 * 
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class SyncAdapterTest {

    /**
     * This test case tests whether the sync adapter is called after a request for a direct synchronization.
     *
     * @throws InterruptedException Thrown if waiting for the sync adapter to report back is interrupted.
     */
    @Test
    public void testRequestSync() throws InterruptedException {
        AccountManager am = AccountManager.get(InstrumentationRegistry.getInstrumentation().getTargetContext());
        Account newAccount = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        if (am.addAccountExplicitly(newAccount, TestUtils.DEFAULT_PASSWORD, Bundle.EMPTY)) {
            ContentResolver.setIsSyncable(newAccount, AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(newAccount, AUTHORITY, true);
        }

        try {
            final Account account = am.getAccountsByType(ACCOUNT_TYPE)[0];

            final Lock lock = new ReentrantLock();
            final Condition condition = lock.newCondition();

            TestSyncStatusObserver observer = new TestSyncStatusObserver(account, lock, condition);

            Object statusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE | ContentResolver.SYNC_OBSERVER_TYPE_PENDING, observer);
            ContentResolver.requestSync(account, AUTHORITY, Bundle.EMPTY);

            lock.lock();
            try {
                if (!condition.await(10, TimeUnit.SECONDS)) {
                    fail("Sync did not happen within the timeout time of 10 seconds.");
                }
            } finally {
                lock.unlock();
                ContentResolver.removeStatusChangeListener(statusChangeListenerHandle);
            }
            assertThat(observer.didSync(), is(equalTo(true)));

        } finally {
            for (Account account : am.getAccountsByType(ACCOUNT_TYPE)) {
                am.removeAccountExplicitly(account);
            }
        }
    }

    /**
     * A <code>SyncStatusObserver</code> used to get information about the synchronization adapter. This observer waits
     * for the sync adapter to start synchronization and stop it again before waking up the actual test case.
     * 
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class TestSyncStatusObserver implements SyncStatusObserver {
        /**
         * The lock used to synchronize the synchronization adapter with the calling test case.
         */
        private final Lock lock;
        /**
         * The condition under which to wake up the calling test case.
         */
        private final Condition syncCondition;
        /**
         * An account required to identify the correct synchronization adapter call.
         */
        private final Account account;
        /**
         * The state of the synchronization. This is <code>true</code> if synchronization has been called;
         * <code>false</code> otherwise.
         */
        private boolean didSync;

        /**
         * Creates a new completely initialized <code>TestSYncStatusObserver</code>.
         *
         * @param account An account required to identify the correct synchronization adapter call.
         * @param lock The lock used to synchronize the synchronization adapter with the calling test case.
         * @param syncCondition The condition under which to wake up the calling test case.
         */
        TestSyncStatusObserver(final @NonNull Account account, final @NonNull Lock lock,
                final @NonNull Condition syncCondition) {
            this.lock = lock;
            this.syncCondition = syncCondition;
            this.didSync = false;
            this.account = account;
        }

        @Override
        public void onStatusChanged(int which) {
            // Print synchronization info for debugging purposes.
            Log.d(TAG, "Sync Status changed! " + which);
            List<SyncInfo> syncs = ContentResolver.getCurrentSyncs();
            Log.d(TAG, "Syncs: " + syncs.size());
            for (SyncInfo syncInfo : syncs) {
                Log.d(TAG, String.format("Sync: %s,%s,%s,%d", syncInfo.account.name, syncInfo.account.type,
                        syncInfo.authority, syncInfo.startTime));
            }

            // Print synchronizing accounts for debugging purposes.
            AccountManager am = AccountManager.get(InstrumentationRegistry.getInstrumentation().getTargetContext());
            Account[] accounts = am.getAccountsByType(ACCOUNT_TYPE);
            for (Account account : accounts) {
                Log.d(TAG,
                        String.format("Account: %s/active: %s/pending: %s", account,
                                ContentResolver.isSyncActive(account, AUTHORITY),
                                ContentResolver.isSyncPending(account, AUTHORITY)));
            }

            // Actual check for test account
            didSync = didSync || ContentResolver.isSyncActive(account, AUTHORITY);
            // Calling thread will only be called if content resolver has been active but is not anymore.
            if (didSync && !ContentResolver.isSyncActive(account, AUTHORITY)) {
                lock.lock();
                try {
                    syncCondition.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

        boolean didSync() {
            return didSync;
        }
    }

}
