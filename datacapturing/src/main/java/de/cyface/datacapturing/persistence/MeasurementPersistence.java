package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.IdentifierTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.DirectionsFile;
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

        // Ensure open and finished measurements dir exist
        if (!openMeasurementsDir.exists()) {
            Validate.isTrue(openMeasurementsDir.mkdirs(), "Unable to create directory");
        }
        if (!finishedMeasurementsDir.exists()) {
            Validate.isTrue(finishedMeasurementsDir.mkdirs(), "Unable to create directory");
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
                Log.d(TAG, "Incremented mid counter to " + currentMeasurementIdentifier);
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
    public void closeRecentMeasurement() {
        Log.d(TAG, "Closing recent measurements");
        synchronized (this) {
            Validate.notNull(currentMeasurementIdentifier);
            Validate.isTrue(openMeasurementsDir.exists());
            Validate.isTrue(openMeasurementsDir.listFiles(FileUtils.directoryFilter()).length == 1);

            // Ensure closed measurement dir exists
            final File closedMeasurement = new File(fileUtils.getFinishedFolderName(currentMeasurementIdentifier));
            if (!closedMeasurement.getParentFile().exists()) {
                if (!closedMeasurement.getParentFile().mkdirs()) {
                    throw new IllegalStateException("Unable to create directory for finished measurement data: "
                            + closedMeasurement.getParentFile().getAbsolutePath());
                }
            }

            // Move measurement to closed dir
            final File openMeasurement = new File(fileUtils.getOpenFolderName(currentMeasurementIdentifier));
            if (!openMeasurement.renameTo(closedMeasurement)) {
                throw new IllegalStateException("Failed to finish measurement by moving dir: "
                        + openMeasurement.getAbsolutePath() + " -> " + closedMeasurement.getAbsolutePath());
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

        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());

        for (File measurement : openMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

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
     * Removes everything from the local persistent data storage.
     * Attention: This currently does not reset the device id and measurement id counter.
     *
     * @return number of rows removed from the database.
     */
    public int clear() {
        int ret = 0;
        final File[] finishedMeasurementDirs = finishedMeasurementsDir.listFiles(FileUtils.directoryFilter());
        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(FileUtils.directoryFilter());
        for (File finishedMeasurementDir : finishedMeasurementDirs) {
            Validate.isTrue(finishedMeasurementDir.delete());
        }
        ret += finishedMeasurementDirs.length;
        for (File openMeasurementDir : openMeasurementDirs) {
            Validate.isTrue(openMeasurementDir.delete());
        }
        ret += openMeasurementDirs.length;
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
}
