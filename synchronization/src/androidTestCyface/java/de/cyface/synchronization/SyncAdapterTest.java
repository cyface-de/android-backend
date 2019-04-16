/*
 * Copyright 2017 Cyface GmbH
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
import static de.cyface.persistence.Utils.getMeasurementUri;
import static de.cyface.synchronization.SyncAdapter.MOCK_IS_CONNECTED_TO_RETURN_TRUE;
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
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

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
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Track;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code> with the persistence layer.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.4.3
 * @since 2.4.0
 */
@RunWith(AndroidJUnit4.class)
// To execute notice errors with the short running testOnPerformSync before the large
// testOnPerformSyncWithLargeMeasurement which can save a lot of time
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class SyncAdapterTest {

    private Context context;
    private ContentResolver contentResolver;
    private Account account;
    private SyncAdapter oocut;
    private AccountManager accountManager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();

        clearPersistenceLayer(context, contentResolver, AUTHORITY);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, TEST_API_URL);
        editor.apply();

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

    /**
     * Tests whether measurements are correctly marked as synced.
     */
    @Test
    public void testOnPerformSync() throws NoSuchMeasurementException, CursorIsNullException {

        // Arrange
        // Insert data to be synced
        final PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context,
                contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
        persistence.restoreOrCreateDeviceId(); // is usually called by the DataCapturingService
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

            final Bundle testBundle = new Bundle();
            testBundle.putString(MOCK_IS_CONNECTED_TO_RETURN_TRUE, "");
            oocut.onPerformSync(account, testBundle, AUTHORITY, client, result);
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
        final List<Track> tracks = persistence.loadTracks(loadedMeasurement.getIdentifier());
        assertThat(tracks.get(0).getGeoLocations().size(), is(1));
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
    @Ignore
    public void testOnPerformSyncWithLargeMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
        // 3_000_000 is the minimum which reproduced MOV-515 on N5X emulator
        final int point3dCount = 3_000_000;
        final int locationCount = 3_000;

        // Arrange
        // Insert data to be synced
        final PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context,
                contentResolver, AUTHORITY, new DefaultPersistenceBehaviour());
        persistence.restoreOrCreateDeviceId(); // is usually called by the DataCapturingService
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

            final Bundle testBundle = new Bundle();
            testBundle.putString(MOCK_IS_CONNECTED_TO_RETURN_TRUE, "");
            oocut.onPerformSync(account, testBundle, AUTHORITY, client, result);
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
        final List<Track> tracks = persistence.loadTracks(loadedMeasurement.getIdentifier());
        assertThat(tracks.get(0).getGeoLocations().size(), is(locationCount));
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
