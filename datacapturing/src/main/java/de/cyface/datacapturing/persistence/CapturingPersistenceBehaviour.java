package de.cyface.datacapturing.persistence;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.util.Log;
import androidx.annotation.NonNull;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.persistence.Constants;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This {@link PersistenceBehaviour} is used when a {@link PersistenceLayer} is used to capture a {@link Measurement}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.0.0
 */
public class CapturingPersistenceBehaviour implements PersistenceBehaviour {

    /**
     * A threadPool to execute operations on their own background threads.
     */
    private ExecutorService threadPool;
    /**
     * Caching the current {@link Measurement}, so we do not need to ask the database each time we require the
     * current measurement. This is <code>null</code> if there is no running measurement or if we lost the
     * cache due to Android stopping the application hosting the data capturing service.
     */
    private Long currentMeasurementIdentifier;
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
     * A reference to the {@link PersistenceLayer} which implements this behaviour to access it's methods.
     */
    private PersistenceLayer persistenceLayer;

    @Override
    public void onStart(@NonNull final PersistenceLayer persistenceLayer) {
        this.persistenceLayer = persistenceLayer;
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public void onNewMeasurement(long measurementId) {
        currentMeasurementIdentifier = measurementId;
    }

    @Override
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
            accelerationsFile = new Point3dFile(persistenceLayer.getContext(), measurementIdentifier,
                    Point3dFile.ACCELERATIONS_FOLDER_NAME, Point3dFile.ACCELERATIONS_FILE_EXTENSION);
        }
        if (rotationsFile == null) {
            rotationsFile = new Point3dFile(persistenceLayer.getContext(), measurementIdentifier,
                    Point3dFile.ROTATIONS_FOLDER_NAME, Point3dFile.ROTATION_FILE_EXTENSION);
        }
        if (directionsFile == null) {
            directionsFile = new Point3dFile(persistenceLayer.getContext(), measurementIdentifier,
                    Point3dFile.DIRECTIONS_FOLDER_NAME, Point3dFile.DIRECTION_FILE_EXTENSION);
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

        persistenceLayer.getResolver().insert(persistenceLayer.getGeoLocationsUri(), values);
    }

    /**
     * Loads the currently captured measurement and refreshes the {@link #currentMeasurementIdentifier} reference. This
     * method should only be called if capturing is active. It throws an error otherwise.
     *
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     *             use {@link PersistenceLayer#hasMeasurement(MeasurementStatus)} to check whether there is an actual
     *             {@link MeasurementStatus#OPEN} measurement.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private void refreshIdentifierOfCurrentlyCapturedMeasurement()
            throws NoSuchMeasurementException, CursorIsNullException {

        final Measurement measurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        currentMeasurementIdentifier = measurement.getIdentifier();
        Log.d(Constants.TAG, "Refreshed currentMeasurementIdentifier to: " + currentMeasurementIdentifier);
    }

    /**
     * Loads the current {@link Measurement} from the internal cache if possible, or from the persistence layer if an
     * {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} {@code Measurement} exists.
     *
     * @return The currently captured {@code Measurement}
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     *             {@link MeasurementStatus#OPEN} or {@link MeasurementStatus#PAUSED} {@code Measurement}
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @Override
    @NonNull
    public Measurement loadCurrentlyCapturedMeasurement() throws NoSuchMeasurementException, CursorIsNullException {
        synchronized (this) {
            if (currentMeasurementIdentifier == null && (persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
                    || persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED))) {
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
     * Update the {@link MeasurementStatus} of the currently active {@link Measurement}.
     *
     * @throws NoSuchMeasurementException When there was no currently captured {@code Measurement}.
     * @throws IllegalArgumentException When the {@param newStatus} was none of the supported:
     *             {@link MeasurementStatus#FINISHED}, {@link MeasurementStatus#PAUSED} or
     *             {@link MeasurementStatus#OPEN}.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public void updateRecentMeasurement(@NonNull final MeasurementStatus newStatus)
            throws NoSuchMeasurementException, CursorIsNullException {
        Validate.isTrue(
                newStatus == FINISHED || newStatus == MeasurementStatus.PAUSED || newStatus == MeasurementStatus.OPEN);

        final long currentlyCapturedMeasurementId = loadCurrentlyCapturedMeasurement().getIdentifier();
        switch (newStatus) {
            case OPEN:
                Validate.isTrue(persistenceLayer
                        .loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.PAUSED);
                break;
            case PAUSED:
                Validate.isTrue(persistenceLayer
                        .loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.OPEN);
                break;
            case FINISHED:
                Validate.isTrue(
                        persistenceLayer.loadMeasurementStatus(currentlyCapturedMeasurementId) == MeasurementStatus.OPEN
                                || persistenceLayer.loadMeasurementStatus(
                                        currentlyCapturedMeasurementId) == MeasurementStatus.PAUSED);
                break;
            default:
                throw new IllegalArgumentException("No supported newState: " + newStatus);
        }

        Log.d(TAG, "Updating recent measurement to: " + newStatus);
        synchronized (this) {
            try {
                persistenceLayer.setStatus(currentlyCapturedMeasurementId, newStatus);
            } finally {
                if (newStatus == FINISHED) {
                    currentMeasurementIdentifier = null;
                }
            }
        }
    }
}
