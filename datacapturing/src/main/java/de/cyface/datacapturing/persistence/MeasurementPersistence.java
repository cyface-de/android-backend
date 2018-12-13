package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.FileUtils;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.Persistence;
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
 * This class extends the {@link Persistence} class by live-Capturing specific functionality.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 6.0.0
 * @since 2.0.0
 */
public class MeasurementPersistence extends Persistence {

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
     * Creates a new completely initialized <code>MeasurementPersistence</code>.
     *
     * @param resolver <code>ContentResolver</code> that provides access to the {@link MeasuringPointsContentProvider}.
     * @param authority The authority used to identify the Android content provider to persist data to or load it from.
     * @param context The {@link Context} required to locate the app's internal storage directory.
     */
    public MeasurementPersistence(@NonNull final Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority) {
        // FIXME: this way the SDK implementing app has to provide an authority when delete(measurement)
        super(context, resolver, authority);
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public Measurement newMeasurement(final @NonNull Vehicle vehicle) {
        final Measurement measurement;
        synchronized (this) {
            measurement = super.newMeasurement(vehicle);
            currentMeasurementIdentifier = measurement.getIdentifier();
        }
        return measurement;
    }

    /**
     * Close the currently active {@link Measurement}.
     */
    public void closeRecentMeasurement() throws NoSuchMeasurementException {
        synchronized (this) {
            final Measurement openMeasurement = loadCurrentlyCapturedMeasurement();
            if (openMeasurement == null) {
                throw new NoSuchMeasurementException("Unable to close measurement as there is no open measurement");
            }
            super.closeMeasurement(openMeasurement);

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
                final File corruptedMeasurementDir = new File(fileUtils.getCorruptedFolderName(measurementId));
                if (!measurementDir.renameTo(corruptedMeasurementDir)) {
                    throw new IllegalStateException("Failed to clean up corrupted measurement by moving dir: "
                            + measurementDir.getAbsolutePath() + " -> " + corruptedMeasurementDir.getAbsolutePath());
                }
            }
            // Ignore non-broken measurements in open dir (e.g. paused measurements)
        }
    }
}
