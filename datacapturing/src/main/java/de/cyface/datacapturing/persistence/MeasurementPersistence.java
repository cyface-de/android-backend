package de.cyface.datacapturing.persistence;

import static android.provider.BaseColumns._ID;
import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.persistence.MeasurementTable.COLUMN_ACCELERATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_DIRECTIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_PERSISTENCE_FILE_FORMAT_VERSION;
import static de.cyface.persistence.MeasurementTable.COLUMN_ROTATIONS;
import static de.cyface.persistence.MeasurementTable.COLUMN_STATUS;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
import de.cyface.datacapturing.DataCapturingListener;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.FileUtils;
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
import de.cyface.utils.DataCapturingException;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the {@link DataCapturingListener} and its delegate
 * objects.
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
     * @param resolver {@link ContentResolver} that provides access to the {@link MeasuringPointsContentProvider}. This
     *            is required as an explicit parameter to allow test to inject a mocked {@code ContentResolver}.
     * @param authority The authority used to identify the Android content provider.
     */
    public MeasurementPersistence(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority) {
        this.context = context;
        this.resolver = resolver;
        this.threadPool = Executors.newCachedThreadPool();
        this.authority = authority;
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

            currentMeasurementIdentifier = Long.valueOf(resultUri.getLastPathSegment());
            return new Measurement(currentMeasurementIdentifier);
        }
    }

    /**
     * Update the {@link MeasurementStatus} of the currently active {@link Measurement}.
     * (!) Attention: this method does not check the previous state and if the life-cycle change is valid. //TODO: ok?
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     * @throws IllegalArgumentException When the {@param newStatus} was none of the supported:
     *             {@link MeasurementStatus#FINISHED}, {@link MeasurementStatus#PAUSED} or
     *             {@link MeasurementStatus#OPEN}.
     */
    public void updateRecentMeasurement(@NonNull final MeasurementStatus newStatus) throws NoSuchMeasurementException {
        Validate.isTrue(newStatus == MeasurementStatus.FINISHED || newStatus == MeasurementStatus.PAUSED
                || newStatus == MeasurementStatus.OPEN);

        final long currentlyCapturedMeasurementId = loadCurrentlyCapturedMeasurement().getIdentifier();
        switch (newStatus) {
            case OPEN:
                Validate.isTrue(loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.PAUSED);
                break;
            case PAUSED:
                Validate.isTrue(loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.OPEN);
                break;
            case FINISHED:
                Validate.isTrue(loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.OPEN
                        || loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.PAUSED);
                break;
            default:
                throw new IllegalArgumentException("No supported newState: " + newStatus);
        }

        Log.d(TAG, "Updating recent measurement to: " + newStatus);
        synchronized (this) {
            try {
                setStatus(currentlyCapturedMeasurementId, newStatus);
            } finally {
                if (newStatus == MeasurementStatus.FINISHED) {
                    currentMeasurementIdentifier = null;
                }
            }
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

        final CapturedDataWriter writer = new CapturedDataWriter(data, accelerationsFile, rotationsFile, directionsFile,
                callback);

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
     * Provides information about whether there is currently a {@link Measurement} in the specified
     * {@link MeasurementStatus}.
     *
     * @param status The {@code MeasurementStatus} in question
     * @return <code>true</code> if a {@code Measurement} of the {@param status} exists.
     * @throws DataCapturingException If content provider was inaccessible.
     */
    public boolean hasMeasurement(@NonNull MeasurementStatus status) throws DataCapturingException {
        Log.d(TAG, "Checking if app has an " + status + " measurement.");

        Cursor cursor = null;
        try {
            synchronized (this) {
                cursor = resolver.query(getMeasurementUri(), null, COLUMN_STATUS + "=?",
                        new String[] {status.getDatabaseIdentifier()}, null);
                Validate.softCatchNullCursor(cursor);

                final boolean hasMeasurement = cursor.getCount() > 0;
                Log.d(TAG, hasMeasurement ? "At least one measurement is " + status + "."
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
     * Loads the currently captured measurement and refreshes the {@link #currentMeasurementIdentifier} reference. This
     * method should only be called if capturing is active. It throws an error otherwise.
     *
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use {@link #hasMeasurement(MeasurementStatus)} to check whether there is an actual
     *             {@link MeasurementStatus#OPEN} measurement.
     */
    private void refreshIdentifierOfCurrentlyCapturedMeasurement() throws NoSuchMeasurementException {
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
     * Returns all {@link Measurement}s. If you only want measurements of a specific {@link MeasurementStatus} call
     * {@link #loadMeasurements(MeasurementStatus)} instead.
     *
     * @return All {@code Measurement}s currently in the local persistent data storage.
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public @NonNull List<Measurement> loadMeasurements() throws DataCapturingException {
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
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public Measurement loadMeasurement(final long measurementIdentifier) throws DataCapturingException {
        final Uri measurementUri = getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier))
                .build();
        Cursor cursor = null;

        try {
            cursor = resolver.query(measurementUri, null, _ID + "=?",
                    new String[] {String.valueOf(measurementIdentifier)}, null);
            Validate.softCatchNullCursor(cursor);
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
            Validate.softCatchNullCursor(cursor);
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
     * Loads all {@link Measurement} which are in a specific {@link MeasurementStatus} from the local persistent data
     * storage.
     *
     * @param status the {@code MeasurementStatus} for which all {@code Measurement}s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state}
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public List<Measurement> loadMeasurements(@NonNull final MeasurementStatus status) throws DataCapturingException {
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
     * Loads a measurement with the provided id from the persistence layer as uncompressed and serialized in
     * the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be compressed.
     *
     * TODO: test this on weak devices for large measurements
     *
     * @param measurement The identifier of the measurement to load.
     * @return The bytes of the measurement in the <code>TRANSFER_FILE_FORMAT_VERSION</code> format.
     * @throws FileCorruptedException If the persisted measurement if broken or in a false format.
     * @throws IOException If the byte array could not be assembled.
     */
    public byte[] loadSerialized(final Measurement measurement) throws FileCorruptedException, IOException {
        final byte[] transferFileFormat = new byte[2];
        transferFileFormat[0] = (byte)(TRANSFER_FILE_FORMAT_VERSION >> 8);
        transferFileFormat[1] = (byte)TRANSFER_FILE_FORMAT_VERSION;
        final MetaFile.MetaData metaData = measurement.getMetaFile().deserialize();
        final byte[] pointCounts = serialize(metaData.getPointMetaData());
        final byte[] geoLocationData = metaData.getPointMetaData().getCountOfGeoLocations() > 0
                ? FileUtils.loadBytes(GeoLocationsFile.loadFile(measurement).getFile())
                : new byte[] {};
        final byte[] accelerationData = metaData.getPointMetaData().getCountOfAccelerations() > 0
                ? FileUtils.loadBytes(Point3dFile.loadFile(measurement, FileUtils.ACCELERATIONS_FILE_NAME,
                        FileUtils.ACCELERATIONS_FILE_EXTENSION).getFile())
                : new byte[] {};
        final byte[] rotationData = metaData.getPointMetaData().getCountOfRotations() > 0 ? FileUtils.loadBytes(
                Point3dFile.loadFile(measurement, FileUtils.ROTATIONS_FILE_NAME, FileUtils.ROTATION_FILE_EXTENSION)
                        .getFile())
                : new byte[] {};
        final byte[] directionData = metaData.getPointMetaData().getCountOfDirections() > 0 ? FileUtils.loadBytes(
                Point3dFile.loadFile(measurement, FileUtils.DIRECTION_FILE_NAME, FileUtils.DIRECTION_FILE_EXTENSION)
                        .getFile())
                : new byte[] {};

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(transferFileFormat);
        outputStream.write(pointCounts);
        outputStream.write(geoLocationData);
        outputStream.write(accelerationData);
        outputStream.write(rotationData);
        outputStream.write(directionData);
        return outputStream.toByteArray();
    }

    /**
     * Loads a measurement with the provided id from the persistence layer as compressed and serialized in
     * the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be transferred.
     * As compression the standard Android GZIP compression is used
     *
     * @param measurement The identifier of the measurement to load.
     * @return The bytes of the measurement in the <code>TRANSFER_FILE_FORMAT_VERSION</code> format.
     * @throws FileCorruptedException If the persisted measurement if broken or in a false format.
     * @throws IOException If the byte array could not be assembled.
     */
    public InputStream loadSerializedCompressed(final Measurement measurement)
            throws FileCorruptedException, IOException {
        final byte[] data = loadSerialized(measurement);

        final Deflater compressor = new Deflater();
        compressor.setInput(data);
        compressor.finish();

        final byte[] output = new byte[data.length];
        final int lengthOfCompressedData = compressor.deflate(output);
        Log.d(TAG, String.format("Compressed data to %d bytes.", lengthOfCompressedData));
        return new ByteArrayInputStream(output, 0, lengthOfCompressedData);
    }

    /**
     * Marks a {@link Measurement.MeasurementStatus#FINISHED} {@link Measurement} as
     * {@link Measurement.MeasurementStatus#SYNCED} and deletes the sensor data.
     *
     * @param measurement The measurement to remove.
     */
    public void markAsSynchronized(final @NonNull Measurement measurement) {
        Validate.isTrue(measurement.getStatus() == FINISHED);
        measurement.setStatus(Measurement.MeasurementStatus.SYNCED);

        // FIXME: for movebis we only delete sensor data not GPS points (+move to synchronized)
        // how do we want to handle this on Cyface ?
        final MetaFile.MetaData metaData;
        try {
            metaData = measurement.getMetaFile().deserialize();
        } catch (final FileCorruptedException e) {
            throw new IllegalStateException(e);
        }

        if (metaData.getPointMetaData().getCountOfAccelerations() > 0) {
            final File accelerationFile = Point3dFile
                    .loadFile(measurement, FileUtils.ACCELERATIONS_FILE_NAME, FileUtils.ACCELERATIONS_FILE_EXTENSION)
                    .getFile();
            Validate.isTrue(accelerationFile.delete());
        }

        if (metaData.getPointMetaData().getCountOfRotations() > 0) {
            final File rotationFile = Point3dFile
                    .loadFile(measurement, FileUtils.ROTATIONS_FILE_NAME, FileUtils.ROTATION_FILE_EXTENSION).getFile();
            Validate.isTrue(rotationFile.delete());
        }

        if (metaData.getPointMetaData().getCountOfDirections() > 0) {
            final File directionFile = Point3dFile
                    .loadFile(measurement, FileUtils.DIRECTION_FILE_NAME, FileUtils.DIRECTION_FILE_EXTENSION).getFile();
            Validate.isTrue(directionFile.delete());
        }
    }

    /**
     * We want to make sure the device id is stored at the same location as the next measurement id counter.
     * This way we ensure ether both or none of both is reset upon re-installation or app reset.
     *
     * @return The device is as string
     */
    public final String restoreOrCreateDeviceId() throws DataCapturingException {
        Log.d(Constants.TAG, "Trying to load device identifier from content provider!");
        Cursor deviceIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                // Try to get device id from database
                deviceIdentifierQueryCursor = resolver.query(getIdentifierUri(authority),
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
                values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, 1);
                final Uri resultUri = resolver.insert(getIdentifierUri(authority), values);
                Validate.notNull("New device id and measurement id counter could not be created!", resultUri);
                Log.d(Constants.TAG, "Created new device id " + deviceId + " and reset measurement id counter");
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
     * Removes everything from the local persistent data storage.
     * (!) Attention: This currently does not reset the device id counter. // FIXME: should this be done? does it work?
     *
     * @return number of rows removed from the database which includes {@link Measurement}s and {@link GeoLocation}s.
     *         This does not include the number of {@link Point3dFile}s removed.
     */
    public int clear() {

        // Remove {@code Point3dFile}s
        final File accelerationFolder = FileUtils.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = FileUtils.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = FileUtils.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        final List<File> accelerationFiles = new ArrayList<>(Arrays.asList(accelerationFolder.listFiles()));
        final List<File> rotationFiles = new ArrayList<>(Arrays.asList(rotationFolder.listFiles()));
        final List<File> directionFiles = new ArrayList<>(Arrays.asList(directionFolder.listFiles()));
        for (File file : accelerationFiles) {
            Validate.isTrue(file.delete());
        }
        for (File file : rotationFiles) {
            Validate.isTrue(file.delete());
        }
        for (File file : directionFiles) {
            Validate.isTrue(file.delete());
        }
        Validate.isTrue(accelerationFolder.delete());
        Validate.isTrue(rotationFolder.delete());
        Validate.isTrue(directionFolder.delete());

        // Remove database entries
        int removedDatabaseRows = 0;
        removedDatabaseRows += resolver.delete(getGeoLocationsUri(), null, null);
        removedDatabaseRows += resolver.delete(getMeasurementUri(), null, null);
        return removedDatabaseRows;
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
        final File accelerationFolder = FileUtils.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotationFolder = FileUtils.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directionFolder = FileUtils.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        if (accelerationFolder.exists()) {
            final File accelerationFile = FileUtils.getFilePath(context, measurement.getIdentifier(),
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
            if (accelerationFile.exists()) {
                Validate.isTrue(accelerationFile.delete());
            }
        }
        if (rotationFolder.exists()) {
            final File rotationFile = FileUtils.getFilePath(context, measurement.getIdentifier(),
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
            if (rotationFile.exists()) {
                Validate.isTrue(rotationFile.delete());
            }
        }
        if (directionFolder.exists()) {
            final File directionFile = FileUtils.getFilePath(context, measurement.getIdentifier(),
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
     * @param measurement The {@code Measurement} to load the track for.
     * @return The loaded track of <code>GeoLocation</code> objects ordered by time ascending or an empty list if
     *         accessing the content provider fails which should hardly ever happen.
     *
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
                refreshIdentifierOfCurrentlyCapturedMeasurement();
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
     * @return The content provider {@link Uri} for the {@link MeasurementTable}.
     */
    private Uri getMeasurementUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MeasurementTable.URI_PATH).build();
    }

    /**
     * @return The content provider {@link Uri} for the {@link GeoLocationsTable}.
     */
    private Uri getGeoLocationsUri() {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GeoLocationsTable.URI_PATH).build();
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     */
    private static Uri getIdentifierUri(final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
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
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public PointMetaData loadPointMetaData(final long measurementId) throws DataCapturingException {
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
     * @throws DataCapturingException If accessing the content provider fails.
     */
    private void setStatus(final long measurementIdentifier, final MeasurementStatus newStatus)
            throws NoSuchMeasurementException, DataCapturingException {
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
