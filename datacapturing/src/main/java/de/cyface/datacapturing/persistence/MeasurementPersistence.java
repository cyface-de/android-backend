package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.persistence.Constants.FINISHED_MEASUREMENTS_PATH;
import static de.cyface.persistence.Constants.OPEN_MEASUREMENTS_PATH;
import static de.cyface.persistence.Utils.getAndCreateDirectory;
import static de.cyface.persistence.Utils.getFolderName;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import de.cyface.datacapturing.Measurement;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.AccelerationsFile;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
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
     * A threadPool to execute operations on their own background threads.
     */
    private ExecutorService threadPool;

    /**
     * Caching the current measurement identifier, so we do not need to ask the database each time we require the
     * current measurement identifier. This is <code>null</code> if there is no running measurement or if we lost the
     * cache due to Android stopping the application hosting the data capturing service.
     */
    private Long currentMeasurementIdentifier;

    private GeoLocationsFile geoLocationsFile;
    private AccelerationsFile accelerationsFile;
    private MetaFile metaFile;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     */
    public MeasurementPersistence() {
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Creates a new {@link Measurement} for the provided {@link Vehicle}.
     *
     * @param vehicle The vehicle to create a new measurement for.
     * @return The newly created <code>Measurement</code>.
     */
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final long measurementId = System.currentTimeMillis(); // FIXME: workaround for testing
        final File dir = getAndCreateDirectory(measurementId);
        Validate.isTrue(dir.exists(), "Measurement file not created");
        metaFile = new MetaFile(measurementId, vehicle);
        currentMeasurementIdentifier = measurementId;
        return new Measurement(currentMeasurementIdentifier);
    }

    /**
     * Close the currently active {@link Measurement}.
     */
    public void closeRecentMeasurement() {
        // For brevity we are closing all open measurements. In order to make sure, that no error occurred
        // we need to check that there is only one such open measurement before closing anything.
        Log.d(TAG, "Closing recent measurements");
        synchronized (this) {
            Validate.notNull(currentMeasurementIdentifier);
            final File openMeasurementDir = new File(getFolderName(true, currentMeasurementIdentifier));
            final File closedMeasurementDir = new File(getFolderName(false, currentMeasurementIdentifier));

            if (!closedMeasurementDir.getParentFile().exists()) {
                if (!closedMeasurementDir.getParentFile().mkdirs()) {
                    throw new IllegalStateException("Unable to create directory for finished measurement data: "
                            + closedMeasurementDir.getParentFile().getAbsolutePath());
                }
            }

            if (!openMeasurementDir.renameTo(closedMeasurementDir)) {
                throw new IllegalStateException("Failed to finish measurement by moving dir: "
                        + openMeasurementDir.getAbsolutePath() + " -> " + closedMeasurementDir.getAbsolutePath());
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
            accelerationsFile = new AccelerationsFile(measurementIdentifier);
        }

        CapturedDataWriter writer = new CapturedDataWriter(data, accelerationsFile, callback);
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
            geoLocationsFile = new GeoLocationsFile(measurementIdentifier);
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

        File openMeasurementsDir = new File(OPEN_MEASUREMENTS_PATH);
        File[] openMeasurementDirs = openMeasurementsDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

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
     * @throws DataCapturingException If access to the persistence layer was somehow impossible. This is probably a
     *             serious system issue and should not occur.
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use
     *             {@link #hasOpenMeasurement()} to check, whether there is an actual open measurement.
     */
    private long refreshIdentifierOfCurrentlyCapturedMeasurement()
            throws DataCapturingException, NoSuchMeasurementException {
        Log.d(TAG, "Trying to load measurement identifier from persistence layer!");
        synchronized (this) {

            File openMeasurementsDir = new File(OPEN_MEASUREMENTS_PATH);
            File[] openMeasurementDirs = openMeasurementsDir.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });

            if (openMeasurementDirs.length == 0) {
                throw new NoSuchMeasurementException("No open measurement found!");
            }

            long measurementIdentifier = Long.parseLong(openMeasurementDirs[0].getName());
            if (openMeasurementDirs.length > 1) {
                Log.w(TAG,
                        "More than one measurement is open. Unable to decide where to store data! Using the one with the highest identifier!");
                for (File openMeasurement : openMeasurementDirs) {
                    final long folderMeasurementId = Long.parseLong(openMeasurement.getName());
                    if (folderMeasurementId > measurementIdentifier) {
                        measurementIdentifier = folderMeasurementId;
                    }
                }
            }

            Log.d(TAG, "Providing measurement identifier " + measurementIdentifier);
            currentMeasurementIdentifier = measurementIdentifier;
            return measurementIdentifier;
        }
    }

    /**
     * @return All measurements currently in the local persistent data storage.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        List<Measurement> measurements = new ArrayList<>();

        final File openMeasurementsDir = new File(OPEN_MEASUREMENTS_PATH);
        final File[] openMeasurementDirs = openMeasurementsDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        for (File measurement : openMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

        final File finishedMeasurementsDir = new File(FINISHED_MEASUREMENTS_PATH);
        final File[] finishedMeasurementDirs = finishedMeasurementsDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        for (File measurement : finishedMeasurementDirs) {
            final long measurementId = Long.parseLong(measurement.getName());
            measurements.add(new Measurement(measurementId));
        }

        return measurements;
    }

    /**
     * Provide one specific measurement from the data storage if it exists.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded measurement if it exists; <code>null</code> otherwise.
     * @throws DataCapturingException If accessing the content provider fails.
     *             /
     *             public Measurement loadMeasurement(final long measurementIdentifier) throws DataCapturingException {
     *             Uri measurementUri =
     *             getMeasurementUri().buildUpon().appendPath(Long.toString(measurementIdentifier)).build();
     *             Cursor cursor = null;
     * 
     *             try {
     *             cursor = resolver.query(measurementUri, null, null, null, null);
     * 
     *             if (cursor == null) {
     *             throw new DataCapturingException(
     *             "Cursor for loading a measurement not correctly initialized. Was null for URI "
     *             + measurementUri);
     *             }
     * 
     *             if (cursor.getCount() > 1) {
     *             throw new DataCapturingException("Too many measurements loaded from URI: " + measurementUri);
     *             }
     * 
     *             if (cursor.moveToFirst()) {
     *             return new Measurement(measurementIdentifier);
     *             } else {
     *             return null;
     *             }
     *             } finally {
     *             if (cursor != null) {
     *             cursor.close();
     *             }
     *             }
     *             }
     */

    /**
     * Loads only the finished {@link Measurement} instances from the local persistent data storage. Finished
     * measurements are the ones not currently capturing or paused.
     *
     * @return All the finished measurements from the local persistent data storage.
     *         /
     *         public List<Measurement> loadFinishedMeasurements() {
     *         Cursor cursor = null;
     * 
     *         try {
     *         List<Measurement> ret = new ArrayList<>();
     * 
     *         cursor = resolver.query(getMeasurementUri(), null, MeasurementTable.COLUMN_FINISHED + "=?",
     *         new String[] {String.valueOf(MeasuringPointsContentProvider.SQLITE_TRUE)}, null);
     *         if (cursor == null) {
     *         throw new IllegalStateException("Unable to access database to load measurements!");
     *         }
     * 
     *         while (cursor.moveToNext()) {
     *         long measurementIdentifier = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
     * 
     *         ret.add(new Measurement(measurementIdentifier));
     *         }
     * 
     *         return ret;
     * 
     *         } finally {
     *         if (cursor != null) {
     *         cursor.close();
     *         }
     *         }
     *         }
     */

    /**
     * Removes everything from the local persistent data storage.
     *
     * @return number of rows removed from the database.
     *         /
     *         public int clear() {
     *         int ret = 0;
     *         ret += resolver.delete(getRotationsUri(), null, null);
     *         ret += resolver.delete(getAccelerationsUri(), null, null);
     *         ret += resolver.delete(getDirectionsUri(), null, null);
     *         ret += resolver.delete(getGeoLocationsUri(), null, null);
     *         ret += resolver.delete(getMeasurementUri(), null, null);
     *         return ret;
     *         }
     */

    /**
     * Removes one finished {@link Measurement} from the local persistent data storage.
     *
     * @param measurement The measurement to remove.
     */
    public void delete(final @NonNull Measurement measurement) throws NoSuchMeasurementException {

        final File measurementDir = new File(getFolderName(false, measurement.getIdentifier()));
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
     *             /
     *             public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) throws
     *             NoSuchMeasurementException {
     *             if (measurement == null) {
     *             throw new NoSuchMeasurementException("Unable to load track for null measurement!");
     *             }
     * 
     *             Cursor locationsCursor = null;
     *             try {
     *             locationsCursor = resolver.query(getGeoLocationsUri(), null, GpsPointsTable.COLUMN_MEASUREMENT_FK +
     *             "=?",
     *             new String[] {Long.valueOf(measurement.getIdentifier()).toString()},
     *             GpsPointsTable.COLUMN_GPS_TIME + " ASC");
     * 
     *             if (locationsCursor == null) {
     *             return Collections.emptyList();
     *             }
     * 
     *             List<GeoLocation> ret = new ArrayList<>(locationsCursor.getCount());
     *             while (locationsCursor.moveToNext()) {
     *             double lat = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LAT));
     *             double lon = locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_LON));
     *             long timestamp = locationsCursor
     *             .getLong(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_GPS_TIME));
     *             double speed =
     *             locationsCursor.getDouble(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_SPEED));
     *             float accuracy = locationsCursor
     *             .getFloat(locationsCursor.getColumnIndex(GpsPointsTable.COLUMN_ACCURACY));
     * 
     *             ret.add(new GeoLocation(lat, lon, timestamp, speed, accuracy));
     *             }
     * 
     *             return ret;
     *             } finally {
     *             if (locationsCursor != null) {
     *             locationsCursor.close();
     *             }
     *             }
     *             }
     */

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
}
