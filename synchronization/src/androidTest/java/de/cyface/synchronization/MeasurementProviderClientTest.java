/*
 * Copyright 2017-2023 Cyface GmbH
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

import static de.cyface.persistence.DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.content.AbstractCyfaceTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.testutils.SharedTestUtils.clearPersistenceLayer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import de.cyface.persistence.content.BaseColumns;
import de.cyface.persistence.content.LocationTable;
import de.cyface.persistence.content.DefaultProviderClient;
import de.cyface.persistence.content.MeasurementTable;
import de.cyface.utils.Validate;

/**
 * Tests that instances of the {@link DefaultProviderClient} do work correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.10
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementProviderClientTest {

    /**
     * Constant you can play around with to find out how many simultaneous operations are possible in one transaction to
     * insert into the <code>ContentProvider</code>.
     */
    private final static int MAX_SIMULTANEOUS_OPERATIONS = 550;
    private Context context;
    private ContentResolver contentResolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        contentResolver = context.getContentResolver();
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
    }

    @After
    public void tearDown() {
        clearPersistenceLayer(context, contentResolver, AUTHORITY);
    }

    /**
     * This test makes sure that larger GeoLocation tracks can be loaded completely form the
     * database as there was a bug which limited the query size to 10_000 entries #MOV-248.
     */
    @Test
    public void testLoadGeoLocations_10hTrack() throws RemoteException {
        // The Location frequency is always 1 Hz, i.e. 10h of measurement:
        testLoadGeoLocations(3600 * 10);
    }

    public void testLoadGeoLocations(int numberOftestEntries) throws RemoteException {
        // Arrange
        ContentProviderClient client = null;
        Cursor locationsCursor = null;
        int numberOfLoadedGeoLocations = 0;

        // Act: Store and load the test entries
        try {
            client = contentResolver.acquireContentProviderClient(AUTHORITY);
            if (client == null) {
                throw new IllegalStateException(String.format(
                        "Unable to initialize content provider client for content provider \"(%s)\"", AUTHORITY));
            }

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_MODALITY, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_STATUS, OPEN.getDatabaseIdentifier());
            measurementValues.put(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                    PERSISTENCE_FILE_FORMAT_VERSION);
            measurementValues.put(MeasurementTable.COLUMN_DISTANCE, 0.0);
            measurementValues.put(BaseColumns.TIMESTAMP, 1);
            Uri result = client.insert(MeasurementTable.Companion.getUri(AUTHORITY), measurementValues);
            Validate.notNull(result, "Measurement insertion failed!");
            Validate.notNull(result.getLastPathSegment());
            final long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(LocationTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(BaseColumns.MEASUREMENT_ID, measurementIdentifier);
            geoLocationValues.put(LocationTable.COLUMN_LON, 1.0);
            geoLocationValues.put(LocationTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(LocationTable.COLUMN_ALTITUDE, 400.);
            geoLocationValues.put(BaseColumns.TIMESTAMP, 1);
            geoLocationValues.put(LocationTable.COLUMN_ACCURACY, 1.0f);
            geoLocationValues.put(LocationTable.COLUMN_VERTICAL_ACCURACY, 20.);
            ContentValues[] geoLocationValuesArray = new ContentValues[numberOftestEntries];
            for (int i = 0; i < numberOftestEntries; i++) {
                geoLocationValuesArray[i] = geoLocationValues;
            }

            long startTime = System.currentTimeMillis();
            // Else we get android.os.TransactionTooLargeException: data parcel size ___ bytes
            for (int startIndex = 0; startIndex < geoLocationValuesArray.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                int endIndex = Math.min(geoLocationValuesArray.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                // BulkInsert is about 80 times faster than insertBatch
                client.bulkInsert(LocationTable.Companion.getUri(AUTHORITY),
                        Arrays.copyOfRange(geoLocationValuesArray, startIndex, endIndex));
                if (startIndex % MAX_SIMULTANEOUS_OPERATIONS * 100 == 0)
                    Log.i(TAG, "Inserting " + startIndex + " entries took: " + (System.currentTimeMillis() - startTime)
                            + " ms");
            }
            Log.i(TAG, "Inserting " + geoLocationValuesArray.length + " entries took: "
                    + (System.currentTimeMillis() - startTime) + " ms");

            // Load entries again
            var oocut = new DefaultProviderClient(measurementIdentifier, client, AUTHORITY);
            startTime = System.currentTimeMillis();

            for (int i = 0; i < geoLocationValuesArray.length; i += DATABASE_QUERY_LIMIT) {
                locationsCursor = oocut.loadGeoLocations(i, DATABASE_QUERY_LIMIT);
                while (locationsCursor.moveToNext()) {
                    numberOfLoadedGeoLocations++;
                }
                Log.i(TAG,
                        "Loading " + locationsCursor.getCount() + " entries  took: "
                                + (System.currentTimeMillis() - startTime) + " ms (" + numberOfLoadedGeoLocations + "/"
                                + geoLocationValuesArray.length + ")");
            }
        } finally {
            if (client != null) {
                client.close();
            }
            if (locationsCursor != null) {
                locationsCursor.close();
            }
        }

        // Assert
        assertThat(numberOfLoadedGeoLocations, is(equalTo(numberOftestEntries)));
    }

    /**
     * Tests the basic {@link DefaultProviderClient} methods.
     */
    @Test
    public void test() throws RemoteException {

        try (ContentProviderClient client = contentResolver.acquireContentProviderClient(AUTHORITY)) {
            Validate.notNull(client,
                    String.format("Unable to initialize content provider client for content provider \"(%s)\"",
                            AUTHORITY));

            // Create test measurement data
            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_MODALITY, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_STATUS, OPEN.getDatabaseIdentifier());
            measurementValues.put(MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                    PERSISTENCE_FILE_FORMAT_VERSION);
            measurementValues.put(MeasurementTable.COLUMN_DISTANCE, 0.0);
            measurementValues.put(BaseColumns.TIMESTAMP, 1);

            // Insert test measurement
            Uri result = client.insert(MeasurementTable.Companion.getUri(AUTHORITY), measurementValues);
            Validate.notNull(result, "Measurement insertion failed!");
            Validate.notNull(result.getLastPathSegment());
            long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            // Create GeoLocation data
            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(LocationTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(BaseColumns.MEASUREMENT_ID, measurementIdentifier);
            geoLocationValues.put(LocationTable.COLUMN_LON, 1.0);
            geoLocationValues.put(LocationTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(LocationTable.COLUMN_ALTITUDE, 400.);
            geoLocationValues.put(BaseColumns.TIMESTAMP, 1L);
            geoLocationValues.put(LocationTable.COLUMN_ACCURACY, 1.0f);
            geoLocationValues.put(LocationTable.COLUMN_VERTICAL_ACCURACY, 20.);

            // Insert GeoLocations
            client.insert(LocationTable.Companion.getUri(AUTHORITY), geoLocationValues);
            client.insert(LocationTable.Companion.getUri(AUTHORITY), geoLocationValues);

            // Check loadGeoLocations()
            DefaultProviderClient oocut = new DefaultProviderClient(measurementIdentifier, client,
                    AUTHORITY);
            Cursor geoLocationCursor = oocut.loadGeoLocations(0, DATABASE_QUERY_LIMIT);
            assertThat(geoLocationCursor.getCount(), is(equalTo(2)));
        }
    }
}
