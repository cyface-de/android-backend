package de.cyface.persistence;

import static android.provider.BaseColumns._ID;
import static de.cyface.persistence.MeasurementTable.COLUMN_ACCELERATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_DIRECTIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.MeasurementTable.COLUMN_ROTATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_STATUS;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the {@code DataCapturingListener} and its delegate
 * objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 8.0.0
 * @since 2.0.0
 */
public class PersistenceLayer {

    /**
     * The {@link Context} required to locate the app's internal storage directory.
     */
    private final Context context;
    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;
    /**
     * The authority used to identify the Android content provider to persist data to or load it from.
     */
    private final String authority;
    /**
     * The {@link PersistenceBehaviour} defines how the {@code Persistence} layer is works. We need this behaviour to
     * differentiate if the {@link PersistenceLayer} is used for live capturing and or to load existing data.
     */
    private PersistenceBehaviour persistenceBehaviour;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private FileAccessLayer fileAccessLayer;

    /**
     * This constructor is only for testing. It's required by the {@code DataCapturingLocalTest} to be able to
     * {@link @Spy} on this object.
     */
    public PersistenceLayer() {
        this.context = null;
        this.resolver = null;
        this.authority = null;
        this.fileAccessLayer = new DefaultFileAccess();
    }

    /**
     * Creates a new completely initialized <code>PersistenceLayer</code>.
     *
     * @param context The {@link Context} required to locate the app's internal storage directory.
     * @param resolver {@link ContentResolver} that provides access to the {@link MeasuringPointsContentProvider}. This
     *            is required as an explicit parameter to allow test to inject a mocked {@code ContentResolver}.
     * @param authority The authority used to identify the Android content provider.
     * @param persistenceBehaviour A {@link PersistenceBehaviour} which tells if this {@link PersistenceLayer} is used
     *            to capture live data.
     */
    public PersistenceLayer(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final PersistenceBehaviour persistenceBehaviour) {
        this.context = context;
        this.resolver = resolver;
        this.authority = authority;
        this.persistenceBehaviour = persistenceBehaviour;
        this.fileAccessLayer = new DefaultFileAccess();
        persistenceBehaviour.onStart(this);
    }

    /**
     * Creates a new, {@link MeasurementStatus#OPEN} {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The {@code Vehicle} to create a new {@code Measurement} for.
     * @return The newly created {@code Measurement}.
     */
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final ContentValues values = new ContentValues();
        values.put(MeasurementTable.COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        values.put(COLUMN_STATUS, MeasurementStatus.OPEN.getDatabaseIdentifier());

        // Synchronized to make sure there can't be two measurements with the same id
        synchronized (this) {
            Uri resultUri = resolver.insert(getMeasurementUri(), values);
            Validate.notNull("New measurement could not be created!", resultUri);
            Validate.notNull(resultUri.getLastPathSegment());

            final long measurementId = Long.valueOf(resultUri.getLastPathSegment());
            persistenceBehaviour.onNewMeasurement(measurementId);
            return new Measurement(measurementId);
        }
    }

    /**
     * Provides information about whether there is currently a {@link Measurement} in the specified
     * {@link MeasurementStatus}.
     *
     * @param status The {@code MeasurementStatus} in question
     * @return <code>true</code> if a {@code Measurement} of the {@param status} exists.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public boolean hasMeasurement(@NonNull MeasurementStatus status) throws CursorIsNullException {
        Log.d(Constants.TAG, "Checking if app has an " + status + " measurement.");

        Cursor cursor = null;
        try {
            synchronized (this) {
                cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                        new String[] {status.getDatabaseIdentifier()}, null);
                Validate.softCatchNullCursor(cursor);

                final boolean hasMeasurement = cursor.getCount() > 0;
                Log.d(Constants.TAG, hasMeasurement ? "At least one measurement is " + status + "."
                        : "No measurement is " + status + ".");
                return hasMeasurement;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Returns all {@link Measurement}s. If you only want measurements of a specific {@link MeasurementStatus} call
     * {@link #loadMeasurements(MeasurementStatus)} instead.
     *
     * @return All {@code Measurement}s currently in the local persistent data storage.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public @NonNull List<Measurement> loadMeasurements() throws CursorIsNullException {
        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, null, null, null);
            Validate.softCatchNullCursor(cursor);

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
     * Provide one specific {@link Measurement} from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the {@code Measurement} to load.
     * @return The loaded {@code Measurement} if it exists; <code>null</code> otherwise.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public Measurement loadMeasurement(final long measurementIdentifier) throws CursorIsNullException {
        final Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier))
                .build();
        Cursor cursor = null;

        try {
            cursor = resolver.query(measurementUri, null, _ID + "=?",
                    new String[] {String.valueOf(measurementIdentifier)}, null);
            Validate.softCatchNullCursor(cursor);
            if (cursor.getCount() > 1) {
                throw new IllegalStateException("Too many measurements loaded from URI: " + measurementUri);
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
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public MeasurementStatus loadMeasurementStatus(final long measurementIdentifier)
            throws NoSuchMeasurementException, CursorIsNullException {
        Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier)).build();
        Cursor cursor = null;

        try {
            cursor = resolver.query(measurementUri, null, null, null, null);
            Validate.softCatchNullCursor(cursor);
            if (cursor.getCount() > 1) {
                throw new IllegalStateException("Too many measurements loaded from URI: " + measurementUri);
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
     * Loads all {@link Measurement} which are in a specific {@link MeasurementStatus} from the local persistent data
     * storage.
     *
     * @param status the {@code MeasurementStatus} for which all {@code Measurement}s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state}
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public List<Measurement> loadMeasurements(@NonNull final MeasurementStatus status) throws CursorIsNullException {
        Cursor cursor = null;

        try {
            final List<Measurement> measurements = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                    new String[] {status.getDatabaseIdentifier()}, null);
            Validate.softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(_ID));
                measurements.add(new Measurement(measurementIdentifier));
            }

            return measurements;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Marks a {@link MeasurementStatus#FINISHED} {@link Measurement} as
     * {@link MeasurementStatus#SYNCED} and deletes the sensor data.
     *
     * @param measurement The measurement to remove.
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     */
    public void markAsSynchronized(@NonNull final Measurement measurement)
            throws NoSuchMeasurementException, CursorIsNullException {
        Validate.isTrue(loadMeasurementStatus(measurement.getIdentifier()) == FINISHED);
        setStatus(measurement.getIdentifier(), SYNCED);

        // FIXME: for movebis we only delete sensor data not GPS points (+move to synchronized)
        // how do we want to handle this on Cyface ?
        final PointMetaData pointMetaData = loadPointMetaData(measurement.getIdentifier());

        if (pointMetaData.getAccelerationPointCounter() > 0) {
            final File accelerationFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION).getFile();
            Validate.isTrue(accelerationFile.delete());
        }

        if (pointMetaData.getRotationPointCounter() > 0) {
            final File rotationFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION).getFile();
            Validate.isTrue(rotationFile.delete());
        }

        if (pointMetaData.getDirectionPointCounter() > 0) {
            final File directionFile = Point3dFile.loadFile(context, fileAccessLayer, measurement.getIdentifier(),
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION).getFile();
            Validate.isTrue(directionFile.delete());
        }
    }

    /**
     * We want to make sure the device id is stored at the same location as the next measurement id counter.
     * This way we ensure ether both or none of both is reset upon re-installation or app reset.
     *
     * @return The device is as string
     */
    public final String restoreOrCreateDeviceId() throws CursorIsNullException {
        Log.d(Constants.TAG, "Trying to load device identifier from content provider!");
        Cursor deviceIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                // Try to get device id from database
                deviceIdentifierQueryCursor = resolver.query(getIdentifierUri(),
                        new String[] {IdentifierTable.COLUMN_DEVICE_ID}, null, null, null);
                Validate.softCatchNullCursor(deviceIdentifierQueryCursor);
                if (deviceIdentifierQueryCursor.getCount() > 1) {
                    throw new IllegalStateException("More entries than expected");
                }
                if (deviceIdentifierQueryCursor.moveToFirst()) {
                    final int indexOfMeasurementIdentifierColumn = deviceIdentifierQueryCursor
                            .getColumnIndex(IdentifierTable.COLUMN_DEVICE_ID);
                    final String did = deviceIdentifierQueryCursor.getString(indexOfMeasurementIdentifierColumn);
                    Log.d(Constants.TAG, "Providing device identifier " + did);
                    return did;
                }

                // Update measurement id counter
                final String deviceId = UUID.randomUUID().toString();
                final ContentValues values = new ContentValues();
                values.put(IdentifierTable.COLUMN_DEVICE_ID, deviceId);
                final Uri resultUri = resolver.insert(getIdentifierUri(), values);
                Validate.notNull("New device id could not be created!", resultUri);
                Log.d(Constants.TAG, "Created new device id " + deviceId);
                return deviceId;
            }
        } finally {
            // This can be null, see documentation
            if (deviceIdentifierQueryCursor != null) {
                deviceIdentifierQueryCursor.close();
            }
        }
    }

    /**
     * Removes one {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The {@code Measurement} to remove.
     * @throws NoSuchMeasurementException If the provided measurement was <code>null</code>.
     */
    public void delete(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        if (measurement == null) { // FIXME: do we need to support this for RM? if so, annotate this here with
                                   // //noinspection
            throw new NoSuchMeasurementException("Unable to delete null measurement!");
        }

        // Delete {@link Point3dFile}s if existent
        final File accelerationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        if (accelerationFolder.exists()) {
            final File accelerationFile = fileAccessLayer.getFilePath(context, measurement.getIdentifier(),
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            if (accelerationFile.exists()) {
                Validate.isTrue(accelerationFile.delete());
            }
        }
        if (rotationFolder.exists()) {
            final File rotationFile = fileAccessLayer.getFilePath(context, measurement.getIdentifier(),
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            if (rotationFile.exists()) {
                Validate.isTrue(rotationFile.delete());
            }
        }
        if (directionFolder.exists()) {
            final File directionFile = fileAccessLayer.getFilePath(context, measurement.getIdentifier(),
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
            if (directionFile.exists()) {
                Validate.isTrue(directionFile.delete());
            }
        }

        // Delete {@link GeoLocation}s and {@link Measurement} entry from database
        resolver.delete(getGeoLocationsUri(), GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurement.getIdentifier()).toString()});
        resolver.delete(getMeasurementUri(), _ID + "=?",
                new String[] {Long.valueOf(measurement.getIdentifier()).toString()});
    }

    /**
     * Loads the track of {@link GeoLocation} objects for the provided {@link Measurement}.
     *
     * TODO [#CY-4438]: From the current implementations (MeasurementContentProviderClient loader and resolver.query) is
     * the loader the faster solution. However, we should upgrade the database access as Android changed it's API.
     *
     * @param measurement The {@code Measurement} to load the track for.
     * @return The loaded track of <code>GeoLocation</code> objects ordered by time ascending or an empty list if
     *         accessing the content provider fails which should hardly ever happen.
     * @throws NoSuchMeasurementException If the provided {@param Measurement} was <code>null</code>.
     */
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        if (measurement == null) {// FIXME: do we need to support this for RM? if so, annotate this here with
            // //noinspection
            throw new NoSuchMeasurementException("Unable to load track for null measurement!");
        }

        Cursor cursor = null;
        try {
            cursor = resolver.query(getGeoLocationsUri(), null, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurement.getIdentifier()).toString()},
                    GeoLocationsTable.COLUMN_GPS_TIME + " ASC");
            if (cursor == null) {
                return Collections.emptyList();
            }

            final List<GeoLocation> geoLocations = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                final double lat = cursor.getDouble(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT));
                final double lon = cursor.getDouble(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LON));
                final long timestamp = cursor.getLong(cursor.getColumnIndex(GeoLocationsTable.COLUMN_GPS_TIME));
                final double speed = cursor.getDouble(cursor.getColumnIndex(GeoLocationsTable.COLUMN_SPEED));
                final float accuracy = cursor.getFloat(cursor.getColumnIndex(GeoLocationsTable.COLUMN_ACCURACY));
                geoLocations.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
            }

            return geoLocations;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     */
    public void shutdown() {
        persistenceBehaviour.shutdown();
    }

    /**
     * @return The content provider {@link Uri} for the {@link MeasurementTable}.
     */
    private Uri getMeasurementUri() {
        return Utils.getMeasurementUri(authority);
    }

    /**
     * @return The content provider {@link Uri} for the {@link GeoLocationsTable}.
     */
    public Uri getGeoLocationsUri() {
        return Utils.getGeoLocationsUri(authority);
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     */
    private Uri getIdentifierUri() {
        return Utils.getIdentifierUri(authority);
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

        final int updatedRows = resolver.update(getMeasurementUri(), pointMetaDataValues, _ID + "=" + measurementId,
                null);
        Validate.isTrue(updatedRows == 1);
    }

    /**
     * Loads the {@code PointMetaData} for a specific measurement, e.g. to resume the capturing.
     *
     * @param measurementId The id of the measurement to load the {@code PointMetaData} for
     * @return the requested {@link PointMetaData}
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public PointMetaData loadPointMetaData(final long measurementId) throws CursorIsNullException {
        Cursor cursor = null;
        try {
            cursor = resolver.query(getMeasurementUri(),
                    new String[] {COLUMN_ACCELERATIONS, COLUMN_ROTATIONS, COLUMN_DIRECTIONS,
                            COLUMN_PERSISTENCE_FILE_FORMAT_VERSION},
                    _ID + "=?", new String[] {String.valueOf(measurementId)}, null);
            Validate.softCatchNullCursor(cursor);
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
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public void setStatus(final long measurementIdentifier, final MeasurementStatus newStatus)
            throws NoSuchMeasurementException, CursorIsNullException {
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

        Log.d(Constants.TAG, "Set measurement " + measurementIdentifier + " to " + newStatus);
    }

    public Context getContext() {
        return context;
    }

    public ContentResolver getResolver() {
        return resolver;
    }

    public PersistenceBehaviour getPersistenceBehaviour() {
        return persistenceBehaviour;
    }
}
