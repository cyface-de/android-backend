package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.DEVICE_IDENTIFIER_KEY;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_PERCENTAGE;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.synchronization.TestUtils.clear;
import static de.cyface.synchronization.TestUtils.getIdentifierUri;
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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;

import de.cyface.persistence.Persistence;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.utils.Validate;

/**
 * Tests if the upload progress is broadcasted as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
@LargeTest
public class UploadProgressTest {
    private Context context;
    private ContentResolver contentResolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();
        clear(context, contentResolver);
    }

    @After
    public void tearDown() {
        clear(context, contentResolver);
        contentResolver = null;
        context = null;
    }

    @Test
    public void testUploadProgressHappyPath() {
        SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        AccountManager manager = AccountManager.get(context);
        Account account = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.putString(DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();
        TestReceiver receiver = new TestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CyfaceConnectionStatusListener.SYNC_FINISHED);
        filter.addAction(CyfaceConnectionStatusListener.SYNC_PERCENTAGE); // FIXME: drop
        filter.addAction(CyfaceConnectionStatusListener.SYNC_PROGRESS);
        filter.addAction(CyfaceConnectionStatusListener.SYNC_STARTED);
        context.registerReceiver(receiver, filter);

        ContentProviderClient client = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            final Measurement measurement = insertTestMeasurement(context, contentResolver, Vehicle.UNKNOWN);
            final long measurementIdentifier = measurement.getIdentifier();
            insertTestGeoLocation(context, measurementIdentifier, 1503055141000L, 49.9304133333333, 8.82831833333333,
                    0.0, 940);
            insertTestGeoLocation(context, measurementIdentifier, 1503055142000L, 49.9305066666667, 8.82814,
                    8.78270530700684, 840);
            insertTestAcceleration(context, measurementIdentifier, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
            insertTestAcceleration(context, measurementIdentifier, 1501662635981L, 10.116563, -0.16765137, 0.3544629);
            insertTestAcceleration(context, measurementIdentifier, 1501662635983L, 10.171648, -0.2921924, 0.3784131);
            insertTestRotation(context, measurementIdentifier, 1501662635981L, 0.001524045, 0.0025423833,
                    -0.0010279021);
            insertTestRotation(context, measurementIdentifier, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236);
            insertTestRotation(context, measurementIdentifier, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708);
            insertTestDirection(context, measurementIdentifier, 1501662636010L, 7.65, -32.4, -71.4);
            insertTestDirection(context, measurementIdentifier, 1501662636030L, 7.65, -32.550003, -71.700005);
            insertTestDirection(context, measurementIdentifier, 1501662636050L, 7.65, -33.15, -71.700005);

            // Write point counters to MetaFile
            MetaFile.append(context, measurementIdentifier, new MetaFile.PointMetaData(2, 3, 3, 3));
            // Finish measurement
            new Persistence(context, contentResolver, AUTHORITY).closeMeasurement(measurement);

            client = contentResolver.acquireContentProviderClient(getIdentifierUri());
            SyncResult result = new SyncResult();
            Validate.notNull(client);
            syncAdapter.onPerformSync(account, new Bundle(), AUTHORITY, client, result);
        } finally {
            if (client != null) {
                client.close();
            }
            context.unregisterReceiver(receiver);
        }

        assertThat(receiver.getCollectedPercentages().size(), is(equalTo(1)));
        assertThat(receiver.getCollectedPercentages().get(0), is(equalTo(1.0f)));
    }
}

class TestReceiver extends BroadcastReceiver {

    private final List<Float> collectedPercentages = new LinkedList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case CyfaceConnectionStatusListener.SYNC_FINISHED:
                Log.d(TAG, "SYNC FINISHED");
                break;
            case CyfaceConnectionStatusListener.SYNC_PROGRESS:
                final float percentage = intent.getFloatExtra(SYNC_PERCENTAGE, -1.0f);
                collectedPercentages.add(percentage);
                Log.d(TAG, "SYNC PROGRESS: " + percentage + " % ");
                break;
            case CyfaceConnectionStatusListener.SYNC_STARTED:
                Log.d(TAG, "SYNC STARTED");
                break;
            default:
                throw new IllegalStateException(String.format("Invalid message %s", intent.getAction()));
        }
    }

    public List<Float> getCollectedPercentages() {
        return collectedPercentages;
    }
}
