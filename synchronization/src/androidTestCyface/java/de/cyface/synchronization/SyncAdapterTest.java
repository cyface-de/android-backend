package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.DEVICE_IDENTIFIER_KEY;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.synchronization.TestUtils.clear;
import static de.cyface.synchronization.TestUtils.getIdentifierUri;
import static de.cyface.synchronization.TestUtils.insertTestGeoLocation;
import static de.cyface.synchronization.TestUtils.insertTestMeasurement;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.Persistence;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.utils.Validate;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code> with the database.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.4.0
 */
@RunWith(AndroidJUnit4.class)
@FlakyTest
public final class SyncAdapterTest {
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

    /**
     * Tests whether points are correctly marked as synced.
     */
    @Test
    public void testOnPerformSync() throws NoSuchMeasurementException {

        // Arrange
        Persistence persistence = new Persistence(context, contentResolver, AUTHORITY);
        final SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        final AccountManager manager = AccountManager.get(context);
        final Account account = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.putString(DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        editor.apply();
        // Insert data to be synced
        final ContentResolver contentResolver = context.getContentResolver();
        final Measurement insertedMeasurement = insertTestMeasurement(context, contentResolver, Vehicle.UNKNOWN);
        final long measurementIdentifier = insertedMeasurement.getIdentifier();
        insertTestGeoLocation(context, measurementIdentifier, 1503055141000L, 49.9304133333333, 8.82831833333333, 0.0,
                940);
        // Write point counters to MetaFile
        MetaFile.append(context, insertedMeasurement.getIdentifier(), new MetaFile.PointMetaData(1, 0, 0, 0));
        // Finish measurement
        persistence.closeMeasurement(insertedMeasurement);
        // Assert that data is in the database
        final Measurement finishedMeasurement = persistence.loadFinishedMeasurement(measurementIdentifier);
        assertThat(finishedMeasurement, notNullValue());
        List<GeoLocation> geoLocations = persistence.loadTrack(finishedMeasurement);
        assertThat(geoLocations.size(), is(1));

        // Mock - nothing to do

        // Act: sync
        ContentProviderClient client = null;
        try {
            client = contentResolver.acquireContentProviderClient(getIdentifierUri());
            final SyncResult result = new SyncResult();
            Validate.notNull(client);
            syncAdapter.onPerformSync(account, new Bundle(), AUTHORITY, client, result);
        } finally {
            if (client != null) {
                client.close();
            }
        }

        // Assert: synced data is marked as synced
        // Measurement entry
        final Measurement syncedMeasurement = persistence.loadSyncedMeasurement(measurementIdentifier);
        assertThat(syncedMeasurement, notNullValue());

        // GPS Point
        geoLocations = persistence.loadTrack(syncedMeasurement);
        assertThat(geoLocations.size(), is(1));
        // TODO: currently we only mark gps points as synced and don't delete them
        // assertThat(locationsCursor.getCount(), is(0));
    }
}
