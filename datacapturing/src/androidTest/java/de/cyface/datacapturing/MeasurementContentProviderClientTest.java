package de.cyface.datacapturing;

import static de.cyface.datacapturing.persistence.CapturedDataWriter.MAX_SIMULTANEOUS_OPERATIONS;
import static de.cyface.persistence.AbstractCyfaceMeasurementTable.DATABASE_QUERY_LIMIT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
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
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.synchronization.MeasurementContentProviderClient;

/**
 * This test is places in datacapturing as it has dependencies on: datacapturing, synchronization
 * and persistence and it's the only module which can access all three of them.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 2.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementContentProviderClientTest {

    /**
     * The tag used to identify Logcat messages from this class.
     */
    private final static String TAG = "de.cyface.test";

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
        Context context = InstrumentationRegistry.getTargetContext();
        ContentProviderClient client = null;
        Cursor locationsCursor = null;
        List<GeoLocation> geoLocations = new ArrayList<>();

        // Act: Store and load the test entries
        try {
            client = context.getContentResolver().acquireContentProviderClient(BuildConfig.provider);

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_FINISHED, 1);
            Uri result = client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, measurementValues);
            final long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues geoLocationValues = new ContentValues();
            geoLocationValues.put(GpsPointsTable.COLUMN_SPEED, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
            geoLocationValues.put(GpsPointsTable.COLUMN_LON, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_LAT, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_IS_SYNCED, 1.0);
            geoLocationValues.put(GpsPointsTable.COLUMN_GPS_TIME, 1);
            geoLocationValues.put(GpsPointsTable.COLUMN_ACCURACY, 1);
            ContentValues[] geoLocationValuesArray = new ContentValues[numberOftestEntries];
            for (int i = 0; i < numberOftestEntries; i++) {
                geoLocationValuesArray[i] = geoLocationValues;
            }

            long startTime = System.currentTimeMillis();
            // Else we get android.os.TransactionTooLargeException: data parcel size ___ bytes
            for (int startIndex = 0; startIndex < geoLocationValuesArray.length; startIndex += MAX_SIMULTANEOUS_OPERATIONS) {
                int endIndex = Math.min(geoLocationValuesArray.length, startIndex + MAX_SIMULTANEOUS_OPERATIONS);
                // BulkInsert is about 80 times faster than insertBatch
                client.bulkInsert(MeasuringPointsContentProvider.GPS_POINTS_URI,
                        Arrays.copyOfRange(geoLocationValuesArray, startIndex, endIndex));
                if (startIndex % MAX_SIMULTANEOUS_OPERATIONS * 100 == 0)
                    Log.i(TAG, "Inserting " + startIndex + " entries took: " + (System.currentTimeMillis() - startTime)
                            + " ms");
            }
            Log.i(TAG, "Inserting " + geoLocationValuesArray.length + " entries took: "
                    + (System.currentTimeMillis() - startTime) + " ms");

            // Load entries again
            MeasurementContentProviderClient oocut = new MeasurementContentProviderClient(measurementIdentifier,
                    client);
            startTime = System.currentTimeMillis();

            for (int i = 0; i < geoLocationValuesArray.length; i += DATABASE_QUERY_LIMIT) {
                locationsCursor = oocut.loadGeoLocations(i, DATABASE_QUERY_LIMIT);
                while (locationsCursor.moveToNext()) {
                    double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT));
                    double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON));
                    long timestamp = locationsCursor
                            .getLong(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME));
                    double speed = locationsCursor
                            .getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED));
                    float accuracy = locationsCursor
                            .getFloat(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY));

                    geoLocations.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
                }
                Log.i(TAG,
                        "Loading " + locationsCursor.getCount() + " entries  took: "
                                + (System.currentTimeMillis() - startTime) + " ms (" + geoLocations.size() + "/"
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
        assertThat(geoLocations.size(), is(equalTo(numberOftestEntries)));
    }
}
