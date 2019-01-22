package de.cyface.synchronization;

import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.synchronization.TestUtils.AUTHORITY;
import static de.cyface.synchronization.TestUtils.TAG;
import static de.cyface.synchronization.TestUtils.insertSampleMeasurement;
import static de.cyface.testutils.SharedTestUtils.getGeoLocationsUri;
import static de.cyface.testutils.SharedTestUtils.getMeasurementUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.DataCapturingException;
import de.cyface.utils.Validate;

/**
 * Tests that instances of the <code>MeasurementContentProviderClient</code> do work correctly.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementContentProviderClientTest {
    /**
     * Constant you can play around with to find out how many simultaneous operations are possible in one transaction to
     * insert into the <code>ContentProvider</code>.
     */
    private final static int MAX_SIMULTANEOUS_OPERATIONS = 550;

    /**
     * This test makes sure that larger GeoLocation tracks can be loaded completely form the
     * database as there was a bug which limited the query size to 10_000 entries #MOV-248.
     */
    @Test
    public void testLoadGeoLocations_10hTrack() throws RemoteException {
        // The Location frequency is always 1 Hz, i.e. 10h of measurement:
        testLoadGeoLocations(3600 * 10);
    }

    @Ignore
    public void testLoadGeoLocations(int numberOftestEntries) throws RemoteException {
        // Arrange
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentProviderClient client = null;
        Cursor locationsCursor = null;
        int numberOfLoadedGeoLocations = 0;

        // Act: Store and load the test entries
        try {
            client = context.getContentResolver().acquireContentProviderClient(AUTHORITY);
            if (client == null) {
                throw new IllegalStateException(String.format(
                        "Unable to initialize content provider client for content provider \"(%s)\"", AUTHORITY));
            }

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_STATUS, OPEN.getDatabaseIdentifier());
            Uri result = client.insert(getMeasurementUri(AUTHORITY), measurementValues);
            Validate.notNull("Measurement insertion failed!", result);
            Validate.notNull(result.getLastPathSegment());
            final long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(GeoLocationsTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            geoLocationValues.put(GeoLocationsTable.COLUMN_LON, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_GPS_TIME, 1);
            geoLocationValues.put(GeoLocationsTable.COLUMN_ACCURACY, 1);
            ContentValues[] geoLocationValuesArray = new ContentValues[numberOftestEntries];
            for (int i = 0; i < numberOftestEntries; i++) {
                geoLocationValuesArray[i] = geoLocationValues;
            }

            long startTime = System.currentTimeMillis();
            // Else we get android.os.TransactionTooLargeException: data parcel size ___ bytes
            for (int startIndex = 0; startIndex < geoLocationValuesArray.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                int endIndex = Math.min(geoLocationValuesArray.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                // BulkInsert is about 80 times faster than insertBatch
                client.bulkInsert(getGeoLocationsUri(AUTHORITY),
                        Arrays.copyOfRange(geoLocationValuesArray, startIndex, endIndex));
                if (startIndex % MAX_SIMULTANEOUS_OPERATIONS * 100 == 0)
                    Log.i(TAG, "Inserting " + startIndex + " entries took: " + (System.currentTimeMillis() - startTime)
                            + " ms");
            }
            Log.i(TAG, "Inserting " + geoLocationValuesArray.length + " entries took: "
                    + (System.currentTimeMillis() - startTime) + " ms");

            // Load entries again
            MeasurementContentProviderClient oocut = new MeasurementContentProviderClient(measurementIdentifier, client,
                    AUTHORITY);
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    client.release();
                } else {
                    client.close();
                }
            }
            if (locationsCursor != null) {
                locationsCursor.close();
            }
        }

        // Assert
        assertThat(numberOfLoadedGeoLocations, is(equalTo(numberOftestEntries)));
    }

    @Test
    public void test() throws RemoteException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentProviderClient client = null;
        // FIXME: here were the sensor points, too, but they were removed as they are not loaded via content provider

        try {
            client = context.getContentResolver().acquireContentProviderClient(AUTHORITY);
            if (client == null) {
                throw new IllegalStateException(String.format(
                        "Unable to initialize content provider client for content provider \"(%s)\"", AUTHORITY));
            }

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_STATUS, OPEN.getDatabaseIdentifier());
            Uri result = client.insert(getMeasurementUri(AUTHORITY), measurementValues);
            Validate.notNull("Measurement insertion failed!", result);
            Validate.notNull(result.getLastPathSegment());
            long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(GeoLocationsTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            geoLocationValues.put(GeoLocationsTable.COLUMN_LON, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(GeoLocationsTable.COLUMN_GPS_TIME, 1L);
            geoLocationValues.put(GeoLocationsTable.COLUMN_ACCURACY, 1);
            client.insert(getGeoLocationsUri(AUTHORITY), geoLocationValues);
            client.insert(getGeoLocationsUri(AUTHORITY), geoLocationValues);

            MeasurementContentProviderClient oocut = new MeasurementContentProviderClient(measurementIdentifier, client,
                    AUTHORITY);

            // FIXME: here was the method tested which marks the measurement as synced and deleted the point3d data
            assertThat(oocut.cleanMeasurement(), is(equalTo(9)));
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * Tests whether the sync adapter loads the correct measurements for synchronization.
     *
     */
    @Test
    public void testGetSyncableMeasurement() throws NoSuchMeasurementException, DataCapturingException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PersistenceLayer persistenceLayer = new PersistenceLayer(context, context.getContentResolver(), AUTHORITY,
                new DefaultPersistenceBehaviour());

        // Create a not finished measurement
        insertSampleMeasurement(context, AUTHORITY, OPEN, persistenceLayer);

        // Create a not synced finished measurement
        Measurement finishedMeasurement = insertSampleMeasurement(context, AUTHORITY, FINISHED, persistenceLayer);

        // Create a synchronized measurement
        insertSampleMeasurement(context, AUTHORITY, SYNCED, persistenceLayer);

        final List<Measurement> loadedMeasurements = persistenceLayer.loadMeasurements(FINISHED);

        assertThat(loadedMeasurements.size(), is(1));
        assertThat(loadedMeasurements.get(0).getIdentifier(), is(equalTo(finishedMeasurement.getIdentifier())));

    }
}
