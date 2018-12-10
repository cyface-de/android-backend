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
import java.util.zip.Deflater;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.RotationsFile;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>SyncAdapter</code> and its
 * delegate objects.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class MeasurementPersistence {

    /**
     * <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     */
    private final ContentResolver resolver;
    /**
     * The authority used to identify the Android content provider to persist data to or load it from.
     */
    private final String authority;
    /**
     * The {@link File} pointing to the directory containing synchronized measurements.
     */
    private final File synchronizedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing finished measurements.
     */
    private final File finishedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing corrupted measurements.
     */
    private final File corruptedMeasurementsDir;
    /**
     * Utility class to locate the directory and file paths used for persistence.
     */
    private final FileUtils fileUtils;
    /**
     * The {@link Context} required to access the persistence layer.
     */
    private final Context context;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link IdentifierTable}.
     * @param authority The authority used to load the identify the Android content provider to load the identifiers.
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public MeasurementPersistence(@NonNull final Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
        this.resolver = resolver;
        this.authority = authority; // FIXME: this way the SDK implementing app has to provide an authority when
                                    // delete(measurement)
        this.context = context;
        this.fileUtils = new FileUtils(context);
        this.finishedMeasurementsDir = new File(fileUtils.getFinishedMeasurementsDirPath());
        this.corruptedMeasurementsDir = new File(fileUtils.getCorruptedMeasurementsDirPath());
        this.synchronizedMeasurementsDir = new File(fileUtils.getSynchronizedMeasurementsDirPath());

        // Ensure measurements dir exist
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
     * Provides information about whether there is a currently finished measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     */
    public boolean hasFinshedMeasurement() {
        Log.d(TAG, "Checking if app has a finished measurement.");

        final File[] openMeasurementDirs = finishedMeasurementsDir.listFiles(FileUtils.directoryFilter());

        boolean hasFinishedMeasurement = false;
        if (openMeasurementDirs != null) {
            if (openMeasurementDirs.length >= 1) {
                hasFinishedMeasurement = true;
            }
        }

        Log.d(TAG, hasFinishedMeasurement ? "One or more measurement is ready for synchronization"
                : "No measurement is ready for synchronization.");
        return hasFinishedMeasurement;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
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
        final byte[] geoLocationData = FileUtils.loadBytes(GeoLocationsFile.loadFile(context, measurementId));
        final byte[] accelerationData = FileUtils.loadBytes(AccelerationsFile.loadFile(context, measurementId));
        final byte[] rotationData = FileUtils.loadBytes(RotationsFile.loadFile(context, measurementId));
        final byte[] directionData = FileUtils.loadBytes(DirectionsFile.loadFile(context, measurementId));

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
        final File accelerationFile = AccelerationsFile.loadFile(context, measurement.getIdentifier());
        Validate.isTrue(accelerationFile.delete());

        final File rotationFile = RotationsFile.loadFile(context, measurement.getIdentifier());
        Validate.isTrue(rotationFile.delete());

        final File directionFile = DirectionsFile.loadFile(context, measurement.getIdentifier());
        Validate.isTrue(directionFile.delete());
    }
}
