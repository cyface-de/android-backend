package de.cyface.synchronization;

import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TEST_API_URL;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static de.cyface.testutils.SharedTestUtils.insertSampleMeasurementWithData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.List;

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
import androidx.test.filters.LargeTest;
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
 * @version 2.2.3
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
        clearPersistenceLayer(context, contentResolver, AUTHORITY);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.apply();
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
        contentResolver = null;
        context = null;
    }

    /**
     * Tests whether measurements are correctly marked as synced.
     */
    @Test
    public void testOnPerformSync() throws NoSuchMeasurementException, CursorIsNullException {

        // Arrange
        PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context, contentResolver,
                AUTHORITY, new DefaultPersistenceBehaviour());
        final SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        final AccountManager manager = AccountManager.get(context);
        final Account account = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null);
        persistence.restoreOrCreateDeviceId();

        // Insert data to be synced
        final Measurement insertedMeasurement = insertSampleMeasurementWithData(context, AUTHORITY,
                MeasurementStatus.FINISHED, persistence, 1, 1);
        final long measurementIdentifier = insertedMeasurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));

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
        final MeasurementStatus newStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(newStatus, is(equalTo(MeasurementStatus.SYNCED)));

        // GeoLocation
        final Measurement loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        assertThat(loadedMeasurement, notNullValue());
        final List<List<GeoLocation>> subTracks = persistence.loadTrack(loadedMeasurement.getIdentifier());
        assertThat(subTracks.get(0).size(), is(1));
    }

    /**
     * Test to reproduce problems occurring when large measurement uploads are not handled correctly,
     * e.g. OOM during serialization or compression.
     * <p>
     * This test was used to reproduce:
     * - MOV-528: OOM on large uploads (depending on the device memory)
     * - MOV-515: 401 when upload takes longer than the token validation time (server-side).
     * (!) This bug is only triggered when you replace MockedHttpConnection with HttpConnection
     */
    @Test
    @LargeTest // ~ 8-10 minutes
    public void testOnPerformSyncWithLargeMeasurement() throws NoSuchMeasurementException, CursorIsNullException {

        // Arrange
        PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context, contentResolver,
                AUTHORITY, new DefaultPersistenceBehaviour());
        final SyncAdapter syncAdapter = new SyncAdapter(context, false, new MockedHttpConnection());
        final AccountManager manager = AccountManager.get(context);
        final Account account = new Account(TestUtils.DEFAULT_USERNAME, ACCOUNT_TYPE);
        manager.addAccountExplicitly(account, TestUtils.DEFAULT_PASSWORD, null);
        persistence.restoreOrCreateDeviceId();

        // Insert data to be synced - 3_000_000 is the minimum which reproduced MOV-515 on N5X emulator
        final int point3dCount = 3_000_000;
        final int locationCount = 3_000;
        final ContentResolver contentResolver = context.getContentResolver();
        final Measurement insertedMeasurement = insertSampleMeasurementWithData(context, AUTHORITY,
                MeasurementStatus.FINISHED, persistence, point3dCount, locationCount);
        final long measurementIdentifier = insertedMeasurement.getIdentifier();
        final MeasurementStatus loadedStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(loadedStatus, is(equalTo(MeasurementStatus.FINISHED)));

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
        final MeasurementStatus newStatus = persistence.loadMeasurementStatus(measurementIdentifier);
        assertThat(newStatus, is(equalTo(MeasurementStatus.SYNCED)));

        // GeoLocation
        final Measurement loadedMeasurement = persistence.loadMeasurement(measurementIdentifier);
        assertThat(loadedMeasurement, notNullValue());
        final List<List<GeoLocation>> subTracks = persistence.loadTrack(loadedMeasurement.getIdentifier());
        assertThat(subTracks.get(0).size(), is(locationCount));
    }

    /**
     * Loads the track of geolocations objects for the provided measurement id.
     *
     * @param measurementId The measurement id of the data to load.
     * @return The cursor for the track of geolocation objects ordered by time ascending.
     */
    public Cursor loadTrack(final ContentResolver resolver, final long measurementId) {
        return resolver.query(getGeoLocationsUri(AUTHORITY), null, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {String.valueOf(measurementId)}, GeoLocationsTable.COLUMN_GEOLOCATION_TIME + " ASC");
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
