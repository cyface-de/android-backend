package de.cyface.persistence;

import static android.provider.BaseColumns._ID;
import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.MeasurementTable.COLUMN_ACCELERATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_DIRECTIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_DISTANCE;
import static de.cyface.persistence.MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.MeasurementTable.COLUMN_ROTATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_STATUS;
import static de.cyface.persistence.MeasurementTable.COLUMN_VEHICLE;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
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
 * @version 10.0.1
 * @since 2.0.0
 */
public class PersistenceLayer<B extends PersistenceBehaviour> {

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
    private B persistenceBehaviour;
    /**
     * The {@link FileAccessLayer} used to interact with files.
     */
    private FileAccessLayer fileAccessLayer;

    /**
     * <b>This constructor is only for testing.</b>
     * <p>
     * It's required by the {@code DataCapturingLocalTest} to be able to {@link @Spy} on this object.
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
            @NonNull final String authority, @NonNull final B persistenceBehaviour) {
        this.context = context;
        this.resolver = resolver;
        this.authority = authority;
        this.persistenceBehaviour = persistenceBehaviour;
        this.fileAccessLayer = new DefaultFileAccess();
        final File accelerationsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionsFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        checkOrCreateFolder(accelerationsFolder);
        checkOrCreateFolder(rotationsFolder);
        checkOrCreateFolder(directionsFolder);
        persistenceBehaviour.onStart(this);
    }

    /**
     * Ensures that the specified exists.
     *
     * @param folder The {@link File} pointer to the folder which is created if it does not yet exist
     */
    private void checkOrCreateFolder(@NonNull final File folder) {
        if (!folder.exists()) {
            Validate.isTrue(folder.mkdir());
        }
    }

    /**
     * Creates a new, {@link MeasurementStatus#OPEN} {@link Measurement} for the provided {@link Vehicle}.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param vehicle The {@code Vehicle} to create a new {@code Measurement} for.
     * @return The newly created {@code Measurement}.
     */
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final ContentValues measurementValues = new ContentValues();
        measurementValues.put(COLUMN_VEHICLE, vehicle.getDatabaseIdentifier());
        measurementValues.put(COLUMN_STATUS, MeasurementStatus.OPEN.getDatabaseIdentifier());
        measurementValues.put(COLUMN_ACCELERATIONS, 0);
        measurementValues.put(COLUMN_ROTATIONS, 0);
        measurementValues.put(COLUMN_DIRECTIONS, 0);
        measurementValues.put(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION,
                MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION);
        measurementValues.put(COLUMN_DISTANCE, 0.0);

        // Synchronized to make sure there can't be two measurements with the same id
        synchronized (this) {
            Uri resultUri = resolver.insert(getMeasurementUri(), measurementValues);
            Validate.notNull("New measurement could not be created!", resultUri);
            Validate.notNull(resultUri.getLastPathSegment());

            final long measurementId = Long.valueOf(resultUri.getLastPathSegment());
            persistenceBehaviour.onNewMeasurement(measurementId);
            return new Measurement(measurementId, OPEN, vehicle, 0, 0, 0,
                    MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION, 0.0);
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
        Log.v(TAG, "Checking if app has an " + status + " measurement.");

        Cursor cursor = null;
        try {
            synchronized (this) {
                cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                        new String[] {status.getDatabaseIdentifier()}, null);
                Validate.softCatchNullCursor(cursor);

                final boolean hasMeasurement = cursor.getCount() > 0;
                Log.v(TAG, hasMeasurement ? "At least one measurement is " + status + "."
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
     * Returns all {@link Measurement}s, no matter the current {@link MeasurementStatus}.
     * If you only want measurements of a specific {@link MeasurementStatus} call
     * {@link #loadMeasurements(MeasurementStatus)} instead.
     *
     * @return A list containing all {@code Measurement}s currently stored on this device by this application. An empty
     *         list if there are no such measurements, but never <code>null</code>.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings({"unused"}) // Used by cyface flavour tests and possibly by implementing apps
    public @NonNull List<Measurement> loadMeasurements() throws CursorIsNullException {
        Cursor cursor = null;
        try {
            List<Measurement> ret = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, null, null, null);
            Validate.softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                final Measurement measurement = loadMeasurement(cursor);
                ret.add(measurement);
            }

            return ret;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Loads a {@link Measurement} objects from a {@link Cursor} which points to a {@code Measurement}.
     *
     * @param cursor a {@code Cursor} which points to a {@code Measurement}
     * @return the {@code Measurement} of the {@code Cursor}
     */
    private Measurement loadMeasurement(@NonNull final Cursor cursor) {
        final long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(_ID));
        final MeasurementStatus status = MeasurementStatus
                .valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_STATUS)));
        final Vehicle vehicle = Vehicle.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_VEHICLE)));
        final int accelerations = cursor.getInt(cursor.getColumnIndex(COLUMN_ACCELERATIONS));
        final int rotations = cursor.getInt(cursor.getColumnIndex(COLUMN_ROTATIONS));
        final int directions = cursor.getInt(cursor.getColumnIndex(COLUMN_DIRECTIONS));
        final short fileFormatVersion = cursor.getShort(cursor.getColumnIndex(COLUMN_PERSISTENCE_FILE_FORMAT_VERSION));
        final double distance = cursor.getDouble(cursor.getColumnIndex(COLUMN_DISTANCE));
        return new Measurement(measurementIdentifier, status, vehicle, accelerations, rotations, directions,
                fileFormatVersion, distance);
    }

    /**
     * Provide one specific {@link Measurement} from the data storage if it exists.
     *
     * Attention: At the loaded {@code Measurement} object and the persistent version of it in the
     * {@link PersistenceLayer} are not directly connected the loaded object is not notified when
     * the it's counterpart in the {@code PersistenceLayer} is changed (e.g. the {@link MeasurementStatus}).
     *
     * @param measurementIdentifier The device wide unique identifier of the {@code Measurement} to load.
     * @return The loaded {@code Measurement} if it exists; <code>null</code> otherwise.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (SR) use this to load single measurements
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
                return loadMeasurement(cursor);
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
     * Provide the {@link MeasurementStatus} of one specific {@link Measurement} from the data storage.
     * <p>
     * <b>ATTENTION:</b> Please be aware that the returned status is only valid at the time this
     * method is called. Changes of the {@code MeasurementStatus} in the persistence layer are not pushed
     * to the {@code MeasurementStatus} returned by this method.
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
     * Loads all {@link Measurement} which are in a specific {@link MeasurementStatus} from the data
     * storage.
     *
     * @param status the {@code MeasurementStatus} for which all {@code Measurement}s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state}. An empty list if there are no
     *         such measurements, but never <code>null</code>.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("unused") // Implementing apps (SR) use this api to load the finished measurements
    public List<Measurement> loadMeasurements(@NonNull final MeasurementStatus status) throws CursorIsNullException {
        Cursor cursor = null;

        try {
            final List<Measurement> measurements = new ArrayList<>();
            cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                    new String[] {status.getDatabaseIdentifier()}, null);
            Validate.softCatchNullCursor(cursor);

            while (cursor.moveToNext()) {
                final Measurement measurement = loadMeasurement(cursor);
                measurements.add(measurement);
            }

            return measurements;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Marks a {@link MeasurementStatus#FINISHED} {@link Measurement} as {@link MeasurementStatus#SYNCED} and deletes
     * the sensor data but does not update the {@link PointMetaData} in the {@link Measurement}!
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param measurement The {@link Measurement} to remove.
     * @throws NoSuchMeasurementException If the {@link Measurement} does not exist.
     */
    public void markAsSynchronized(final Measurement measurement)
            throws NoSuchMeasurementException, CursorIsNullException {

        // The status in the database could be different from the one in the object so load it again
        final long measurementId = measurement.getIdentifier();
        Validate.isTrue(loadMeasurementStatus(measurementId) == FINISHED);
        setStatus(measurementId, SYNCED);

        // TODO [CY-4359]: implement cyface variant where not only sensor data but also GeoLocations are deleted
        if (measurement.getAccelerations() > 0) {
            final File accelerationFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION).getFile();
            Validate.isTrue(accelerationFile.delete());
        }

        if (measurement.getRotations() > 0) {
            final File rotationFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION).getFile();
            Validate.isTrue(rotationFile.delete());
        }

        if (measurement.getDirections() > 0) {
            final File directionFile = Point3dFile.loadFile(context, fileAccessLayer, measurementId,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION).getFile();
            Validate.isTrue(directionFile.delete());
        }
    }

    /**
     * We want to make sure the device id is stored at the same location as the next measurement id counter.
     * This way we ensure ether both or none of both is reset upon re-installation or app reset.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK. Use
     * {@code DataCapturingService#getDeviceIdentifier()} instead.
     *
     * @return The device is as string
     */
    public final String restoreOrCreateDeviceId() throws CursorIsNullException {
        try {
            return loadDeviceId();
        } catch (final NoDeviceIdException e) {
            // Create a new device id
            final String deviceId = UUID.randomUUID().toString();
            final ContentValues identifierValues = new ContentValues();
            identifierValues.put(IdentifierTable.COLUMN_DEVICE_ID, deviceId);
            final Uri resultUri = resolver.insert(getIdentifierUri(), identifierValues);
            Validate.notNull("New device id could not be created!", resultUri);
            Log.d(TAG, "Created new device id " + deviceId);
            return deviceId;
        }
    }

    /**
     * Loads the device identifier from the persistence layer.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK. Use
     * {@code DataCapturingService#getDeviceIdentifier()} instead.
     *
     * @return The device is as string
     * @throws CursorIsNullException when accessing the {@link ContentProvider} failed
     * @throws NoDeviceIdException when there are no entries in the {@link IdentifierTable}
     */
    @NonNull
    public final String loadDeviceId() throws CursorIsNullException, NoDeviceIdException {
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
                    Log.v(TAG, "Providing device identifier " + did);
                    Validate.notNull(did);
                    return did;
                }

                throw new NoDeviceIdException("No entries in IdentifierTable.");
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
     * @param measurementIdentifier The id of the {@code Measurement} to remove.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (SR) use this to delete measurements
    public void delete(final long measurementIdentifier) {

        // Delete {@link Point3dFile}s if existent
        final File accelerationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = fileAccessLayer.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = fileAccessLayer.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        if (accelerationFolder.exists()) {
            final File accelerationFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            if (accelerationFile.exists()) {
                Validate.isTrue(accelerationFile.delete());
            }
        }
        if (rotationFolder.exists()) {
            final File rotationFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            if (rotationFile.exists()) {
                Validate.isTrue(rotationFile.delete());
            }
        }
        if (directionFolder.exists()) {
            final File directionFile = fileAccessLayer.getFilePath(context, measurementIdentifier,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
            if (directionFile.exists()) {
                Validate.isTrue(directionFile.delete());
            }
        }

        // Delete {@link GeoLocation}s and {@link Measurement} entry from database
        resolver.delete(getGeoLocationsUri(), GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                new String[] {Long.valueOf(measurementIdentifier).toString()});
        resolver.delete(getMeasurementUri(), _ID + "=?", new String[] {Long.valueOf(measurementIdentifier).toString()});
    }

    /**
     * Loads the track of {@link GeoLocation} objects for the provided {@link Measurement}. This method loads the
     * complete track into memory. For large tracks this could slow down the device or even reach the applications
     * memory limit.
     *
     * TODO [CY-4438]: From the current implementations (MeasurementContentProviderClient loader and resolver.query) is
     * the loader the faster solution. However, we should upgrade the database access as Android changed it's API.
     *
     * TODO [MOV-554]: provide a custom list implementation that loads only small portions into memory.
     *
     * @param measurementIdentifier The id of the {@code Measurement} to load the track for.
     * @return The track associated with the {@code Measurement} as a list of ordered (by timestamp)
     *         {@code GeoLocation}s.
     */
    @SuppressWarnings("unused") // Sdk implementing apps (RS) use this api to display the tracks
    public List<GeoLocation> loadTrack(final long measurementIdentifier) {

        Cursor cursor = null;
        try {
            cursor = resolver.query(getGeoLocationsUri(), null, GeoLocationsTable.COLUMN_MEASUREMENT_FK + "=?",
                    new String[] {Long.valueOf(measurementIdentifier).toString()},
                    GeoLocationsTable.COLUMN_GEOLOCATION_TIME + " ASC");
            if (cursor == null) {
                return Collections.emptyList();
            }

            final List<GeoLocation> geoLocations = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                final double lat = cursor.getDouble(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LAT));
                final double lon = cursor.getDouble(cursor.getColumnIndex(GeoLocationsTable.COLUMN_LON));
                final long timestamp = cursor.getLong(cursor.getColumnIndex(GeoLocationsTable.COLUMN_GEOLOCATION_TIME));
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
     * <p>
     * <b>ATTENTION:</b> This method is called automatically and should not be called from outside the SDK.
     */
    public void shutdown() {
        persistenceBehaviour.shutdown();
    }

    /**
     * @return The content provider {@link Uri} for the {@link MeasurementTable}.
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    @SuppressWarnings("WeakerAccess") // Because this is used to view measurements in an SDK implementing app
    public Uri getMeasurementUri() {
        return Utils.getMeasurementUri(authority);
    }

    /**
     * @return The content provider {@link Uri} for the {@link GeoLocationsTable}.
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    public Uri getGeoLocationsUri() {
        return Utils.getGeoLocationsUri(authority);
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     *         <p>
     *         <b>ATTENTION:</b> This method should not be needed from outside the SDK.
     */
    private Uri getIdentifierUri() {
        return Utils.getIdentifierUri(authority);
    }

    /**
     * When pausing or stopping a {@link Measurement} we store the {@link Point3d} counters and the
     * {@link MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION} in the {@link Measurement} to make sure we can
     * deserialize the {@link Point3dFile}s with deprecated {@code PERSISTENCE_FILE_FORMAT_VERSION}s. This also could
     * avoid corrupting {@code Point3dFile}s when the last bytes could not be written successfully.
     * <p>
     * <b>ATTENTION:</b> This method should not be called from outside the SDK.
     *
     * @param pointMetaData The {@code Point3dFile} meta information required for deserialization
     * @param measurementId The id of the measurement associated with the {@link PointMetaData}
     */
    public void storePointMetaData(@NonNull final PointMetaData pointMetaData, final long measurementId) {
        Log.d(TAG, "Storing point meta data.");

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
     * Loads the currently captured {@link Measurement} from the cache, if possible, or from the
     * {@link PersistenceLayer}.
     *
     * @throws NoSuchMeasurementException If this method has been called while no {@code Measurement} was active. To
     *             avoid this use {@link PersistenceLayer#hasMeasurement(MeasurementStatus)} to check whether there is
     *             an actual {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @return the currently captured {@link Measurement}
     */
    @SuppressWarnings("unused") // Implementing apps use this to get the ongoing measurement info
    public Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
        return persistenceBehaviour.loadCurrentlyCapturedMeasurement();
    }

    /**
     * Loads the currently captured {@link Measurement} explicitly from the {@link PersistenceLayer}.
     * <p>
     * <b>ATTENTION:</b> SDK implementing apps should use {@link #loadCurrentlyCapturedMeasurement()} instead.
     *
     * @throws NoSuchMeasurementException If this method has been called while no {@code Measurement} was active. To
     *             avoid this use {@link PersistenceLayer#hasMeasurement(MeasurementStatus)} to check whether there is
     *             an actual {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @return the currently captured {@link Measurement}
     */
    public Measurement loadCurrentlyCapturedMeasurementFromPersistence()
            throws NoSuchMeasurementException, CursorIsNullException {
        Log.v(Constants.TAG, "Trying to load currently captured measurement from PersistenceLayer!");

        final List<Measurement> openMeasurements = loadMeasurements(MeasurementStatus.OPEN);
        final List<Measurement> pausedMeasurements = loadMeasurements(MeasurementStatus.PAUSED);
        if (openMeasurements.size() == 0 && pausedMeasurements.size() == 0) {
            throw new NoSuchMeasurementException("No currently captured measurement found!");
        }
        if (openMeasurements.size() + pausedMeasurements.size() > 1) {
            throw new IllegalStateException("More than one currently captured measurement found!");
        }

        return (openMeasurements.size() == 1 ? openMeasurements : pausedMeasurements).get(0);
    }

    /**
     * Updates the {@link MeasurementStatus} in the data persistence layer.
     * <p>
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param newStatus The new {@code MeasurementStatus}
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public void setStatus(final long measurementIdentifier, final MeasurementStatus newStatus)
            throws NoSuchMeasurementException, CursorIsNullException {

        final ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, newStatus.getDatabaseIdentifier());
        updateMeasurement(measurementIdentifier, values);

        // Make sure the database state **after** the status update is still valid
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

        Log.d(TAG, "Set measurement " + measurementIdentifier + " to " + newStatus);
    }

    /**
     * Updates the {@code Measurement#distance} entry of the currently captured {@link Measurement}.
     * <p>
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param newDistance The new {@code Measurement#distance} to be stored.
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     */
    public void setDistance(final long measurementIdentifier, final double newDistance)
            throws NoSuchMeasurementException {
        final ContentValues values = new ContentValues();
        values.put(COLUMN_DISTANCE, newDistance);
        updateMeasurement(measurementIdentifier, values);
    }

    /**
     * Updates the {@code Measurement#distance} entry of the currently captured {@link Measurement}.
     *
     * @param measurementIdentifier The id of the {@link Measurement} to be updated
     * @param values The new {@link ContentValues} to be stored.
     * @throws NoSuchMeasurementException if there was no {@code Measurement} with the id
     *             {@param measurementIdentifier}.
     */
    private void updateMeasurement(final long measurementIdentifier, @NonNull final ContentValues values)
            throws NoSuchMeasurementException {

        final int updatedRows = resolver.update(getMeasurementUri(), values, _ID + "=" + measurementIdentifier, null);
        Validate.isTrue(updatedRows < 2, "Duplicate measurement id entries.");
        if (updatedRows == 0) {
            throw new NoSuchMeasurementException("The measurement could not be updated as it does not exist.");
        }
    }

    /**
     * @return the {@link #context}
     */
    public Context getContext() {
        return context;
    }

    /**
     * @return the {@link #resolver}
     */
    public ContentResolver getResolver() {
        return resolver;
    }

    /**
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @return the {@link #fileAccessLayer}
     */
    public FileAccessLayer getFileAccessLayer() {
        return fileAccessLayer;
    }

    /**
     * <b>ATTENTION:</b> This should not be used by SDK implementing apps.
     *
     * @return the {@link #persistenceBehaviour}
     */
    public B getPersistenceBehaviour() {
        return persistenceBehaviour;
    }
}
