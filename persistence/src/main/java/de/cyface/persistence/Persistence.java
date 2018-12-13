package de.cyface.persistence;

import static de.cyface.persistence.Constants.TAG;
import static de.cyface.persistence.serialization.MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION;
import static de.cyface.persistence.serialization.MeasurementSerializer.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

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
 * <code>SyncAdapter</code> and its
 * delegate objects.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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
     * The {@link File} pointing to the directory containing open measurements.
     */
    protected final File openMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing finished measurements.
     */
    private final File finishedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing corrupted measurements.
     */
    protected final File corruptedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing synchronized measurements.
     */
    private final File synchronizedMeasurementsDir;
    /**
     * Utility class to locate the directory and file paths used for persistence.
     */
    protected final FileUtils fileUtils;
    /**
     * The {@link Context} required to access the persistence layer.
     */
    protected final Context context;

    /**
     * Creates a new completely initialized <code>Persistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link IdentifierTable}.
     * @param authority The authority used to load the identify the Android content provider to load the identifiers.
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public Persistence(@NonNull final Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
        this.resolver = resolver;
        this.authority = authority; // FIXME: this way the SDK implementing app has to provide an authority when
                                    // delete(measurement)
        this.context = context;
        this.fileUtils = new FileUtils(context);
        this.openMeasurementsDir = new File(fileUtils.getOpenMeasurementsDirPath());
        this.finishedMeasurementsDir = new File(fileUtils.getFinishedMeasurementsDirPath());
        this.corruptedMeasurementsDir = new File(fileUtils.getCorruptedMeasurementsDirPath());
        this.synchronizedMeasurementsDir = new File(fileUtils.getSynchronizedMeasurementsDirPath());

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
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The newly created <code>Measurement</code>.
     */// FIXME :clean up
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final long measurementId;
        synchronized (this) {
            Log.d(TAG, "Trying to load measurement identifier from content provider!");
            Cursor measurementIdentifierQueryCursor = null;
            try {
                // Read get measurement id from database
                synchronized (this) {
                    measurementIdentifierQueryCursor = resolver.query(getIdentifierUri(authority),
                            new String[] {IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID}, null, null, null);
                    // This can be null, see documentation
                    // noinspection ConstantConditions
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

                // Update measurement id counter
                final ContentValues values = new ContentValues();
                values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, measurementId + 1);
                final int updatedRows = resolver.update(getIdentifierUri(authority), values, null, null);
                Validate.isTrue(updatedRows == 1);
                Log.d(TAG, "Incremented mid counter to " + (measurementId + 1));
            } finally {
                // This can be null, see documentation
                // noinspection ConstantConditions
                if (measurementIdentifierQueryCursor != null) {
                    measurementIdentifierQueryCursor.close();
                }
            }
        }

        final File dir = fileUtils.getAndCreateDirectory(measurementId);
        Validate.isTrue(dir.exists(), "Measurement directory not created");
        new MetaFile(context, measurementId, vehicle);
        return new Measurement(measurementId);
    }

    /**
     * Close the specified {@link Measurement}.
     * (!) Attention: This only moved the measurement to the finished folder. The
     * <code>DataCapturingBackgroundService</code>
     * must have stopped normally in advance in order for the point counts to be written into the {@link MetaFile}.
     * Else, the file is seen as corrupted.
     */
    public void closeMeasurement(@NonNull final Measurement openMeasurement) {
        Log.d(TAG, "Closing measurement: " + openMeasurement.getIdentifier());
        synchronized (this) {
            Validate.isTrue(openMeasurementsDir.exists());

            // Ensure closed measurement dir exists
            final File closedMeasurement = new File(fileUtils.getFinishedFolderName(openMeasurement.getIdentifier()));
            if (!closedMeasurement.getParentFile().exists()) {
                if (!closedMeasurement.getParentFile().mkdirs()) {
                    throw new IllegalStateException("Unable to create directory for finished measurement data: "
                            + closedMeasurement.getParentFile().getAbsolutePath());
                }
            }

            // Move measurement to closed dir
            final File openMeasurementDir = new File(fileUtils.getOpenFolderName(openMeasurement.getIdentifier()));
            if (!openMeasurementDir.renameTo(closedMeasurement)) {
                throw new IllegalStateException("Failed to finish measurement by moving dir: "
                        + openMeasurementDir.getAbsolutePath() + " -> " + closedMeasurement.getAbsolutePath());
            }
        }
    }

    /**
     * @return All (open and finished) measurements currently in the local persistent data storage.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        final List<Measurement> measurements = new ArrayList<>();
        measurements.addAll(loadOpenMeasurements());
        measurements.addAll(loadFinishedMeasurements());
        return measurements;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
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
     * Loads only the finished {@link Measurement} instances from the local persistent data storage. Finished
     * measurements are the ones not currently capturing or paused.
     *
     * @return All the finished measurements from the local persistent data storage.
     */
    public List<Measurement> loadFinishedMeasurements() {
        final List<Measurement> measurements = new ArrayList<>();

        final File[] finishedMeasurementDirs = finishedMeasurementsDir.listFiles(FileUtils.directoryFilter());

        for (File measurement : finishedMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

        return measurements;
    }

    /**
     * Loads only the open {@link Measurement} instances from the local persistent data storage. Open
     * measurements are the ones currently capturing or paused. If the <code>DataCapturingBackgroundService</code>
     * stopped unexpectedly there can also be corrupted open measurements, see
     * <code>MeasurementPersistence#cleanCorruptedOpenMeasurements()</code>
     *
     * @return All the open measurements from the local persistent data storage.
     */
    public List<Measurement> loadOpenMeasurements() {
        final List<Measurement> measurements = new ArrayList<>();

        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());

        for (File measurement : openMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

        return measurements;
    }

    /**
     * Provides information about whether there is a currently finished measurement or not.
     *
     * @return <code>true</code> if a measurement is finished; <code>false</code> otherwise.
     */
    public boolean hasFinshedMeasurement() {
        Log.d(TAG, "Checking if app has a finished measurement.");

        final File[] measurementDirs = finishedMeasurementsDir.listFiles(FileUtils.directoryFilter());

        boolean hasFinishedMeasurement = false;
        if (measurementDirs != null) {
            if (measurementDirs.length >= 1) {
                hasFinishedMeasurement = true;
            }
        }

        Log.d(TAG, hasFinishedMeasurement ? "One or more measurement is ready for synchronization"
                : "No measurement is ready for synchronization.");
        return hasFinishedMeasurement;
    }

    /**
     * Provides information about whether there is a currently synchronized measurement or not.
     *
     * @return <code>true</code> if a measurement is synchronized; <code>false</code> otherwise.
     */
    public boolean hasSyncedMeasurement() {
        Log.d(TAG, "Checking if app has a synchronized measurement.");

        final File[] measurementDirs = synchronizedMeasurementsDir.listFiles(FileUtils.directoryFilter());

        boolean hasSyncedMeasurement = false;
        if (measurementDirs != null) {
            if (measurementDirs.length >= 1) {
                hasSyncedMeasurement = true;
            }
        }

        return hasSyncedMeasurement;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     *         FIXME: reduce code duplicity
     */
    public Measurement loadFinishedMeasurement(final long measurementIdentifier) {
        final List<Measurement> measurements = new ArrayList<>();
        for (Measurement measurement : loadFinishedMeasurements()) {
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
     * Provides information about whether there is a currently open measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     */
    public boolean hasOpenMeasurement() {
        Log.d(TAG, "Checking if app has an open measurement.");

        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());

        boolean hasOpenMeasurement = false;
        if (openMeasurementDirs != null) {
            if (openMeasurementDirs.length > 1) {
                throw new IllegalStateException("More than one measurement is open.");
            }
            if (openMeasurementDirs.length == 1) {
                hasOpenMeasurement = true;
            }
        }

        Log.d(TAG, hasOpenMeasurement ? "One measurement is open." : "No measurement is open.");
        return hasOpenMeasurement;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     */
    public Measurement loadSyncedMeasurement(final long measurementIdentifier) {
        final List<Measurement> measurements = new ArrayList<>();
        for (Measurement measurement : loadSyncedMeasurements()) {
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
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     */
    public Measurement loadOpenMeasurement(final long measurementIdentifier) {
        final List<Measurement> measurements = new ArrayList<>();
        for (Measurement measurement : loadOpenMeasurements()) {
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
     * Loads only the synchronized {@link Measurement} instances from the local persistent data storage.
     *
     * @return All the synchronized measurements from the local persistent data storage.
     */
    public List<Measurement> loadSyncedMeasurements() {
        final List<Measurement> measurements = new ArrayList<>();

        final File[] finishedMeasurementDirs = synchronizedMeasurementsDir.listFiles(FileUtils.directoryFilter());

        for (File measurement : finishedMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

        return measurements;
    }

    /**
     * @return The content provider URI for the {@link IdentifierTable}
     */
    private static Uri getIdentifierUri(final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
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
        final byte[] data = outputStream.toByteArray();

        final Deflater compressor = new Deflater();
        compressor.setInput(data);
        compressor.finish();

        final byte[] output = new byte[data.length];
        final int lengthOfCompressedData = compressor.deflate(output);
        Log.d(TAG, String.format("Compressed data to %d bytes.", lengthOfCompressedData));
        return new ByteArrayInputStream(output, 0, lengthOfCompressedData);
    }

    /**
     * Marks a finished {@link Measurement} as synchronized and deletes the sensor data.
     *
     * @param measurement The measurement to remove.
     */
    public void markAsSynchronized(final @NonNull Measurement measurement) {

        final File finishedMeasurementDir = new File(fileUtils.getFinishedFolderName(measurement.getIdentifier()));
        if (!finishedMeasurementDir.exists()) {
            throw new IllegalStateException(
                    "Failed to remove non existent finished measurement: " + measurement.getIdentifier());
        }

        // Move measurement to synced dir
        final File syncedMeasurementDir = new File(fileUtils.getSyncedFolderName(measurement.getIdentifier()));
        if (!finishedMeasurementDir.renameTo(syncedMeasurementDir)) {
            throw new IllegalStateException("Failed to move measurement: " + finishedMeasurementDir.getAbsolutePath()
                    + " -> " + syncedMeasurementDir.getAbsolutePath());
        }

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
     * @param resolver The {@link ContentResolver} to access the {@link IdentifierTable}.
     * @return The device is as string
     */
    public final String restoreOrCreateDeviceId(final ContentResolver resolver) {
        Log.d(Constants.TAG, "Trying to load device identifier from content provider!");
        Cursor deviceIdentifierQueryCursor = null;
        try {
            synchronized (this) {
                // Try to get device id from database
                deviceIdentifierQueryCursor = resolver.query(getIdentifierUri(authority),
                        new String[] {IdentifierTable.COLUMN_DEVICE_ID}, null, null, null);
                // This can be null, see documentation
                // noinspection ConstantConditions
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
            // noinspection ConstantConditions
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
        int ret = 0;
        final File[] finishedMeasurementDirs = finishedMeasurementsDir.listFiles(FileUtils.directoryFilter());
        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());
        final File[] corruptedMeasurementDirs = corruptedMeasurementsDir.listFiles(FileUtils.directoryFilter());
        final File[] syncedMeasurementDirs = synchronizedMeasurementsDir.listFiles(FileUtils.directoryFilter());
        for (File dir : finishedMeasurementDirs) {
            for (File file : dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += finishedMeasurementDirs.length;
        for (File dir : openMeasurementDirs) {
            for (File file : dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += openMeasurementDirs.length;
        for (File dir : corruptedMeasurementDirs) {
            for (File file : dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += corruptedMeasurementDirs.length;
        for (File dir : syncedMeasurementDirs) {
            for (File file : dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += syncedMeasurementDirs.length;
        return ret;
    }

    /**
     * Removes one finished {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     */
    public void delete(final @NonNull Measurement measurement) {

        final File measurementDir = new File(fileUtils.getFinishedFolderName(measurement.getIdentifier()));
        if (!measurementDir.exists()) {
            throw new IllegalStateException(
                    "Failed to remove non existent finished measurement: " + measurement.getIdentifier());
        }

        final File[] files = measurementDir.listFiles();
        for (File file : files) {
            final boolean success = file.delete();
            if (!success) {
                throw new IllegalStateException("Failed to remove file: " + file);
            }
        }
        final boolean dirDeleted = measurementDir.delete();
        if (!dirDeleted) {
            throw new IllegalStateException(
                    "Failed to delete finished measurement dir: " + measurement.getIdentifier());
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

    public ContentResolver getResolver() {
        return resolver;
    }

    public Context getContext() {
        return context;
    }
}
