package de.cyface.synchronization;

import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_FALSE;
import static de.cyface.persistence.MeasuringPointsContentProvider.SQLITE_TRUE;
import static de.cyface.synchronization.TestUtils.ACCOUNT_TYPE;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.clearDatabase;
import static de.cyface.synchronization.TestUtils.getGeoLocationsUri;
import static de.cyface.synchronization.TestUtils.getMeasurementUri;
import static de.cyface.synchronization.TestUtils.insertTestGeoLocation;
import static de.cyface.synchronization.TestUtils.insertTestMeasurement;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.utils.Validate;

/**
 * Tests the correct internal workings of the <code>CyfaceSyncAdapter</code> with the database.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.4.0
 */
@RunWith(AndroidJUnit4.class)
public class CyfaceSyncAdapterTest {
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
        final CyfaceSyncAdapter syncAdapter = new CyfaceSyncAdapter(context, false, new MockedHttpConnection(), 2, 2, 2,
                2);
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