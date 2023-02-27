/*
 * Copyright 2018-2021 Cyface GmbH
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

import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.serializer.model.Point3DType.ACCELERATION;
import static de.cyface.serializer.model.Point3DType.DIRECTION;
import static de.cyface.serializer.model.Point3DType.ROTATION;
import static de.cyface.synchronization.BundlesExtrasCodes.SYNC_PERCENTAGE_ID;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_FINISHED;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_PROGRESS;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_STARTED;
import static de.cyface.synchronization.SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertGeoLocation;
import static de.cyface.testutils.SharedTestUtils.insertMeasurementEntry;
import static de.cyface.testutils.SharedTestUtils.insertPoint3D;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.DefaultPersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.serialization.Point3DFile;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests if the upload progress is broadcasted as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.4.5
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UploadProgressTest {

    private Context context;
    private ContentResolver contentResolver;
    private DefaultPersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    private AccountManager accountManager;
    private SyncAdapter oocut;
    private Account account;

    /**
     * @throws CursorIsNullException When the {@link ContentProvider} is not accessible
     */
    @Before
    public void setUp() throws CursorIsNullException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();

        clearPersistenceLayer(context, contentResolver, AUTHORITY);
        persistenceLayer = new DefaultPersistenceLayer<>(context, contentResolver, AUTHORITY,
                new DefaultPersistenceBehaviour());
        persistenceLayer.restoreOrCreateDeviceId();

        // Ensure reproducibility
        accountManager = AccountManager.get(context);
        SharedTestUtils.cleanupOldAccounts(accountManager, ACCOUNT_TYPE, AUTHORITY);

        // Add new sync account (usually done by DataCapturingService and WifiSurveyor)
        account = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        accountManager.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null);

        oocut = new SyncAdapter(context, false, new MockedHttpConnection());
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
    // TODO [MOV-683]: See logcat - still uses an actual API
    @Ignore("This is currently still dependent on a real test api")
    public void testUploadProgressHappyPath() throws CursorIsNullException, NoSuchMeasurementException {

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.apply();

        final TestReceiver receiver = new TestReceiver();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SYNC_FINISHED);
        filter.addAction(SYNC_PROGRESS);
        filter.addAction(SYNC_STARTED);
        context.registerReceiver(receiver, filter);

        ContentProviderClient client = null;
        try {
            final Measurement measurement = insertMeasurementEntry(persistenceLayer, Modality.UNKNOWN);
            final long measurementIdentifier = measurement.getIdentifier();
            insertGeoLocation(contentResolver, AUTHORITY, measurementIdentifier, 1503055141000L, 49.9304133333333,
                    8.82831833333333, 0.0, 940);
            insertGeoLocation(contentResolver, AUTHORITY, measurementIdentifier, 1503055142000L, 49.9305066666667,
                    8.82814, 8.78270530700684, 8.4f);

            // Insert file base data
            final Point3DFile accelerationsFile = new Point3DFile(context, measurementIdentifier, ACCELERATION);
            final Point3DFile rotationsFile = new Point3DFile(context, measurementIdentifier, ROTATION);
            final Point3DFile directionsFile = new Point3DFile(context, measurementIdentifier, DIRECTION);
            insertPoint3D(accelerationsFile, 1501662635973L, 10.1189575, -0.15088624, 0.2921924);
            insertPoint3D(accelerationsFile, 1501662635981L, 10.116563, -0.16765137, 0.3544629);
            insertPoint3D(accelerationsFile, 1501662635983L, 10.171648, -0.2921924, 0.3784131);
            insertPoint3D(rotationsFile, 1501662635981L, 0.001524045, 0.0025423833, -0.0010279021);
            insertPoint3D(rotationsFile, 1501662635990L, 0.001524045, 0.0025423833, -0.016474236);
            insertPoint3D(rotationsFile, 1501662635993L, -0.0064654383, -0.0219587, -0.014343708);
            insertPoint3D(directionsFile, 1501662636010L, 7.65, -32.4, -71.4);
            insertPoint3D(directionsFile, 1501662636030L, 7.65, -32.550003, -71.700005);
            insertPoint3D(directionsFile, 1501662636050L, 7.65, -33.15, -71.700005);

            // Mark measurement as finished
            persistenceLayer.setStatus(measurementIdentifier, MeasurementStatus.FINISHED, false);

            client = contentResolver.acquireContentProviderClient(getGeoLocationsUri(AUTHORITY));
            SyncResult result = new SyncResult();
            Validate.notNull(client);

            final Bundle testBundle = new Bundle();
            testBundle.putString(MOCK_IS_CONNECTED_TO_RETURN_TRUE, "");
            oocut.onPerformSync(account, testBundle, AUTHORITY, client, result);

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
        Validate.notNull(intent);
        Validate.notNull(intent.getAction());

        if (intent.getAction().equals(SYNC_FINISHED)) {
            Log.d(TAG, "SYNC FINISHED");
        } else if (intent.getAction().equals(SYNC_PROGRESS)) {
            final float percentage = intent.getFloatExtra(SYNC_PERCENTAGE_ID, -1.0f);
            collectedPercentages.add(percentage);
            Log.d(TAG, "SYNC PROGRESS: " + percentage + " % ");
        } else if (intent.getAction().equals(SYNC_STARTED)) {
            Log.d(TAG, "SYNC STARTED");
        } else {
            throw new IllegalStateException(String.format("Invalid message %s", intent.getAction()));
        }
    }

    public List<Float> getCollectedPercentages() {
        return collectedPercentages;
    }
}
