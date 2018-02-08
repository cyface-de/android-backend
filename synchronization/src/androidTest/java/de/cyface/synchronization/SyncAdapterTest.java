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
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

/**
 * Created by muthmann on 07.02.18.
 */
@RunWith(AndroidJUnit4.class)
public final class SyncAdapterTest {

    private final static String TAG = "de.cyface.sync.test";

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

        } finally {
            am.removeAccount(am.getAccountsByType("de.cyface")[0], null, null, null);
        }
    }

    private static class TestSyncStatusObserver implements SyncStatusObserver {

        static final String CONTENT_PROVIDER_AUTHORITY = "de.cyface.provider";
        private final Lock lock;
        private final Condition syncCondition;
        private final Account account;
        private boolean didSync;

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
