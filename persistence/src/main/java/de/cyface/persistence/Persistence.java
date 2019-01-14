package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.FileUtils.getMeasurementsFolder;
import static de.cyface.persistence.model.Measurement.MeasurementStatus.CORRUPTED;
import static de.cyface.persistence.model.Measurement.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.Measurement.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.Measurement.MeasurementStatus.PAUSED;
import static de.cyface.persistence.model.Measurement.MeasurementStatus.SYNCED;
import static de.cyface.persistence.serialization.MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION;
import static de.cyface.persistence.serialization.MeasurementSerializer.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.RotationsFile;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>DataCapturingService</code>,
 * <code>SyncAdapter</code> and its delegate objects.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.0.0
 */
public class Persistence {

    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;
    /**
     * The authority used to identify the Android content provider to persist data to or load it from.
     */
    private final String authority;
    /**
     * The {@link Context} required to access the persistence layer.
     */
    protected final Context context;

    /**
     * Creates a new completely initialized <code>Persistence</code>.
     *
     * @param context The {@link Context} required to locate the app's internal storage directory.
     * @param authority The authority used to load the identify the Android content provider to load the identifiers.
     */
    public Persistence(@NonNull final Context context, final @NonNull String authority) {
        this.resolver = context.getContentResolver();
        this.authority = authority;
        this.context = context;
        final File openMeasurementsDir = getMeasurementsFolder(context, OPEN);
        final File finishedMeasurementsDir = getMeasurementsFolder(context, FINISHED);
        final File corruptedMeasurementsDir = getMeasurementsFolder(context, Measurement.MeasurementStatus.CORRUPTED);
        final File synchronizedMeasurementsDir = getMeasurementsFolder(context, Measurement.MeasurementStatus.SYNCED);
        // FIXME: add paused folder

        // Ensure measurements dir exist
        if (!openMeasurementsDir.exists()) {
            Validate.isTrue(openMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!synchronizedMeasurementsDir.exists()) {
            Validate.isTrue(synchronizedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!finishedMeasurementsDir.exists()) {
            Validate.isTrue(finishedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!corruptedMeasurementsDir.exists()) {
            Validate.isTrue(corruptedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        // FIXME: add paused folder
    }

    /**
     * Creates a new, {@link Measurement.MeasurementStatus#OPEN}, {@link Measurement}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The newly created <code>Measurement</code>.
     */
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final long measurementId;
        // Synchronized to make sure there can't be two measurements with the same id
        synchronized (this) {
            Log.d(TAG, "Trying to load measurement identifier from content provider!");
            measurementId = loadNextMeasurementId();

            // Update measurement id counter - must be synchronized
            final ContentValues values = new ContentValues();
            values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, measurementId + 1);
            final int updatedRows = resolver.update(getIdentifierUri(authority), values, null, null);
            Validate.isTrue(updatedRows == 1);
            Log.d(TAG, "Incremented mid counter to " + (measurementId + 1));
        }

        final Measurement measurement = new Measurement(context, measurementId, OPEN);
        measurement.createDirectory();
        measurement.createMetaFile(vehicle);
        // FIXME: update other new metafile code section, too
        return measurement;
    }

    /**
     * Loads the next measurement id available for a new measurement.
     *
     * @return the next available measurement id
     */
    private long loadNextMeasurementId() {
        final long measurementId;
        Cursor measurementIdentifierQueryCursor = null;
        try {
            // Read get measurement id from database
            synchronized (this) {
                measurementIdentifierQueryCursor = resolver.query(getIdentifierUri(authority),
                        new String[] {IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID}, null, null, null);
                // This can be null, see documentation
                if (measurementIdentifierQueryCursor == null) {
                    throw new IllegalStateException("Unable to query for next measurement identifier!");
                }
                if (measurementIdentifierQueryCursor.getCount() > 1) {
                    throw new IllegalStateException("More entries than expected");
                }
                if (!measurementIdentifierQueryCursor.moveToFirst()) {
                    throw new IllegalStateException("Unable to get next measurement id!");
                }
                final int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor
                        .getColumnIndex(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID);
                measurementId = measurementIdentifierQueryCursor.getLong(indexOfMeasurementIdentifierColumn);
                Log.d(TAG, "Providing measurement identifier " + measurementId);
            }
        } finally {
            // This can be null, see documentation
            if (measurementIdentifierQueryCursor != null) {
                measurementIdentifierQueryCursor.close();
            }
        }
        return measurementId;
    }

    /**
     * Closes the specified {@link Measurement} which is currently {@link Measurement.MeasurementStatus#OPEN} or
     * {@link Measurement.MeasurementStatus#PAUSED}.
     *
     * (!) Attention: See {@link Measurement#setStatus(Measurement.MeasurementStatus)}
     *
     * @throws IllegalStateException when the {@param measurement} was not open or paused.
     */
    public void closeMeasurement(@NonNull final Measurement measurement) {
        Validate.isTrue(measurement.getStatus() == OPEN | measurement.getStatus() == PAUSED);

        Log.d(TAG, "Closing measurement: " + measurement.getIdentifier());
        measurement.setStatus(FINISHED);
    }

    /**
     * Returns all {@link Measurement}s. If you only want measurements of a specific
     * {@link Measurement.MeasurementStatus} call {@link #loadMeasurements(Measurement.MeasurementStatus)} instead.
     *
     * @return All measurements currently in the local persistent data storage.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        final List<Measurement> measurements = new ArrayList<>();
        measurements.addAll(loadMeasurements(OPEN));
        measurements.addAll(loadMeasurements(PAUSED));
        measurements.addAll(loadMeasurements(FINISHED));
        measurements.addAll(loadMeasurements(SYNCED));
        measurements.addAll(loadMeasurements(CORRUPTED));
        return measurements;
    }

    /**
     * Loads all {@link Measurement}s in a given {@link Measurement.MeasurementStatus}.
     *
     * @param status The status of the measurements to return
     * @return The measurements which are in the given status
     */
    public List<Measurement> loadMeasurements(@NonNull final Measurement.MeasurementStatus status) {
        final File measurementsFolder = getMeasurementsFolder(context, status);
        final File[] measurementFolders = measurementsFolder.listFiles(FileUtils.directoryFilter());

        final List<Measurement> measurements = new ArrayList<>();
        for (File measurement : measurementFolders) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(context, measurementId, status));
        }

        return measurements;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     *
     *         // FIXME: currently only finds open and finished measurements, see #loadMeasurements()
     */
    public Measurement loadMeasurement(final long measurementIdentifier) {
        final List<Measurement> measurements = new ArrayList<>();
        for (Measurement measurement : loadMeasurements()) {
            if (measurement.getIdentifier() == measurementIdentifier) {
                measurements.add(measurement);
            }
        }

        if (measurements.size() > 1) {
            throw new IllegalStateException("Too many measurements loaded with id: " + measurementIdentifier);
        }

        if (measurements.size() == 1) {
            return measurements.get(0);
        } else {
            return null;
        }
    }

    /**
     * Provide one specific measurement from the data storage if it exists. Makes sure it's in the expected status.
     *
     * @param measurementIdentifier The device wide unique identifier of the {@link Measurement} to load.
     * @param status The expected {@link Measurement.MeasurementStatus} of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     * @throws IllegalStateException if the measurement is in the wrong state.
     */
    public Measurement loadMeasurement(final long measurementIdentifier,
            @NonNull final Measurement.MeasurementStatus status) {
        final Measurement measurement = loadMeasurement(measurementIdentifier);
        if (measurement == null) {
            return null;
        }

        Validate.isTrue(measurement.getStatus() == status);
        return measurement;
    }

    /**
     * Provides information about whether there is {@link Measurement} if a specified
     * {@link Measurement.MeasurementStatus}.
     *
     * @param status The status for which to check if there are measurements
     * @return <code>true</code> if a measurement of the {@param status} exists.
     */
    public boolean hasMeasurement(@NonNull final Measurement.MeasurementStatus status) {
        final File measurementsFolder = getMeasurementsFolder(context, status);
        final File[] measurementFolders = measurementsFolder.listFiles(FileUtils.directoryFilter());

        boolean hasMeasurement = false;
        if (measurementFolders != null) {
            if (measurementFolders.length >= 1) {
                hasMeasurement = true;
            }
        }

        Log.d(TAG, hasMeasurement ? "One or more measurements of status " + status + " exist."
                : "No measurement of status " + status + "exist.");
        return hasMeasurement;
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     */
    private static Uri getIdentifierUri(final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * Loads a measurement with the provided id from the persistence layer as uncompressed and serialized in
     * the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION} format, ready to be compressed.
     *
     * @param measurementId The identifier of the measurement to load.
     * @return The bytes of the measurement in the <code>TRANSFER_FILE_FORMAT_VERSION</code> format.
     * @throws FileCorruptedException If the persisted measurement if broken or in a false format.
     * @throws IOException If the byte array could not be assembled.
     */
    public byte[] loadSerialized(final long measurementId) throws FileCorruptedException, IOException {
        final byte[] transferFileFormat = new byte[2];
        transferFileFormat[0] = (byte)(TRANSFER_FILE_FORMAT_VERSION >> 8);
        transferFileFormat[1] = (byte)TRANSFER_FILE_FORMAT_VERSION;
        final MetaFile.MetaData metaData = MetaFile.deserialize(context, measurementId);
        final byte[] pointCounts = serialize(metaData.getPointMetaData());
        final byte[] geoLocationData = metaData.getPointMetaData().getCountOfGeoLocations() > 0
                ? FileUtils.loadBytes(GeoLocationsFile.loadFile(context, measurementId))
                : new byte[] {};
        final byte[] accelerationData = metaData.getPointMetaData().getCountOfAccelerations() > 0
                ? FileUtils.loadBytes(AccelerationsFile.loadFile(context, measurementId))
                : new byte[] {};
        final byte[] rotationData = metaData.getPointMetaData().getCountOfRotations() > 0
                ? FileUtils.loadBytes(RotationsFile.loadFile(context, measurementId))
                : new byte[] {};
        final byte[] directionData = metaData.getPointMetaData().getCountOfDirections() > 0
                ? FileUtils.loadBytes(DirectionsFile.loadFile(context, measurementId))
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
     *
     * @param measurementId The identifier of the measurement to load.
     * @return The bytes of the measurement in the <code>TRANSFER_FILE_FORMAT_VERSION</code> format.
     * @throws FileCorruptedException If the persisted measurement if broken or in a false format.
     * @throws IOException If the byte array could not be assembled.
     */
    public InputStream loadSerializedCompressed(final long measurementId) throws FileCorruptedException, IOException {
        final byte[] data = loadSerialized(measurementId);

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
            metaData = MetaFile.deserialize(context, measurement.getIdentifier());
        } catch (FileCorruptedException e) {
            throw new IllegalStateException(e);
        }

        if (metaData.getPointMetaData().getCountOfAccelerations() > 0) {
            final File accelerationFile = AccelerationsFile.loadFile(context, measurement.getIdentifier());
            Validate.isTrue(accelerationFile.delete());
        }

        if (metaData.getPointMetaData().getCountOfRotations() > 0) {
            final File rotationFile = RotationsFile.loadFile(context, measurement.getIdentifier());
            Validate.isTrue(rotationFile.delete());
        }

        if (metaData.getPointMetaData().getCountOfDirections() > 0) {
            final File directionFile = DirectionsFile.loadFile(context, measurement.getIdentifier());
            Validate.isTrue(directionFile.delete());
        }
    }

    /**
     * We want to make sure the device id is stored at the same location as the next measurement id counter.
     * This way we ensure ether both or none of both is reset upon re-installation or app reset.
     *
     * @return The device is as string
     */
    public final String restoreOrCreateDeviceId() {
        Log.d(Constants.TAG, "Trying to load device identifier from content provider!");
        Cursor deviceIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                // Try to get device id from database
                deviceIdentifierQueryCursor = resolver.query(getIdentifierUri(authority),
                        new String[] {IdentifierTable.COLUMN_DEVICE_ID}, null, null, null);
                // This can be null, see documentation
                if (deviceIdentifierQueryCursor == null) {
                    throw new IllegalStateException("Unable to query for device identifier!");
                }
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
     * Attention: This currently does not reset the device id and measurement id counter.
     *
     * @return number of measurement directories removed.
     */
    public int clear() {
        final List<File> measurementFolders = new ArrayList<>();
        measurementFolders
                .addAll(Arrays.asList(getMeasurementsFolder(context, OPEN).listFiles(FileUtils.directoryFilter())));
        measurementFolders
                .addAll(Arrays.asList(getMeasurementsFolder(context, FINISHED).listFiles(FileUtils.directoryFilter())));
        measurementFolders.addAll(Arrays.asList(getMeasurementsFolder(context, Measurement.MeasurementStatus.SYNCED)
                .listFiles(FileUtils.directoryFilter())));
        measurementFolders.addAll(Arrays.asList(getMeasurementsFolder(context, Measurement.MeasurementStatus.CORRUPTED)
                .listFiles(FileUtils.directoryFilter())));
        // FIXME: add paused

        for (File dir : measurementFolders) {
            for (File file : dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        return measurementFolders.size();
    }

    /**
     * Removes one finished {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     * @throws IllegalStateException if the measurement folder does not exist or if the folder or it's files could not
     *             be removed.
     */
    public void delete(final @NonNull Measurement measurement) {

        final File measurementDir = measurement.getMeasurementFolder();
        if (!measurementDir.exists()) {
            throw new IllegalStateException("Failed to remove non existent measurement: " + measurementDir.getPath());
        }

        final File[] files = measurementDir.listFiles();
        for (File file : files) {
            final boolean success = file.delete();
            if (!success) {
                throw new IllegalStateException("Failed to remove file: " + file.getParent());
            }
        }
        final boolean dirDeleted = measurementDir.delete();
        if (!dirDeleted) {
            throw new IllegalStateException("Failed to delete measurement dir: " + measurementDir.getPath());
        }
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
        // To ensure the SDK user does not provide a null parameter
        // noinspection ConstantConditions
        if (measurement == null) {
            throw new NoSuchMeasurementException("Unable to load track for null measurement!");
        }

        // Load file with geolocations
        return GeoLocationsFile.deserialize(context, measurement.getIdentifier());
    }

    public Context getContext() {
        return context;
    }
}
