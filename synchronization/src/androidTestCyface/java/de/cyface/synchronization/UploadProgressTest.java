package de.cyface.synchronization;

import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_POINTS_TO_TRANSMIT;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_POINTS_TRANSMITTED;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.synchronization.TestUtils.clearDatabase;
import static de.cyface.synchronization.TestUtils.getGeoLocationsUri;
import static de.cyface.synchronization.TestUtils.insertTestAcceleration;
import static de.cyface.synchronization.TestUtils.insertTestDirection;
import static de.cyface.synchronization.TestUtils.insertTestGeoLocation;
import static de.cyface.synchronization.TestUtils.insertTestMeasurement;
import static de.cyface.synchronization.TestUtils.insertTestRotation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.utils.Validate;

/**
 * Tests if the upload progress is broadcasted as expected.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class UploadProgressTest {
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

    @Test
    public void testUploadProgressHappyPath() {
        CyfaceSyncAdapter syncAdapter = new CyfaceSyncAdapter(context, false, new MockedHttpConnection(), 2, 2, 2, 2);
        AccountManager manager = AccountManager.get(context);
        Account account = new Account(TestUtils.DEFAULT_FREE_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_FREE_PASSWORD, null);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, "https://s1.cyface.de/v1/dcs");
        editor.putString(SyncService.DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();
        TestReceiver receiver = new TestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CyfaceConnectionListener.SYNC_FINISHED);
        filter.addAction(CyfaceConnectionListener.SYNC_PROGRESS);
        filter.addAction(SYNC_POINTS_TRANSMITTED);
        filter.addAction(SYNC_POINTS_TO_TRANSMIT);
        filter.addAction(CyfaceConnectionListener.SYNC_STARTED);
        context.registerReceiver(receiver, filter);

        ContentProviderClient client = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            long measurementIdentifier = insertTestMeasurement(contentResolver, "UNKNOWN");
            insertTestGeoLocation(contentResolver, measurementIdentifier, 1503055141000L, 49.9304133333333,
                    8.82831833333333, 0.0, 940);
            insertTestGeoLocation(contentResolver, measurementIdentifier, 1503055142000L, 49.9305066666667, 8.82814,
                    8.78270530700684, 840);
            insertTestAcceleration(contentResolver, measurementIdentifier, 1501662635973L, 10.1189575, -0.15088624,
                    0.2921924);
            insertTestAcceleration(contentResolver, measurementIdentifier, 1501662635981L, 10.116563, -0.16765137,
                    0.3544629);
            insertTestAcceleration(contentResolver, measurementIdentifier, 1501662635983L, 10.171648, -0.2921924,
                    0.3784131);
            insertTestRotation(contentResolver, measurementIdentifier, 1501662635981L, 0.001524045, 0.0025423833,
                    -0.0010279021);
            insertTestRotation(contentResolver, measurementIdentifier, 1501662635990L, 0.001524045, 0.0025423833,
                    -0.016474236);
            insertTestRotation(contentResolver, measurementIdentifier, 1501662635993L, -0.0064654383, -0.0219587,
                    -0.014343708);
            insertTestDirection(contentResolver, measurementIdentifier, 1501662636010L, 7.65, -32.4, -71.4);
            insertTestDirection(contentResolver, measurementIdentifier, 1501662636030L, 7.65, -32.550003, -71.700005);
            insertTestDirection(contentResolver, measurementIdentifier, 1501662636050L, 7.65, -33.15, -71.700005);

            client = contentResolver.acquireContentProviderClient(getGeoLocationsUri());
            SyncResult result = new SyncResult();
            Validate.notNull(client);
            syncAdapter.onPerformSync(account, new Bundle(), AUTHORITY, client, result);
        } finally {
            if (client != null) {
                client.close();
            }
            context.unregisterReceiver(receiver);
        }

        assertThat(receiver.getCollectedProgress().size(), is(equalTo(2)));
        assertThat(receiver.getCollectedProgress().get(0), is(equalTo(8L)));
        assertThat(receiver.getCollectedProgress().get(1), is(equalTo(11L)));
        assertThat(receiver.getCollectedTotalProgress().size(), is(equalTo(2)));
        assertThat(receiver.getCollectedTotalProgress().get(0), is(equalTo(11L)));
        assertThat(receiver.getCollectedTotalProgress().get(1), is(equalTo(11L)));
    }
}

class TestReceiver extends BroadcastReceiver {

    private final List<Long> collectedProgress = new LinkedList<>();
    private final List<Long> collectedTotalProgress = new LinkedList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case CyfaceConnectionListener.SYNC_FINISHED:
                Log.d(TAG, "SYNC FINISHED");
                break;
            case CyfaceConnectionListener.SYNC_PROGRESS:
                final long countOfTransmittedPoints = intent.getLongExtra(SYNC_POINTS_TRANSMITTED, 0);
                final long countOfPointsToTransmit = intent.getLongExtra(SYNC_POINTS_TO_TRANSMIT, 0);
                collectedProgress.add(countOfTransmittedPoints);
                collectedTotalProgress.add(countOfPointsToTransmit);
                Log.d(TAG, "SYNC PROGRESS: " + countOfTransmittedPoints + " / " + countOfPointsToTransmit);
                break;
            case SYNC_POINTS_TRANSMITTED:
                Log.d(TAG, "SYNC PROGRESS TRANSMITTED");
                break;
            case SYNC_POINTS_TO_TRANSMIT:
                Log.d(TAG, "SYNC PROGRESS TOTAL");
                break;
            case CyfaceConnectionListener.SYNC_STARTED:
                Log.d(TAG, "SYNC STARTED");
                break;
            default:
                throw new IllegalStateException(String.format("Invalid message %s", intent.getAction()));
        }
    }

    public List<Long> getCollectedProgress() {
        return collectedProgress;
    }

    public List<Long> getCollectedTotalProgress() {
        return collectedTotalProgress;
    }
}
