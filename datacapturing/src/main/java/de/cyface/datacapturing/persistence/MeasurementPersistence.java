package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.Constants;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.RotationsFile;
import de.cyface.utils.Validate;

/**
 * This class wraps the Cyface Android persistence API as required by the <code>DataCapturingListener</code> and its
 * delegate objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 6.0.0
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
     * The file to write the geolocation points to.
     */
    private GeoLocationsFile geoLocationsFile;
    /**
     * The file to write the acceleration points to.
     */
    private AccelerationsFile accelerationsFile;
    /**
     * The file to write the rotation points to.
     */
    private RotationsFile rotationsFile;
    /**
     * The file to write the direction points to.
     */
    private DirectionsFile directionsFile;
    /**
     * The {@link File} pointing to the directory containing open measurements.
     */
    private final File openMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing finished measurements.
     */
    private final File finishedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing corrupted measurements.
     */
    private final File corruptedMeasurementsDir;
    /**
     * The {@link File} pointing to the directory containing synchronized measurements.
     */
    private final File synchronizedMeasurementsDir;
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
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public MeasurementPersistence(@NonNull final Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
        this.resolver = resolver;
        this.threadPool = Executors.newCachedThreadPool();
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
        if (!finishedMeasurementsDir.exists()) {
            Validate.isTrue(finishedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!corruptedMeasurementsDir.exists()) {
            Validate.isTrue(corruptedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!synchronizedMeasurementsDir.exists()) {
            Validate.isTrue(synchronizedMeasurementsDir.mkdirs(), "Unable to create directory");
        }
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The newly created <code>Measurement</code>.
     */// FIXME :clean up
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) throws DataCapturingException {
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
                        throw new DataCapturingException("Unable to query for next measurement identifier!");
                    }
                    if (measurementIdentifierQueryCursor.getCount() > 1) {
                        throw new IllegalStateException("More entries than expected");
                    }
                    if (!measurementIdentifierQueryCursor.moveToFirst()) {
                        throw new IllegalStateException("Unable to get next measurement id!");
                    }
                    final int indexOfMeasurementIdentifierColumn = measurementIdentifierQueryCursor
                            .getColumnIndex(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID);
                    currentMeasurementIdentifier = measurementIdentifierQueryCursor
                            .getLong(indexOfMeasurementIdentifierColumn);
                    Log.d(TAG, "Providing measurement identifier " + currentMeasurementIdentifier);
                }

                // Update measurement id counter
                final ContentValues values = new ContentValues();
                values.put(IdentifierTable.COLUMN_NEXT_MEASUREMENT_ID, currentMeasurementIdentifier + 1);
                final int updatedRows = resolver.update(getIdentifierUri(authority), values, null, null);
                Validate.isTrue(updatedRows == 1);
                Log.d(TAG, "Incremented mid counter to " + (currentMeasurementIdentifier + 1));
            } finally {
                // This can be null, see documentation
                // noinspection ConstantConditions
                if (measurementIdentifierQueryCursor != null) {
                    measurementIdentifierQueryCursor.close();
                }
            }
        }

        final File dir = fileUtils.getAndCreateDirectory(currentMeasurementIdentifier);
        Validate.isTrue(dir.exists(), "Measurement directory not created");
        new MetaFile(context, currentMeasurementIdentifier, vehicle);
        return new Measurement(currentMeasurementIdentifier);
    }

    /**
     * Close the currently active {@link Measurement}.
     */
    public void closeRecentMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "Closing recent measurements");
        synchronized (this) {
            final Measurement openMeasurement = loadCurrentlyCapturedMeasurement();
            if (openMeasurement == null) {
                throw new NoSuchMeasurementException("Unable to close measurement as there is no open measurement");
            }
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

            currentMeasurementIdentifier = null;
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
        if (accelerationsFile == null) {
            accelerationsFile = new AccelerationsFile(context, measurementIdentifier);
        }
        if (rotationsFile == null) {
            rotationsFile = new RotationsFile(context, measurementIdentifier);
        }
        if (directionsFile == null) {
            directionsFile = new DirectionsFile(context, measurementIdentifier);
        }

        CapturedDataWriter writer = new CapturedDataWriter(data, accelerationsFile, rotationsFile, directionsFile,
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
        if (geoLocationsFile == null) {
            geoLocationsFile = new GeoLocationsFile(context, measurementIdentifier);
        }

        geoLocationsFile.append(location);
    }

    /**
     * Provides information about whether there is a currently open measurement or not.
     *
     * @return <code>true</code> if a measurement is open; <code>false</code> otherwise.
     * @throws DataCapturingException If more than one measurement is open or access to the content provider was
     *             impossible. The second case is probably a serious system issue and should not happen.
     */
    public boolean hasOpenMeasurement() throws DataCapturingException {
        Log.d(TAG, "Checking if app has an open measurement.");

        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());

        boolean hasOpenMeasurement = false;
        if (openMeasurementDirs != null) {
            if (openMeasurementDirs.length > 1) {
                throw new DataCapturingException("More than one measurement is open.");
            }
            if (openMeasurementDirs.length == 1) {
                hasOpenMeasurement = true;
            }
        }

        Log.d(TAG, hasOpenMeasurement ? "One measurement is open." : "No measurement is open.");
        return hasOpenMeasurement;
    }

    /**
     * Provides the identifier of the measurement currently captured by the framework. This method should only be called
     * if capturing is active. It throws an error otherwise.
     *
     * @return The system wide unique identifier of the active measurement.
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use
     *             {@link #hasOpenMeasurement()} to check, whether there is an actual open measurement.
     */
    private long refreshIdentifierOfCurrentlyCapturedMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "Trying to load measurement identifier from persistence layer!");
        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());

        if (openMeasurementDirs.length == 0) {
            throw new NoSuchMeasurementException("No open measurement found!");
        }
        if (openMeasurementDirs.length > 1) {
            throw new IllegalStateException("More than one measurement is open.");
        }

        currentMeasurementIdentifier = Long.parseLong(openMeasurementDirs[0].getName());
        Log.d(TAG, "Providing measurement identifier " + currentMeasurementIdentifier);
        return currentMeasurementIdentifier;
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
     * @throws DataCapturingException If accessing the content provider fails.
     */
    public Measurement loadMeasurement(final long measurementIdentifier) throws DataCapturingException {
        final List<Measurement> measurements = new ArrayList<>();
        for (Measurement measurement : loadMeasurements()) {
            if (measurement.getIdentifier() == measurementIdentifier) {
                measurements.add(measurement);
            }
        }

        if (measurements.size() > 1) {
            throw new DataCapturingException("Too many measurements loaded with id: " + measurementIdentifier);
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
     * measurements are the ones currently capturing or paused. If the {@link DataCapturingBackgroundService}
     * stopped unexpectedly there can also be corrupted open measurements, see
     * {@link MeasurementPersistence#cleanCorruptedOpenMeasurements()}
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
            for (File file: dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += finishedMeasurementDirs.length;
        for (File dir : openMeasurementDirs) {
            for (File file: dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += openMeasurementDirs.length;
        for (File dir : corruptedMeasurementDirs) {
            for (File file: dir.listFiles()) {
                Validate.isTrue(file.delete());
            }
            Validate.isTrue(dir.delete());
        }
        ret += corruptedMeasurementDirs.length;
        for (File dir : syncedMeasurementDirs) {
            for (File file: dir.listFiles()) {
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
     * FIXME: duplicate in other M.P. class
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

    /**
     * Loads the identifier of the current measurement from the internal cache if possible, or from the persistence
     * layer if an open measurement exists. If neither the cache nor the persistence layer have an open measurement this
     * method returns <code>null</code>.
     *
     * @return The identifier of the currently captured measurement or <code>null</code> if none exists.
     */
    public @Nullable Measurement loadCurrentlyCapturedMeasurement() {
        Log.d(TAG, "loadCurrentlyCapturedMeasurement ...");
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
     * @return The content provider URI for the {@link IdentifierTable}
     */
    public static Uri getIdentifierUri(final String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(IdentifierTable.URI_PATH).build();
    }

    /**
     * Before starting or resuming a measurement we need to make sure that there are no corrupted measurements
     * left in the /open/ folder. This can happen when the {@link DataCapturingBackgroundService} dies a devastating
     * Exception-death and was not able to append the {@link MetaFile.PointMetaData} to the {@link MetaFile}.
     *
     * FIXME: If we want to support double start/resume calls this method needs to be adjusted
     * so that it does not clean open measurements when there is capturing going on, else we kill
     * an ongoing measurement! We could also make sure this method is not called when capturing is
     * taking place. But how do we synchronize this? The capturing could be in progress of starting,
     * i.e. created the folder but the BGS is not yet launched? #MOV-460
     */
    public void cleanCorruptedOpenMeasurements() {
        final File[] openMeasurements = openMeasurementsDir.listFiles(FileUtils.directoryFilter());
        for (File measurementDir : openMeasurements) {
            final int measurementId = Integer.parseInt(measurementDir.getName());
            try {
                MetaFile.deserialize(context, measurementId);
            } catch (final FileCorruptedException e) {
                // Expected, we want to mark such measurements as corrupted

                Log.d(TAG, "Moving corrupted measurement: " + measurementId);
                Validate.isTrue(corruptedMeasurementsDir.exists());

                // Move measurement to corrupted dir
                final File corruptedMeasurementDir = new File(
                        fileUtils.getCorruptedFolderName(measurementId));
                if (!measurementDir.renameTo(corruptedMeasurementDir)) {
                    throw new IllegalStateException("Failed to clean up corrupted measurement by moving dir: "
                            + measurementDir.getAbsolutePath() + " -> "
                            + corruptedMeasurementDir.getAbsolutePath());
                }
            }
            // Ignore non-broken measurements in open dir (e.g. paused measurements)
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
}
