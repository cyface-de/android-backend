package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.Persistence;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.GeoLocationsFile;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.Validate;

/**
 * This class extends the {@link Persistence} class by live-Capturing specific functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 2.0.0
 */
public class MeasurementPersistence extends Persistence {

    /**
     * A threadPool to execute operations on their own background threads.
     */
    private ExecutorService threadPool;
    /**
     * Caching the current {@link Measurement}, so we do not need to ask the database each time we require the
     * current measurement. This is <code>null</code> if there is no running measurement or if we lost the
     * cache due to Android stopping the application hosting the data capturing service.
     */
    private Measurement currentMeasurement;
    /**
     * The file to write the geolocation points to.
     */
    private GeoLocationsFile geoLocationsFile;
    /**
     * The file to write the acceleration points to.
     */
    private Point3dFile accelerationsFile;
    /**
     * The file to write the rotation points to.
     */
    private Point3dFile rotationsFile;
    /**
     * The file to write the direction points to.
     */
    private Point3dFile directionsFile;

    /**
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public MeasurementPersistence(@NonNull final Context context, final @NonNull String authority) {
        super(context, authority);
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        currentMeasurement = super.newMeasurement(vehicle);
        return currentMeasurement;
    }

    /**
     * Finish the currently active {@link Measurement}.
     */
    public void finishRecentMeasurement() throws NoSuchMeasurementException {
        synchronized (this) {
            final Measurement measurement = loadCurrentlyCapturedMeasurement();
            if (measurement == null) {
                throw new NoSuchMeasurementException("Unable to close measurement as there is no measurement");
            }
            super.closeMeasurement(measurement);
            currentMeasurement = null;
        }
    }

    /**
     * Saves the provided {@link CapturedData} to the local persistent storage of the device.
     *
     * @param data The data to store.
     * @param measurement The {@link Measurement} to store the data to.
     */
    public void storeData(final @NonNull CapturedData data, final Measurement measurement,
            final @NonNull WritingDataCompletedCallback callback) {
        if (threadPool.isShutdown()) {
            return;
        }
        if (accelerationsFile == null) {
            accelerationsFile = new Point3dFile(measurement, FileUtils.ACCELERATIONS_FILE_NAME,
                    FileUtils.ACCELERATIONS_FILE_EXTENSION);
        }
        if (rotationsFile == null) {
            rotationsFile = new Point3dFile(measurement, FileUtils.ROTATIONS_FILE_NAME,
                    FileUtils.ROTATION_FILE_EXTENSION);
        }
        if (directionsFile == null) {
            directionsFile = new Point3dFile(measurement, FileUtils.DIRECTION_FILE_NAME,
                    FileUtils.DIRECTION_FILE_EXTENSION);
        }

        final CapturedDataWriter writer = new CapturedDataWriter(data, accelerationsFile, rotationsFile, directionsFile,
                callback);
        threadPool.submit(writer);
    }

    /**
     * Stores the provided geo location under the currently active captured measurement.
     * 
     * @param location The geo location to store.
     * @param measurement The {@link Measurement} to store the data to.
     */
    public void storeLocation(final @NonNull GeoLocation location, final Measurement measurement) {
        if (geoLocationsFile == null) {
            geoLocationsFile = new GeoLocationsFile(measurement);
        }

        geoLocationsFile.append(location);
    }

    /**
     * Loads the currently captured measurement and refreshes the {@link #currentMeasurement} pointer. This method
     * should only be called if capturing is active. It throws an error otherwise.
     *
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use {@link #hasMeasurement(Measurement.MeasurementStatus)}} to check whether there is an actual
     *             {@link Measurement.MeasurementStatus#OPEN} measurement.
     */
    private void refreshCurrentlyCapturedMeasurementPointer() throws NoSuchMeasurementException {
        Log.d(TAG, "Trying to load currently captured measurement from persistence layer!");

        final List<Measurement> openMeasurements = loadMeasurements(Measurement.MeasurementStatus.OPEN);
        if (openMeasurements.size() == 0) {
            throw new NoSuchMeasurementException("No open measurement found!");
        }
        if (openMeasurements.size() > 1) {
            throw new IllegalStateException("More than one measurement is open.");
        }

        currentMeasurement = openMeasurements.get(0);
        Log.d(TAG, "Refreshed currentMeasurement to: " + currentMeasurement.getIdentifier());
    }

    /**
     * Loads the current measurement from the internal cache if possible, or from the persistence layer if an open
     * measurement exists. If neither the cache nor the persistence layer have an open measurement this method returns
     * <code>null</code>.
     *
     * @return The currently captured measurement or <code>null</code> if none exists.
     * @throws NoSuchMeasurementException when there is no active measurement
     */
    public @Nullable Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException {
        Log.d(TAG, "loadCurrentlyCapturedMeasurement ...");

        synchronized (this) {
            final boolean hasUnfinishedMeasurement = hasMeasurement(Measurement.MeasurementStatus.OPEN)
                    || hasMeasurement(Measurement.MeasurementStatus.PAUSED);
            if (currentMeasurement == null && hasUnfinishedMeasurement) {
                refreshCurrentlyCapturedMeasurementPointer();
                Validate.isTrue(currentMeasurement != null);
            }
            if (currentMeasurement == null) {
                return null;
            }
            return currentMeasurement;
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
     * Before starting, resuming or synchronizing a measurement we make sure that there are no corrupted measurements
     * left for a specific {@link Measurement.MeasurementStatus}.
     * This can happen when the {@link DataCapturingBackgroundService} dies with a hard exception and, thus, did not
     * append the {@link MetaFile.PointMetaData} to the {@link MetaFile} (i.e. there is a dead "OPEN" measurement).
     *
     * TODO: should we add markCorruptedMeasurements(FINISHED) before sync (if sync crashed while deleting a m)?
     *
     * @param status The status of which all corrupted measurements are marked as
     *            {@link Measurement.MeasurementStatus#CORRUPTED}
     */
    public void markCorruptedMeasurements(@NonNull final Measurement.MeasurementStatus status) {

        final List<Measurement> measurements = loadMeasurements(status);
        for (Measurement measurement : measurements) {
            Validate.isTrue(status == Measurement.MeasurementStatus.OPEN, "Unsupported case");

            // Identify and mark dead "OPEN" measurements
            try {
                measurement.getMetaFile().deserialize();
            } catch (final FileCorruptedException e) {
                // This means the measurement is corrupted
                Log.d(TAG, "Moving corrupted measurement: " + measurement.getMeasurementFolder());
                measurement.setStatus(Measurement.MeasurementStatus.CORRUPTED);
            }
        }
    }
}
