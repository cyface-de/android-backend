package de.cyface.synchronization;

import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.synchronization.BundlesExtrasCodes.SYNC_PERCENTAGE_ID;
import static de.cyface.synchronization.SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.DEFAULT_PASSWORD;
import static de.cyface.synchronization.TestUtils.DEFAULT_USERNAME;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertGeoLocation;
import static de.cyface.testutils.SharedTestUtils.insertMeasurementEntry;
import static de.cyface.testutils.SharedTestUtils.insertPoint3d;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests if the upload progress is broadcasted as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.3
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UploadProgressTest {
    private Context context;
    private ContentResolver contentResolver;
    private PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    private AccountManager accountManager;

    /**
     * @throws CursorIsNullException When the {@link ContentProvider} is not accessible
     */
    @Before
    public void setUp() throws CursorIsNullException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
        persistenceLayer = new PersistenceLayer<>(context, contentResolver, AUTHORITY,
                new DefaultPersistenceBehaviour());
        persistenceLayer.restoreOrCreateDeviceId();
        accountManager = AccountManager.get(context);
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, contentResolver, AUTHORITY);

        final Account[] oldAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        if (oldAccounts.length > 0) {
            for (Account oldAccount : oldAccounts) {
                ContentResolver.removePeriodicSync(oldAccount, AUTHORITY, Bundle.EMPTY);
                Validate.isTrue(accountManager.removeAccountExplicitly(oldAccount));
            }
        }

        contentResolver = null;
        context = null;
    }

    @Test
    @FlakyTest // because this is currently still dependent on a real test api (see logcat)
    public void testUploadProgressHappyPath() throws CursorIsNullException {
        SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        Account account = new Account(DEFAULT_USERNAME, ACCOUNT_TYPE);
        accountManager.addAccountExplicitly(account, DEFAULT_PASSWORD, null);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.apply();
        TestReceiver receiver = new TestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CyfaceConnectionStatusListener.SYNC_FINISHED);
        filter.addAction(CyfaceConnectionStatusListener.SYNC_PROGRESS);
        filter.addAction(CyfaceConnectionStatusListener.SYNC_STARTED);
        context.registerReceiver(receiver, filter);

        ContentProviderClient client = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Measurement measurement = insertMeasurementEntry(persistenceLayer, Vehicle.UNKNOWN);
            long measurementIdentifier = measurement.getIdentifier();
            insertGeoLocation(contentResolver, AUTHORITY, measurementIdentifier, 1503055141000L, 49.9304133333333,
                    8.82831833333333, 0.0, 940);
            insertGeoLocation(contentResolver, AUTHORITY, measurementIdentifier, 1503055142000L, 49.9305066666667,
                    8.82814, 8.78270530700684, 840);

            // Insert file base data
            final Point3dFile accelerationsFile = new Point3dFile(context, measurementIdentifier,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            final Point3dFile rotationsFile = new Point3dFile(context, measurementIdentifier,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            final Point3dFile directionsFile = new Point3dFile(context, measurementIdentifier,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            insertPoint3d(accelerationsFile, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
            insertPoint3d(accelerationsFile, 1501662635981L, 10.116563, -0.16765137, 0.3544629);
            insertPoint3d(accelerationsFile, 1501662635983L, 10.171648, -0.2921924, 0.3784131);
            insertPoint3d(rotationsFile, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
            insertPoint3d(rotationsFile, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236);
            insertPoint3d(rotationsFile, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708);
            insertPoint3d(directionsFile, 1501662636010L, 7.65, -32.4, -71.4);
            insertPoint3d(directionsFile, 1501662636030L, 7.65, -32.550003, -71.700005);
            insertPoint3d(directionsFile, 1501662636050L, 7.65, -33.15, -71.700005);

            client = contentResolver.acquireContentProviderClient(getGeoLocationsUri(AUTHORITY));
            SyncResult result = new SyncResult();
            Validate.notNull(client);
            final Bundle testBundle = new Bundle();
            testBundle.putString(MOCK_IS_CONNECTED_TO_RETURN_TRUE, "");
            syncAdapter.onPerformSync(account, testBundle, AUTHORITY, client, result);
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
                final float percentage = intent.getFloatExtra(SYNC_PERCENTAGE_ID, -1.0f);
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
