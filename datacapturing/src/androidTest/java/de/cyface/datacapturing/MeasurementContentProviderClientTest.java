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
@LargeTest
public class MeasurementContentProviderClientTest {

    private final static String TAG = "de.cyface.datacapturing";

    @Test
    public void testLoadGeoLocations_largeAmount() throws RemoteException, OperationApplicationException {
        testLoadGeoLocations_largeAmount(10);
        testLoadGeoLocations_largeAmount(100);
        testLoadGeoLocations_largeAmount(1_001);
        testLoadGeoLocations_largeAmount(10_001);
        testLoadGeoLocations_largeAmount(20_001);
    }

    @Ignore
    public void testLoadGeoLocations_largeAmount(int numberOftestEntries) throws RemoteException, OperationApplicationException {
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

            List<GeoLocation> geoLocationList = new ArrayList<>();
            for (long i = 0L; i < numberOftestEntries; i++) {
                geoLocationList.add(new GeoLocation(1.0, 1.0, i, 1.0, 1));
            }
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            operations.addAll(MeasurementPersistence.newGeoLocationInsertOperation(geoLocationList,
                    MeasuringPointsContentProvider.GPS_POINTS_URI, new MeasurementPersistence.Mapper<GeoLocation>() {
                        @Override
                        public ContentValues map(GeoLocation geoLocation) {
                            ContentValues geoLocationValues = new ContentValues();
                            geoLocationValues.put(GpsPointsTable.COLUMN_SPEED, geoLocation.getSpeed());
                            geoLocationValues.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);
                            geoLocationValues.put(GpsPointsTable.COLUMN_LON, geoLocation.getLon());
                            geoLocationValues.put(GpsPointsTable.COLUMN_LAT, geoLocation.getLat());
                            geoLocationValues.put(GpsPointsTable.COLUMN_IS_SYNCED, geoLocation.getSpeed());
                            geoLocationValues.put(GpsPointsTable.COLUMN_GPS_TIME, geoLocation.getTimestamp());
                            geoLocationValues.put(GpsPointsTable.COLUMN_ACCURACY, geoLocation.getAccuracy());
                            return geoLocationValues;
                        }
                    }));

            long startTime = System.currentTimeMillis();
            // Else we get android.os.TransactionTooLargeException: data parcel size ___ bytes
            for (int i = 0; i < operations.size(); i += MAX_SIMULTANEOUS_OPERATIONS) {
                int startIndex = i;
                int endIndex = Math.min(operations.size(),i+MAX_SIMULTANEOUS_OPERATIONS);//TODO removed "-1" here, do the same in the real code, else we loose every 500th dataPoint
                client.applyBatch(new ArrayList<>(operations.subList(startIndex, endIndex)));
                Log.i(TAG, "Inserted "+ (endIndex-startIndex) +" entries");
            }
            Log.i(TAG, "Inserting "+ operations.size() +" entries took: "+(System.currentTimeMillis() - startTime) + " ms");

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
