package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>DataCapturingListener</code> and its
 * delegate objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.4
 * @since 2.0.0
 */
public class MeasurementPersistence {

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
     * @param context
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     */
    public MeasurementPersistence(Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
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
     * Finish the currently active {@link Measurement}.
     *
     * @return The number of rows successfully updated.
     */
    public int finishRecentMeasurement() {
        // For brevity we are finishing all {@code MeasurementStatus#OPEN} measurements. In order to make sure, that no
        // error occurred we need to check that there is only one such open measurement before closing anything.
        Log.d(TAG, "Finishing recent measurements");
        ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_FINISHED, 1);
        synchronized (this) {
            int updatedRows = resolver.update(getMeasurementUri(), values, MeasurementTable.COLUMN_FINISHED + "=?",
                    new String[] {"0"});
            currentMeasurementIdentifier = null;

            Log.d(TAG, "Finished " + updatedRows + " measurements");
            return updatedRows;
        }
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     */
    public void storeData(final @NonNull CapturedData data, final long measurementIdentifier,
            final @NonNull WritingDataCompletedCallback callback) {
        if (threadPool.isShutdown()) {
            return;
        }

        CapturedDataWriter writer = new CapturedDataWriter(data, resolver, authority, measurementIdentifier, callback);

        threadPool.submit(writer);
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
     * Provides information about whether there is currently a measurement in the specified {@link MeasurementStatus}.
     *
     * @param status The {@code MeasurementStatus} in question
     * @return <code>true</code> if a measurement is {@param status}; <code>false</code> otherwise.
     * @throws NoSuchMeasurementException If more than one measurement is open or access to the content provider was
     *             impossible. The second case is probably a serious system issue and should not happen.
     */
    public boolean hasMeasurement(@NonNull MeasurementStatus status) throws NoSuchMeasurementException {
        Log.d(TAG, "Checking if app has an " + status + " measurement.");
        Validate.isTrue(status == MeasurementStatus.OPEN, "Not yet implemented");

        final String selection;
        final String[] selectionArgs;
        // noinspection SwitchStatementWithTooFewBranches
        switch (status) {
            case OPEN:
                selection = MeasurementTable.COLUMN_FINISHED + "=?";
                selectionArgs = new String[] {String.valueOf(MeasuringPointsContentProvider.SQLITE_FALSE)};
                break;

            default:
                throw new IllegalArgumentException("Unsupported state: " + status);
        }

        Cursor measurementCursor = null;
        try {
            synchronized (this) {
                measurementCursor = resolver.query(getMeasurementUri(), null, selection, selectionArgs, null);

                if (measurementCursor == null) {
                    throw new NoSuchMeasurementException(
                            "Unable to initialize cursor to check for " + status + " measurement. Cursor was null!");
                }

                if (measurementCursor.getCount() > 1) {
                    throw new NoSuchMeasurementException("More than one measurement is " + status + ".");
                }

                final boolean hasMeasurement = measurementCursor.getCount() == 1;
                Log.d(TAG, hasMeasurement ? "One measurement is " + status + "." : "No measurement is " + status + ".");
                return hasMeasurement;
            }
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
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
     *             {@link #hasMeasurement(MeasurementStatus)} to check, whether there is an actual open measurement.
     */
    private long refreshIdentifierOfCurrentlyCapturedMeasurement()
            throws DataCapturingException, NoSuchMeasurementException {
        Log.d(TAG, "Trying to load measurement identifier from content provider!");
        Cursor measurementIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                measurementIdentifierQueryCursor = resolver.query(getMeasurementUri(),
                        new String[] {BaseColumns._ID, MeasurementTable.COLUMN_FINISHED},
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

                int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor
                        .getColumnIndex(BaseColumns._ID);
                long measurementIdentifier = measurementIdentifierQueryCursor
                        .getLong(indexOfMeasurementIdentifierColumn);
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
     * Loads only the {@link Measurement} in a specific {@link MeasurementStatus} from the local persistent data
     * storage.
     *
     * @param status the {@code MeasurementStatus} for which all {@code Measurement}s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state} from the local persistent data storage.
     */
    public List<Measurement> loadMeasurements(@NonNull final MeasurementStatus status) {
        Validate.isTrue(status == MeasurementStatus.FINISHED, "Not yet supported");

        final String selection;
        final String[] selectionArgs;
        // noinspection SwitchStatementWithTooFewBranches
        switch (status) {
            case FINISHED:
                selection = MeasurementTable.COLUMN_FINISHED + "=?";
                selectionArgs = new String[] {String.valueOf(MeasuringPointsContentProvider.SQLITE_TRUE)};
                break;

            default:
                throw new IllegalArgumentException("Unsupported status" + status);
        }

        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();

            cursor = resolver.query(getMeasurementUri(), null, selection, selectionArgs, null);
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
     * FIXME: also remove all persistence files such as accelerations, etc. and the identifier table?!
     *
     * @return number of rows removed from the database.
     */
    public int clear() {
        int ret = 0;
        ret += resolver.delete(getGeoLocationsUri(), null, null);
        ret += resolver.delete(getMeasurementUri(), null, null);
        throw new IllegalStateException("Not implemented");
        // return ret;
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     *
     * @throws NoSuchMeasurementException If the provided measurement was <code>null</code>.
     */
    public void delete(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        if (measurement == null) {
            throw new NoSuchMeasurementException("Unable to delete null measurement!");
        }

        String[] arrayWithMeasurementIdentifier = {Long.valueOf(measurement.getIdentifier()).toString()};
        resolver.delete(getRotationsUri(), RotationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getAccelerationsUri(), AccelerationPointTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getDirectionsUri(), DirectionPointTable.COLUMN_MEASUREMENT_FK + "=?",
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
        if (measurement == null) {
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
        return new Uri.Builder().scheme("content").authority(authority).appendPath(AccelerationPointTable.URI_PATH)
                .build();
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
        return new Uri.Builder().scheme("content").authority(authority).appendPath(DirectionPointTable.URI_PATH)
                .build();
    }

    /**
     * When pausing or stopping a {@link Measurement} we store the {@link Point3d} counters and the
     * {@link MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION} in the {@link Measurement} to make sure we can
     * deserialize the {@link Point3dFile}s with deprecated {@code PERSISTENCE_FILE_FORMAT_VERSION}s. This also could
     * avoid corrupting {@code Point3dFile}s when the last bytes could not be written successfully.
     *
     * @param pointMetaData The {@code Point3dFile} meta information required for deserialization
     * @param measurementId The id of the measurement associated with the {@link PointMetaData}
     */
    public void storePointMetaData(@NonNull final PointMetaData pointMetaData, final long measurementId) {
        // FIXME:
        throw new IllegalStateException("not yet implemented");
    }

    /**
     * Loads the {@code PointMetaData} for a specific measurement, e.g. to resume the capturing.
     *
     * @param measurementId The id of the measurement to load the {@code PointMetaData} for
     * @return the requested {@link PointMetaData}
     */
    public PointMetaData loadPointMetaData(final long measurementId) {
        // FIXME
        throw new IllegalStateException("not yet implemented");
    }
}
