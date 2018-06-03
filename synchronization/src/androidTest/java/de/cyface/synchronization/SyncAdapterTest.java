package de.cyface.synchronization;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

/**
 * Tests that the sync adapter implemented by this component gets called. This test is not so much about transmitting
 * data, but focuses on whether the sync adapter was implemented correctly.
 * <p>
 * Currently the test calls the actual data transmission code and thus depends on a running server instance. This makes
 * the test large and flaky. Future implementation will hopefully remove this dependency.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public final class SyncAdapterTest {

    /**
     * The tag used to identify log messages from logcat.
     */
    private final static String TAG = "de.cyface.sync.test";

    /**
     * This test case tests whether the sync adapter is called after a request for a direct synchronization.
     *
     * @throws InterruptedException Thrown if waiting for the sync adapter to report back is interrupted.
     */
    @Test
    public void testRequestSync() throws InterruptedException {
        AccountManager am = AccountManager.get(InstrumentationRegistry.getContext());
        Account newAccount = new Account("default_user", "de.cyface");
        if (am.addAccountExplicitly(newAccount, "testpw", Bundle.EMPTY)) {
            ContentResolver.setIsSyncable(newAccount, "de.cyface.provider", 1);
            ContentResolver.setSyncAutomatically(newAccount, "de.cyface.provider", true);
        }

        try {
            final Account account = am.getAccountsByType("de.cyface")[0];

            final Lock lock = new ReentrantLock();
            final Condition condition = lock.newCondition();

            TestSyncStatusObserver observer = new TestSyncStatusObserver(account, lock, condition);

            ContentResolver.requestSync(account, "de.cyface.provider", Bundle.EMPTY);
            ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, observer);

            lock.lock();
            try {
                if (!condition.await(10, TimeUnit.SECONDS)) {
                    fail();
                }
            } finally {
                lock.unlock();
            }
            assertThat(observer.didSync(), is(equalTo(true)));

        } finally {
            am.removeAccount(am.getAccountsByType("de.cyface")[0], null, null, null);
        }
    }

    /**
     * A <code>SyncStatusObserver</code> used to get information about the synchronization adapter. This observer waits
     * for the sync adapter to start synchroniation and stop it again bevor waking up the actual test case.
     * 
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class TestSyncStatusObserver implements SyncStatusObserver {

        /**
         * The authority identifying the content provider used by the <code>SyncAdapter</code> under test.
         */
        static final String CONTENT_PROVIDER_AUTHORITY = "de.cyface.provider";
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
            this.account = account;
            didSync = false;
        }

        @Override
        public void onStatusChanged(int which) {
            Log.d(TAG, "Sync Status changed!");
            Log.d(TAG, String.format("Sync Status active is: %s",
                    ContentResolver.isSyncActive(account, CONTENT_PROVIDER_AUTHORITY)));
            didSync = didSync || ContentResolver.isSyncActive(account, CONTENT_PROVIDER_AUTHORITY);
            // Calling thread will only be called if content resolver has been active but is not anymore.
            if (didSync && !ContentResolver.isSyncActive(account, CONTENT_PROVIDER_AUTHORITY)) {
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
