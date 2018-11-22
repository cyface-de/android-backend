package de.cyface.synchronization;

import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_FALSE;
import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_TRUE;

import static de.cyface.synchronization.TestUtils.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.*;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.utils.Validate;

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
 * @version 1.1.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public final class SyncAdapterTest {
    Context context;
    ContentResolver contentResolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
        contentResolver = context.getContentResolver();
        clearDatabase(contentResolver);
    }

    @After
    public void tearDown() {
        clearDatabase(contentResolver);
        contentResolver = null;
        context = null;
    }

    /**
     * Tests whether points are correctly marked as synced.
     */
    @Test
    public void testOnPerformSync() {

        // Arrange
        final SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        final AccountManager manager = AccountManager.get(context);
        final Account account = new Account(TestUtils.DEFAULT_FREE_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_FREE_PASSWORD, null);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, "https://s1.cyface.de/v1/dcs");
        editor.putString(SyncService.DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();
        // Insert data to be synced
        final ContentResolver contentResolver = context.getContentResolver();
        final long measurementIdentifier = insertTestMeasurement(contentResolver, "UNKNOWN");
        insertTestGeoLocation(contentResolver, measurementIdentifier, 1503055141000L, 49.9304133333333,
                8.82831833333333, 0.0, 940);
        Cursor locationsCursor = null;
        Cursor measurementsCursor = null;
        // Assert that data is in the database
        try {
            // Measurement entry
            measurementsCursor = loadMeasurement(contentResolver, measurementIdentifier);
            assertThat(measurementsCursor.getCount(), is(1));
            measurementsCursor.moveToNext();
            final int measurementIsFinished = measurementsCursor
                    .getInt(measurementsCursor.getColumnIndex(MeasurementTable.COLUMN_FINISHED));
            assertThat(measurementIsFinished, is(SQLITE_TRUE));
            final int measurementIsSynced = measurementsCursor
                    .getInt(measurementsCursor.getColumnIndex(MeasurementTable.COLUMN_SYNCED));
            assertThat(measurementIsSynced, is(SQLITE_FALSE));
            // GPS Point
            locationsCursor = loadTrack(contentResolver, measurementIdentifier);
            assertThat(locationsCursor.getCount(), is(1));
            locationsCursor.moveToNext();
            final int gpsPointIsSynced = locationsCursor
                    .getInt(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_IS_SYNCED));
            assertThat(gpsPointIsSynced, is(SQLITE_FALSE));
        } finally {
            if (locationsCursor != null) {
                locationsCursor.close();
            }
            if (measurementsCursor != null) {
                measurementsCursor.close();
            }
        }

        // Mock - nothing to do

        // Act: sync
        ContentProviderClient client = null;
        try {
            client = contentResolver.acquireContentProviderClient(getGeoLocationsUri());
            final SyncResult result = new SyncResult();
            Validate.notNull(client);
            syncAdapter.onPerformSync(account, new Bundle(), AUTHORITY, client, result);
        } finally {
            if (client != null) {
                client.close();
            }
        }

        // Assert: synced data is marked as synced
        try {
            // Measurement entry
            measurementsCursor = loadMeasurement(contentResolver, measurementIdentifier);
            assertThat(measurementsCursor.getCount(), is(1));
            measurementsCursor.moveToNext();
            final int measurementIsSynced = measurementsCursor
                    .getInt(measurementsCursor.getColumnIndex(MeasurementTable.COLUMN_SYNCED));
            assertThat(measurementIsSynced, is(SQLITE_TRUE));
            // GPS Point
            locationsCursor = loadTrack(contentResolver, measurementIdentifier);
            assertThat(locationsCursor.getCount(), is(0));
        } finally {
            if (locationsCursor != null) {
                locationsCursor.close();
            }
            if (measurementsCursor != null) {
                measurementsCursor.close();
            }
        }
    }

    /**
     * This test case tests whether the sync adapter is called after a request for a direct synchronization.
     *
     * @throws InterruptedException Thrown if waiting for the sync adapter to report back is interrupted.
     */
    @Test
    public void testRequestSync() throws InterruptedException {
        AccountManager am = AccountManager.get(InstrumentationRegistry.getTargetContext());
        Account newAccount = new Account(TestUtils.DEFAULT_FREE_USERNAME, ACCOUNT_TYPE);
        if (am.addAccountExplicitly(newAccount, TestUtils.DEFAULT_FREE_PASSWORD, Bundle.EMPTY)) {
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
            AccountManager am = AccountManager.get(InstrumentationRegistry.getTargetContext());
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

    /**
     * Loads the track of geolocations objects for the provided measurement id.
     *
     * @param measurementId The measurement id of the data to load.
     * @return The cursor for the track of geolocation objects ordered by time ascending.
     */
    public Cursor loadTrack(final ContentResolver resolver, final long measurementId) {
        return resolver.query(getGeoLocationsUri(), null, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {String.valueOf(measurementId)}, GpsPointsTable.COLUMN_GPS_TIME + " ASC");
    }

    /**
     * Loads the measurement for the provided measurement id.
     *
     * @param measurementId The measurement id of the measurement to load.
     * @return The cursor for the loaded measurement.
     */
    public Cursor loadMeasurement(final ContentResolver resolver, final long measurementId) {
        return resolver.query(getMeasurementUri(), null, BaseColumns._ID + "=?",
                new String[] {String.valueOf(measurementId)}, null);
    }
}
