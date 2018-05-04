package de.cyface.datacapturing;

import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Point3D;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.persistence.BuildConfig;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.SamplePointTable;
import de.cyface.synchronization.MeasurementContentProviderClient;
import de.cyface.synchronization.MeasurementSerializer;

import static de.cyface.datacapturing.persistence.MeasurementPersistence.MAX_SIMULTANEOUS_OPERATIONS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * TODO: To avoid circular dependencies I had to place this test into the capturing module instead
 * of the synchronization module as   MeasurementContentProviderClient. Also, I am not sure, if
 * the "make public" changes I had to do for this tests are ok.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MeasurementContentProviderClientTest {

    private final static String TAG = "de.cyface.datacapturing";

    @Test
    public void testLoadGeoLocations_largeAmount() throws RemoteException {
        testLoadGeoLocations_largeAmount(10);
        testLoadGeoLocations_largeAmount(100);
        testLoadGeoLocations_largeAmount(1_001);
        testLoadGeoLocations_largeAmount(10_001);
        testLoadGeoLocations_largeAmount(20_001);
        testLoadGeoLocations_largeAmount(100_001);
    }

    @Ignore
    public void testLoadGeoLocations_largeAmount(int numberOftestEntries) throws RemoteException {
        // Arrange
        Context context = InstrumentationRegistry.getTargetContext();
        ContentProviderClient client = null;
        Cursor locationsCursor = null;
        int numberOfLoadedEntries;

        // Act: Store and load the test entries
        try {
            client = context.getContentResolver().acquireContentProviderClient(BuildConfig.provider);

            ContentValues measurementValues = new ContentValues();
            measurementValues.put(MeasurementTable.COLUMN_VEHICLE, "BICYCLE");
            measurementValues.put(MeasurementTable.COLUMN_FINISHED, 1);
            Uri result = client.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, measurementValues);
            final long measurementIdentifier = Long.parseLong(result.getLastPathSegment());

            ContentValues[] geoLocationValuesArray = new ContentValues[numberOftestEntries];
            for (int i = 0; i < numberOftestEntries; i++) {
                ContentValues geoLocationValues = new ContentValues();
                geoLocationValues.put(GpsPointsTable.COLUMN_SPEED, 1.0);
                geoLocationValues.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                geoLocationValues.put(GpsPointsTable.COLUMN_LON, 1.0);
                geoLocationValues.put(GpsPointsTable.COLUMN_LAT, 1.0);
                geoLocationValues.put(GpsPointsTable.COLUMN_IS_SYNCED, 1.0);
                geoLocationValues.put(GpsPointsTable.COLUMN_GPS_TIME, i);
                geoLocationValues.put(GpsPointsTable.COLUMN_ACCURACY, 1);
                geoLocationValuesArray[i] = geoLocationValues;
            }

            long startTime = System.currentTimeMillis();
            // Else we get android.os.TransactionTooLargeException: data parcel size ___ bytes
            for (int i = 0; i < geoLocationValuesArray.length; i += MAX_SIMULTANEOUS_OPERATIONS) {
                int startIndex = i;
                int endIndex = Math.min(geoLocationValuesArray.length,i+MAX_SIMULTANEOUS_OPERATIONS);//TODO removed "-1" here, do the same in the real code, else we loose every 500th dataPoint
                client.bulkInsert(MeasuringPointsContentProvider.GPS_POINTS_URI, Arrays.copyOfRange(geoLocationValuesArray, startIndex, endIndex));
                Log.i(TAG, "Inserted "+ (endIndex-startIndex) +" entries");
            }
            Log.i(TAG, "Inserting "+ geoLocationValuesArray.length +" entries took: "+(System.currentTimeMillis() - startTime) + " ms");

            // Load entries again
            MeasurementContentProviderClient oocut = new MeasurementContentProviderClient(measurementIdentifier,
                    client);
            startTime = System.currentTimeMillis();
            locationsCursor = oocut.loadGeoLocations();
            numberOfLoadedEntries = locationsCursor.getCount();
            Log.i(TAG, "Counting "+ numberOfLoadedEntries +" entries took: "+(System.currentTimeMillis() - startTime) + " ms");

            List<GeoLocation> geoLocations = new ArrayList<>(locationsCursor.getCount());
            while (locationsCursor.moveToNext()) {
                double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT));
                double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON));
                long timestamp = locationsCursor
                        .getLong(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME));
                double speed = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED));
                float accuracy = locationsCursor
                        .getFloat(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY));

                geoLocations.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
            }
            Log.i(TAG, "Loading "+geoLocations.size()+" entries took: "+(System.currentTimeMillis() - startTime) + " ms");
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
        assertThat(numberOfLoadedEntries, is(equalTo(numberOftestEntries)));
    }
}
