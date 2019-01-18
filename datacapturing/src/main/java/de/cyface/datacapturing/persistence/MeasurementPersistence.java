package de.cyface.datacapturing.persistence;

import static android.provider.BaseColumns._ID;
import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.persistence.MeasurementTable.COLUMN_ACCELERATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_DIRECTIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.MeasurementTable.COLUMN_ROTATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_STATUS;

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
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
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
 * @version 7.1.0
 * @since 2.0.0
 */
public class MeasurementPersistence {

    /**
     * The {@link Context} required to locate the app's internal storage directory.
     */
    private final Context context;
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
     * The {@link Point3dFile} to write the acceleration points to.
     */
    private Point3dFile accelerationsFile;
    /**
     * The {@link Point3dFile} to write the rotation points to.
     */
    private Point3dFile rotationsFile;
    /**
     * The {@link Point3dFile} to write the direction points to.
     */
    private Point3dFile directionsFile;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param context The {@link Context} required to locate the app's internal storage directory.
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     */
    public MeasurementPersistence(Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
        this.context = context;
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
        final ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(COLUMN_STATUS, MeasurementStatus.OPEN.getDatabaseIdentifier());
        synchronized (this) {
            Uri resultUri = resolver.insert(getMeasurementUri(), values);
            Validate.notNull("New measurement could not be created!", resultUri);
            Validate.notNull(resultUri.getLastPathSegment());

            currentMeasurementIdentifier = Long.valueOf(resultUri.getLastPathSegment());
            return new Measurement(currentMeasurementIdentifier);
        }
    }

    /**
     * Finish the currently active {@link Measurement}.
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     */
    public void finishRecentMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "Finishing recent measurement");
        synchronized (this) {
            try {
                setStatus(loadCurrentlyCapturedMeasurement().getIdentifier(), MeasurementStatus.FINISHED);
            } finally {
                currentMeasurementIdentifier = null;
            }
        }
    }

    /**
     * Pause the currently active {@link Measurement}.
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     */
    public void pauseRecentMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "Pausing recent measurement");
        synchronized (this) {
            setStatus(loadCurrentlyCapturedMeasurement().getIdentifier(), MeasurementStatus.PAUSED);
        }
    }

    /**
     * Resumes the currently active {@link Measurement}.
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     */
    public void resumeRecentMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "Resuming recent measurement");
        synchronized (this) {
            setStatus(loadCurrentlyCapturedMeasurement().getIdentifier(), MeasurementStatus.OPEN);
        }
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     * @param measurementIdentifier The id of the {@link Measurement} to store the data to.
     */
    public void storeData(final @NonNull CapturedData data, final long measurementIdentifier,
            final @NonNull WritingDataCompletedCallback callback) {
        if (threadPool.isShutdown()) {
            return;
        }
        if (accelerationsFile == null) {
            accelerationsFile = new Point3dFile(context, measurementIdentifier, Point3dFile.ACCELERATIONS_FOLDER_NAME,
                    Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        }
        if (rotationsFile == null) {
            rotationsFile = new Point3dFile(context, measurementIdentifier, Point3dFile.ROTATIONS_FOLDER_NAME,
                    Point3dFile.ROTATION_FILE_EXTENSION);
        }
        if (directionsFile == null) {
            directionsFile = new Point3dFile(context, measurementIdentifier, Point3dFile.DIRECTIONS_FOLDER_NAME,
                    Point3dFile.DIRECTION_FILE_EXTENSION);
        }

        final CapturedDataWriter writer = new CapturedDataWriter(data, resolver, authority, measurementIdentifier,
                accelerationsFile, rotationsFile, directionsFile, callback);

        threadPool.submit(writer);
    }

    /**
     * Stores the provided geo location under the currently active captured measurement.
     *
     * @param location The geo location to store.
     * @param measurementIdentifier The identifier of the measurement to store the data to.
     */
    public void storeLocation(final @NonNull GeoLocation location, final long measurementIdentifier) {

        final ContentValues values = new ContentValues();
        // Android gets the accuracy in meters but we save it in centimeters to reduce size during transmission
        values.put(GeoLocationsTable.COLUMN_ACCURACY, Math.round(location.getAccuracy() * 100));
        values.put(GeoLocationsTable.COLUMN_GPS_TIME, location.getTimestamp());
        values.put(GeoLocationsTable.COLUMN_LAT, location.getLat());
        values.put(GeoLocationsTable.COLUMN_LON, location.getLon());
        values.put(GeoLocationsTable.COLUMN_SPEED, location.getSpeed());
        values.put(GeoLocationsTable.COLUMN_MEASUREMENT_FK, measurementIdentifier);

        resolver.insert(getGeoLocationsUri(), values);
    }

    /**
     * Provides information about whether there is currently a measurement in the specified {@link MeasurementStatus}.
     *
     * @param status The {@code MeasurementStatus} in question
     * @return <code>true</code> if a measurement is {@param status}; <code>false</code> otherwise.
     * @throws IllegalStateException If access to the content provider was impossible. This is probably a serious
     *             system issue and should not happen.
     */
    public boolean hasMeasurement(@NonNull MeasurementStatus status) {
        Log.d(TAG, "Checking if app has an " + status + " measurement."); // FIXME: make sure we set the status to pause
                                                                          // and synced (not just finished, paused and
                                                                          // open)

        Cursor measurementCursor = null;
        try {
            synchronized (this) {
                measurementCursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                        new String[] {status.getDatabaseIdentifier()}, null);

                if (measurementCursor == null) {
                    throw new IllegalStateException(
                            "Unable to initialize cursor to check for " + status + " measurement. Cursor was null!");
                }

                final boolean hasMeasurement = measurementCursor.getCount() > 0;
                Log.d(TAG, hasMeasurement ? "At least one measurement is " + status + "."
                        : "No measurement is " + status + ".");
                return hasMeasurement;
            }
        } finally {
            if (measurementCursor != null) {
                measurementCursor.close();
            }
        }
    }

    /**
     * Loads the currently captured measurement and refreshes the {@link #currentMeasurementIdentifier} reference. This
     * method should only be called if capturing is active. It throws an error otherwise.
     *
     * @throws DataCapturingException If access to the content provider was somehow impossible. This is probably a
     *             serious system issue and should not occur.
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use {@link #hasMeasurement(MeasurementStatus)} to check whether there is an actual
     *             {@link MeasurementStatus#OPEN} measurement.
     */
    private void refreshIdentifierOfCurrentlyCapturedMeasurement()
            throws DataCapturingException, NoSuchMeasurementException {
        Log.d(TAG, "Trying to load currently captured measurement from persistence layer!");

        final List<Measurement> openMeasurements = loadMeasurements(MeasurementStatus.OPEN);
        if (openMeasurements.size() == 0) {
            throw new NoSuchMeasurementException("No open measurement found!");
        }
        if (openMeasurements.size() > 1) {
            throw new IllegalStateException("More than one measurement is open.");
        }
        currentMeasurementIdentifier = openMeasurements.get(0).getIdentifier();
        Log.d(TAG, "Refreshed currentMeasurementIdentifier to: " + currentMeasurementIdentifier);
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
                long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(_ID));

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
            cursor = resolver.query(measurementUri, null, _ID + "=?",
                    new String[] {String.valueOf(measurementIdentifier)}, null);

            if (cursor == null) {
                throw new DataCapturingException( // FIXME: do we really still need to soft capture this?
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
     * Provide the {@link MeasurementStatus} of one specific measurement from the data storage.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded {@code MeasurementStatus}
     * @throws NoSuchMeasurementException If accessing the measurement does not exist.
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public MeasurementStatus loadMeasurementStatus(final long measurementIdentifier)
            throws DataCapturingException, NoSuchMeasurementException {
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
            if (!cursor.moveToFirst()) {
                throw new NoSuchMeasurementException("Failed to load MeasurementStatus.");
            }

            return MeasurementStatus.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_STATUS)));
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

        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();

            cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                    new String[] {status.getDatabaseIdentifier()}, null);
            if (cursor == null) {
                throw new IllegalStateException("Unable to access database to load measurements!");
            }

            while (cursor.moveToNext()) {
                long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(_ID));

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
        if (measurement == null) { // FIXME: do we need to support this for RM? if so, annotate this here with
                                   // //noinspection
            throw new NoSuchMeasurementException("Unable to delete null measurement!");
        }

        String[] arrayWithMeasurementIdentifier = {Long.valueOf(measurement.getIdentifier()).toString()};
        resolver.delete(getGeoLocationsUri(), GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                arrayWithMeasurementIdentifier);
        resolver.delete(getMeasurementUri(), _ID + "=?", arrayWithMeasurementIdentifier);
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
        if (measurement == null) {// FIXME: do we need to support this for RM? if so, annotate this here with
            // //noinspection
            throw new NoSuchMeasurementException("Unable to load track for null measurement!");
        }

        Cursor locationsCursor = null;
        try {
            locationsCursor = resolver.query(getGeoLocationsUri(), null, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()},
                    GeoLocationsTable.COLUMN_GPS_TIME + " ASC");

            if (locationsCursor == null) {
                return Collections.emptyList();
            }

            List<GeoLocation> ret = new ArrayList<>(locationsCursor.getCount());
            while (locationsCursor.moveToNext()) {
                double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT));
                double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_LON));
                long timestamp = locationsCursor
                        .getLong(locationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_GPS_TIME));
                double speed = locationsCursor
                        .getDouble(locationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED));
                float accuracy = locationsCursor
                        .getFloat(locationsCursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY));

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
     * Loads the current {@link Measurement} from the internal cache if possible, or from the persistence layer if an
     * {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED}
     * {@code Measurement} exists.
     *
     * @return The currently captured {@code Measurement}
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     *             {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED}
     *             {@code Measurement}
     */
    public @NonNull Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException {
        synchronized (this) {
            if (currentMeasurementIdentifier == null
                    && (hasMeasurement(MeasurementStatus.OPEN) || hasMeasurement(MeasurementStatus.PAUSED))) {
                try {
                    refreshIdentifierOfCurrentlyCapturedMeasurement();
                } catch (final DataCapturingException e1) {
                    throw new IllegalStateException(e1);
                }
                Validate.isTrue(currentMeasurementIdentifier != null);
            }

            if (currentMeasurementIdentifier == null) {
                throw new NoSuchMeasurementException(
                        "Trying to load measurement identifier while no measurement was open or paused!");
            }

            return new Measurement(currentMeasurementIdentifier);
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
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GeoLocationsTable.URI_PATH).build();
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
        final ContentValues pointMetaDataValues = new ContentValues();
        pointMetaDataValues.put(COLUMN_ACCELERATIONS, pointMetaData.getAccelerationPointCounter());
        pointMetaDataValues.put(COLUMN_ROTATIONS, pointMetaData.getRotationPointCounter());
        pointMetaDataValues.put(COLUMN_DIRECTIONS, pointMetaData.getDirectionPointCounter());
        pointMetaDataValues.put(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                pointMetaData.getPersistenceFileFormatVersion());

        final int updatedRows = resolver.update(getMeasurementUri(), pointMetaDataValues,
                _ID + "=" + currentMeasurementIdentifier, null);
        Validate.isTrue(updatedRows == 1);
    }

    /**
     * Loads the {@code PointMetaData} for a specific measurement, e.g. to resume the capturing.
     *
     * @param measurementId The id of the measurement to load the {@code PointMetaData} for
     * @return the requested {@link PointMetaData}
     */
    public PointMetaData loadPointMetaData(final long measurementId) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(getMeasurementUri(),
                    new String[] {COLUMN_ACCELERATIONS, COLUMN_ROTATIONS, COLUMN_DIRECTIONS,
                            COLUMN_PERSISTENCE_FILE_FORMAT_VERSION},
                    _ID + "=?", new String[] {String.valueOf(measurementId)}, null);

            if (cursor == null) { // FIXME: in other cases we soft capture this and throw a DataCapturingException
                throw new IllegalStateException("Unable to access database to load measurements!");
            }
            Validate.isTrue(cursor.moveToNext(),
                    "Failed to load PointMetaData for non existent measurement" + measurementId);

            final int accelerations = cursor.getInt(cursor.getColumnIndex(COLUMN_ACCELERATIONS));
            final int rotations = cursor.getInt(cursor.getColumnIndex(COLUMN_ROTATIONS));
            final int directions = cursor.getInt(cursor.getColumnIndex(COLUMN_DIRECTIONS));
            final short persistenceFileFormatVersion = cursor
                    .getShort(cursor.getColumnIndex(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION));
            return new PointMetaData(accelerations, rotations, directions, persistenceFileFormatVersion);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Updates the {@link MeasurementStatus} in the data persistence layer.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param newStatus The new {@code MeasurementStatus}
     * @throws NoSuchMeasurementException if there was no measurement with the id {@param measurementIdentifier}.
     */
    private void setStatus(final long measurementIdentifier, final MeasurementStatus newStatus)
            throws NoSuchMeasurementException {
        final ContentValues statusValue = new ContentValues();
        statusValue.put(COLUMN_STATUS, newStatus.getDatabaseIdentifier());

        int updatedRows = resolver.update(getMeasurementUri(), statusValue, _ID + "=" + measurementIdentifier, null);
        Validate.isTrue(updatedRows < 2, "Duplicate measurement id entries.");
        if (updatedRows == 0) {
            throw new NoSuchMeasurementException("The measurement could not be updated as it does not exist.");
        }

        switch (newStatus) {
            case OPEN:
                Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED));
                break;
            case PAUSED:
                Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN));
                break;
            case FINISHED:
                Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN));
                Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED));
                break;
            case SYNCED:
                break;
            default:
                throw new IllegalArgumentException("Not supported");
        }

        Log.d(TAG, "Set measurement " + currentMeasurementIdentifier + " to " + newStatus);
    }
}
