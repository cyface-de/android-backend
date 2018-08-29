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
import android.support.annotation.Nullable;
import android.util.Log;

import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
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
 * @version 5.0.0
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
     * The authority used to identify the Android content provider to persist data to or load it from.
     */
    private final String authority;

    /**
     * Caching the current measurement identifier, so we do not need to ask the database each time we require the
     * current measurement identifier. This is <code>null</code> if there is no running measurement or if we lost the
     * cache due to Android stopping the application hosting the data capturing service.
     */
    private Long currentMeasurementIdentifier;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     */
    public MeasurementPersistence(final @NonNull ContentResolver resolver, final @NonNull String authority) {
        this.resolver = resolver;
        this.threadPool = Executors.newCachedThreadPool();
        this.authority = authority;
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The newly created <code>Measurement</code>.
     */
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(MeasurementTable.COLUMN_FINISHED, false);
        synchronized (this) {
            Uri resultUri = resolver.insert(getMeasurementUri(), values);

            if (resultUri == null) {
                throw new IllegalStateException("New measurement could not be created!");
            }

            currentMeasurementIdentifier = Long.valueOf(resultUri.getLastPathSegment());
            return new Measurement(currentMeasurementIdentifier);
        }
    }

    /**
     * Close the currently active {@link Measurement}.
     *
     * @return The number of rows successfully updated.
     */
    public int closeRecentMeasurement() {
        // For brevity we are closing all open measurements. If we would like to make sure, that no error has occured we
        // would need to check that there is only one such open measurement before closing anything.
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Closing recent measurements");
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, 1);
        synchronized (this) {
            int updatedRows = resolver.update(getMeasurementUri(), values, MeasurementTable.COLUMN_FINISHED + "=?",
                    new String[]{"0"});
            currentMeasurementIdentifier = null;

            if (BuildConfig.DEBUG)
                Log.d(TAG, "Closed " + updatedRows + " measurements");
            return updatedRows;
        }
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

        threadPool.submit(new CapturedDataWriter(data, resolver, authority, measurementIdentifier,
                new WritingDataCompletedCallback() {
                    @Override
                    public void writingDataCompleted() {
                        // TODO: Add some useful code here as soon as data capturing is activated again.
                        if (BuildConfig.DEBUG)
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

        resolver.insert(getGeoLocationsUri(), values);
    }

    /**
     * Provides information about whether there is a currently open measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     * @throws DataCapturingException If more than one measurement is open or access to the content provider was
     *             impossible. The second case is probably a serious system issue and should not happen.
     */
    public boolean hasOpenMeasurement() throws DataCapturingException {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Checking if app has an open measurement.");
        Cursor openMeasurementQueryCursor = null;
        try {
            synchronized (this) {
                openMeasurementQueryCursor = resolver.query(getMeasurementUri(), null,
                        MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null, null);

                if (openMeasurementQueryCursor == null) {
                    throw new DataCapturingException(
                            "Unable to initialize cursor to check for open measurement. Cursor was null!");
                }

                if (openMeasurementQueryCursor.getCount() > 1) {
                    throw new DataCapturingException("More than one measurement is open.");
                }

                boolean hasOpenMeasurement = openMeasurementQueryCursor.getCount() == 1;
                if (BuildConfig.DEBUG)
                    Log.d(TAG, hasOpenMeasurement ? "One measurement is open." : "No measurement is open.");
                return hasOpenMeasurement;
            }
        } finally {
            if (openMeasurementQueryCursor != null) {
                openMeasurementQueryCursor.close();
            }
        }
    }

    /**
     * Provides the identifier of the measurement currently captured by the framework. This method should only be called
     * if capturing is active. It throws an error otherwise.
     *
     * @return The system wide unique identifier of the active measurement.
     * @throws DataCapturingException If access to the content provider was somehow impossible. This is probably a
     *             serious system issue and should not occur.
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use
     *             {@link #hasOpenMeasurement()} to check, whether there is an actual open measurement.
     */
    private long refreshIdentifierOfCurrentlyCapturedMeasurement()
            throws DataCapturingException, NoSuchMeasurementException {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Trying to load measurement identifier from content provider!");
        Cursor measurementIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                measurementIdentifierQueryCursor = resolver.query(getMeasurementUri(),
                        new String[]{BaseColumns._ID, MeasurementTable.COLUMN_FINISHED},
                        MeasurementTable.COLUMN_FINISHED + "=" + MeasuringPointsContentProvider.SQLITE_FALSE, null,
                        BaseColumns._ID + " DESC");
                if (measurementIdentifierQueryCursor == null) {
                    throw new DataCapturingException("Unable to query for measurement identifier!");
                }

                if (measurementIdentifierQueryCursor.getCount() > 1) {
                    Log.w(TAG,
                            "More than one measurement is open. Unable to decide where to store data! Using the one with the highest identifier!");
                }

                if (!measurementIdentifierQueryCursor.moveToFirst()) {
                    throw new NoSuchMeasurementException("Unable to get measurement to store captured data to!");
                }

                int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor.getColumnIndex(BaseColumns._ID);
                long measurementIdentifier = measurementIdentifierQueryCursor.getLong(indexOfMeasurementIdentifierColumn);
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Providing measurement identifier " + measurementIdentifier);
                return measurementIdentifier;
            }
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

            cursor = resolver.query(getMeasurementUri(), null, null, null, null);
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
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public Measurement loadMeasurement(final long measurementIdentifier) throws DataCapturingException {
        Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier)).build();
        Cursor cursor = null;

        try {
            cursor = resolver.query(measurementUri, null, null, null, null);

            if (cursor == null) {
                throw new DataCapturingException(
                        "Cursor for loading a measurement not correctly initialized. Was null for URI "
                                + measurementUri);
            }

            if (cursor.getCount() > 1) {
                throw new DataCapturingException("Too many measurements loaded from URI: " + measurementUri);
            }

            if (cursor.moveToFirst()) {
                return new Measurement(measurementIdentifier);
            } else {
                return null;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Loads only the finished {@link Measurement} instances from the local persistent data storage. Finished
     * measurements are the ones not currently capturing or paused.
     *
     * @return All the finished measurements from the local persistent data storage.
     */
    public List<Measurement> loadFinishedMeasurements() {
        Cursor cursor = null;

        try {
            List<Measurement> ret = new ArrayList<>();

            cursor = resolver.query(getMeasurementUri(), null, MeasurementTable.COLUMN_FINISHED + "=?",
                    new String[] {String.valueOf(MeasuringPointsContentProvider.SQLITE_TRUE)}, null);
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
        resolver.delete(getRotationsUri(), null, null);
        resolver.delete(getAccelerationsUri(), null, null);
        resolver.delete(getDirectionsUri(), null, null);
        resolver.delete(getGeoLocationsUri(), null, null);
        resolver.delete(getMeasurementUri(), null, null);
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     *
     * @throws NoSuchMeasurementException If the provided measurement was <code>null</code>.
     */
    public void delete(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        if(measurement==null) {
            throw new NoSuchMeasurementException("Unable to delete null measurement!");
        }

        String[] arrayWithMeasurementIdentifier = {Long.valueOf(measurement.getIdentifier()).toString()};
        resolver.delete(getRotationsUri(), RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getAccelerationsUri(), SamplePointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getDirectionsUri(), MagneticValuePointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getGeoLocationsUri(), GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getMeasurementUri(), BaseColumns._ID + "=?", arrayWithMeasurementIdentifier);
    }

    /**
     * Loads the track of <code>GeoLocation</code> objects for the provided measurement.
     *
     * @param measurement The measurement to load the track for.
     * @return The loaded track of <code>GeoLocation</code> objects ordered by time ascending.
     *
     * @throws NoSuchMeasurementException If the provided measurement was <code>null</code>.
     */
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        if(measurement==null) {
            throw new NoSuchMeasurementException("Unable to load track for null measurement!");
        }

        Cursor locationsCursor = null;
        try {
            locationsCursor = resolver.query(getGeoLocationsUri(), null, GpsPointsTable.COLUMN_MEASUREMENT_FK + "=?",
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
     * Loads the identifier of the current measurement from the internal cache if possible, or from the database if an
     * open measurement exists. If neither the cache nor the database have an open measurement this method returns
     * <code>null</code>.
     *
     * @return The identifier of the currently captured measurement or <code>null</code> if none exists.
     */
    public @Nullable Measurement loadCurrentlyCapturedMeasurement() {
        try {
            synchronized (this) {
                if (currentMeasurementIdentifier != null) {
                    return new Measurement(currentMeasurementIdentifier);
                } else if (hasOpenMeasurement()) {
                    return new Measurement(refreshIdentifierOfCurrentlyCapturedMeasurement());
                } else {
                    return null;
                }
            }
        } catch (DataCapturingException e) {
            throw new IllegalStateException("Unrecoverable internal error!", e);
        } catch (NoSuchMeasurementException e) {
            Log.w(TAG, "Trying to load measurement identifier while no measurement was open!");
            return null;
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

    /**
     * @return The content provider URI for the measurement table.
     */
    private Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MeasurementTable.URI_PATH).build();
    }

    /**
     * @return The content provider URI for the geo locations table.
     */
    private Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH).build();
    }

    /**
     * @return The content provider URI for the accelerations table.
     */
    private Uri getAccelerationsUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(SamplePointTable.URI_PATH).build();
    }

    /**
     * @return The content provider URI for the rotations table.
     */
    private Uri getRotationsUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH).build();
    }

    /**
     * @return The content provider URI for the directions table.
     */
    private Uri getDirectionsUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MagneticValuePointTable.URI_PATH)
                .build();
    }
}
