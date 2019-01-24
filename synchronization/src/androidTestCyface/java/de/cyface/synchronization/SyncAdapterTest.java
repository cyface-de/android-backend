package de.cyface.synchronization;

import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.synchronization.Constants.DEVICE_IDENTIFIER_KEY;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.synchronization.TestUtils.clear;
import static de.cyface.synchronization.TestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
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
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code> with the persistence layer.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.1.0
 * @since 2.4.0
 */
@RunWith(AndroidJUnit4.class)
public final class SyncAdapterTest {
    private Context context;
    private ContentResolver contentResolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();
        clear(context, contentResolver, AUTHORITY);
    }

    @After
    public void tearDown() {
        clear(context, contentResolver, AUTHORITY);
        contentResolver = null;
        context = null;
    }

    /**
     * Tests whether points are correctly marked as synced.
     */
    @Test
    public void testOnPerformSync() throws NoSuchMeasurementException, CursorIsNullException {

        // Arrange
        PersistenceLayer persistence = new PersistenceLayer(context, contentResolver, AUTHORITY,
                new DefaultPersistenceBehaviour());
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
        final Measurement insertedMeasurement = insertSampleMeasurementWithData(context, AUTHORITY,
                MeasurementStatus.FINISHED, persistence);
        final long measurementIdentifier = insertedMeasurement.getIdentifier();

        // Mock - nothing to do

        // Act: sync
        ContentProviderClient client = null;
        try {
            client = contentResolver.acquireContentProviderClient(getGeoLocationsUri(AUTHORITY));
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
        final Measurement loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.SYNCED)));
        assertThat(loadedMeasurement, notNullValue());

        // GPS Point
        List<GeoLocation> geoLocations = persistence.loadTrack(loadedMeasurement);
        assertThat(geoLocations.size(), is(1));
    }

    /**
     * Loads the track of geolocations objects for the provided measurement id.
     *
     * @param measurementId The measurement id of the data to load.
     * @return The cursor for the track of geolocation objects ordered by time ascending.
     */
    public Cursor loadTrack(final ContentResolver resolver, final long measurementId) {
        return resolver.query(getGeoLocationsUri(AUTHORITY), null, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {String.valueOf(measurementId)}, GeoLocationsTable.COLUMN_GPS_TIME + " ASC");
    }

    /**
     * Loads the measurement for the provided measurement id.
     *
     * @param measurementId The measurement id of the measurement to load.
     * @return The cursor for the loaded measurement.
     */
    public Cursor loadMeasurement(final ContentResolver resolver, final long measurementId) {
        return resolver.query(getMeasurementUri(AUTHORITY), null, BaseColumns._ID + "=?",
                new String[] {String.valueOf(measurementId)}, null);
    }
}
