package de.cyface.datacapturing.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>DataCapturingListener</code> and its
 * delegate objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.2
 * @since 2.0.0
 */
public class MeasurementPersistence {

    /**
     * Tag used to identify messages on logcat.
     */
    private static final String TAG = "de.cyface.capturing";
    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;
    /**
     * A threadPool to execute operations on their own background threads.
     */
    private ExecutorService threadPool;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    public MeasurementPersistence(final @NonNull ContentResolver resolver) {
        this.resolver = resolver;
        threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The system wide unique identifier of the newly created <code>Measurement</code>.
     */
    public long newMeasurement(final @NonNull Vehicle vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_FINISHED, false);
        Uri resultUri = resolver.insert(MeasuringPointsContentProvider.MEASUREMENT_URI, values);

        if (resultUri == null) {
            throw new IllegalStateException("New measurement could not be created!");
        }

        return Long.valueOf(resultUri.getLastPathSegment());
    }

    /**
     * Close the currently active {@link Measurement}.
     *
     * @return The number of rows successfully updated.
     */
    public int closeRecentMeasurement() {
        // For brevity we are closing all open measurements. If we would like to make sure, that no error has occured we
        // would need to check that there is only one such open measurement before closing anything.
        Log.d(TAG, "Closing recent measurements");
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, 1);
        int updatedRows = resolver.update(MeasuringPointsContentProvider.MEASUREMENT_URI, values,
                MeasurementTable.COLUMN_FINISHED + "=?", new String[]{"0"});
        Log.d(TAG, "Closed "+updatedRows+" measurements");
        return updatedRows;
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     */
    public void storeData(final @NonNull CapturedData data, final long measurementIdentifier) {
        if (threadPool.isShutdown()) {
            return;
        }

        threadPool.submit(new CapturedDataWriter(data, resolver, measurementIdentifier, new WritingDataCompletedCallback() {
            @Override
            public void writingDataCompleted() {
                // TODO: Add some useful code here as soon as data capturing is activated again.
                Log.d(TAG, "Completed writing data.");
            }
        }));
    }

    /**
     * Stores the provided geo location under the currently active captured measurement.
     *
     * @param location The geo location to store.
     * @param measurementIdentifier The identifier of the measurement to store the data to.
     */
    public void storeLocation(final @NonNull GeoLocation location, final long measurementIdentifier) {

        ContentValues values = new ContentValues();
        // Android gets the accuracy in meters but we save it in centimeters to reduce size during transmission
        values.put(GpsPointsTable.COLUMN_ACCURACY, Math.round(location.getAccuracy() * 100));
        values.put(GpsPointsTable.COLUMN_GPS_TIME, location.getTimestamp());
        values.put(GpsPointsTable.COLUMN_IS_SYNCED, false);
        values.put(GpsPointsTable.COLUMN_LAT, location.getLat());
        values.put(GpsPointsTable.COLUMN_LON, location.getLon());
        values.put(GpsPointsTable.COLUMN_SPEED, location.getSpeed());
        values.put(GpsPointsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);

        resolver.insert(MeasuringPointsContentProvider.GPS_POINTS_URI, values);
    }

    /**
     * Provides information about whether there is a currently open measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     * @throws DataCapturingException If more than one measurement is open.
     */
    public boolean hasOpenMeasurement() throws DataCapturingException {
        Log.d(TAG, "Checking if app has an open measurement.");
        Cursor openMeasurementQueryCursor = null;
        try {
            openMeasurementQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null, null);

            if (openMeasurementQueryCursor.getCount() > 1) {
                throw new DataCapturingException("More than one measurement is open.");
            }

            boolean hasOpenMeasurement = openMeasurementQueryCursor.getCount() == 1;
            Log.d(TAG, hasOpenMeasurement ? "One measurement is open.": "No measurement is open.");
            return hasOpenMeasurement;
        } finally {
            if (openMeasurementQueryCursor != null) {
                openMeasurementQueryCursor.close();
            }
        }
    }

    /**
     * Provides the identifier of the measurement currently captured by the framework. This method should only be called
     * if capturing is active or throw an error otherwise.
     *
     * @return The system wide unique identifier of the active measurement.
     */
    public long getIdentifierOfCurrentlyCapturedMeasurement() {
        Log.d(TAG, "Trying to load measurement identifier from content provider!");
        Cursor measurementIdentifierQueryCursor = null;
        try {
            measurementIdentifierQueryCursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI,
                    new String[] {BaseColumns._ID, MeasurementTable.COLUMN_FINISHED},
                    MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null,
                    BaseColumns._ID + " DESC");
            if (measurementIdentifierQueryCursor == null) {
                throw new IllegalStateException("Unable to query for measurement identifier!");
            }

            if (measurementIdentifierQueryCursor.getCount() > 1) {
                Log.w(TAG,
                        "More than one measurement is open. Unable to decide where to store data! Using the one with the highest identifier!");
            }

            if (!measurementIdentifierQueryCursor.moveToFirst()) {
                throw new IllegalStateException("Unable to get measurement to store captured data to!");
            }

            int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor.getColumnIndex(BaseColumns._ID);
            long measurementIdentifier = measurementIdentifierQueryCursor.getLong(indexOfMeasurementIdentifierColumn);
            Log.d(TAG, "Providing measurement identifier "+measurementIdentifier);
            return measurementIdentifier;
        } finally {
            if (measurementIdentifierQueryCursor != null) {
                measurementIdentifierQueryCursor.close();
            }
        }
    }

    /**
     * @return All measurements currently in the local persistent data storage.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();

            cursor = resolver.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null, null, null);
            if (cursor == null) {
                throw new IllegalStateException("Unable to access database to load measurements!");
            }

            while (cursor.moveToNext()) {
                long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));

                ret.add(new Measurement(measurementIdentifier));
            }

            return ret;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Removes everything from the local persistent data storage.
     */
    public void clear() {
        resolver.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.GPS_POINTS_URI, null, null);
        resolver.delete(MeasuringPointsContentProvider.MEASUREMENT_URI, null, null);
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     */
    public void delete(final @NonNull Measurement measurement) {
        String[] arrayWithMeasurementIdentifier = {Long.valueOf(measurement.getIdentifier()).toString()};
        resolver.delete(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                RotationPointTable.COLUMN_MEASUREMENT_FK + "=?", arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.SAMPLE_POINTS_URI, SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?", arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.GPS_POINTS_URI, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(MeasuringPointsContentProvider.MEASUREMENT_URI, BaseColumns._ID + "=?",
                arrayWithMeasurementIdentifier);
    }

    /**
     * Loads the track of <code>GeoLocation</code> objects for the provided measurement.
     *
     * @param measurement The measurement to load the track for.
     * @return The loaded track of <code>GeoLocation</code> objects ordered by time ascending.
     */
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) {
        Cursor locationsCursor = null;
        try {
            locationsCursor = resolver.query(MeasuringPointsContentProvider.GPS_POINTS_URI, null,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()},
                    GpsPointsTable.COLUMN_GPS_TIME + " ASC");

            if (locationsCursor == null) {
                return Collections.emptyList();
            }

            List<GeoLocation> ret = new ArrayList<>(locationsCursor.getCount());
            while (locationsCursor.moveToNext()) {
                double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT));
                double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON));
                long timestamp = locationsCursor
                        .getLong(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME));
                double speed = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED));
                float accuracy = locationsCursor
                        .getFloat(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY));

                ret.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
            }

            return ret;
        } finally {
            if (locationsCursor != null) {
                locationsCursor.close();
            }
        }
    }

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     */
    public void shutdown() {
        if (threadPool != null) {
            try {
                threadPool.shutdown();
                threadPool.awaitTermination(1, TimeUnit.SECONDS);
                threadPool.shutdownNow();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
