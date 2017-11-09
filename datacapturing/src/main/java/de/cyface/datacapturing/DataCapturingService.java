package de.cyface.datacapturing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * {@link #forceSyncUnsyncedMeasurements()}.
 * </p>
 * <p>
 * An object of this class is not thread safe and should only be used once per application. You may start and stop the
 * service as often as you like and reuse the object.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataCapturingService {

    /**
     * <p>
     * {@code true} if data capturing is running; {@code false} otherwise.
     * </p>
     */
    private boolean isRunning = false;
    /**
     * <p>
     * A listener that is notified of important events during data capturing.
     * </p>
     */
    private DataCapturingListener listener;
    /**
     * <p>
     * A poor mans data storage. This is only in memory and will be replaced by a database on persistent storage.
     * </p>
     */
    private final List<Measurement> unsyncedMeasurements;

    /**
     * <p>
     * Creates a new completely initialized {@link DataCapturingService}.
     * </p>
     */
    public DataCapturingService() {
        unsyncedMeasurements = new ArrayList<>();
    }

    /**
     * <p>
     * Starts the capturing process. This operation is idempotent.
     * </p>
     */
    public void start() {
        isRunning = true;
        unsyncedMeasurements.add(new Measurement(unsyncedMeasurements.size()));
    }

    /**
     * <p>
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running. This operation is idempotent.
     * </p>
     * 
     * @param listener A listener that is notified of important events during data capturing.
     */
    public void start(final DataCapturingListener listener) {
        this.listener = listener;
        start();
    }

    /**
     * <p>
     * Stops the currently running data capturing process or does nothing if the process is not running.
     * </p>
     */
    public void stop() {
        isRunning = false;
    }

    /**
     * @return A list containing the not yet synchronized measurements cached by this application.
     */
    public List<Measurement> getUnsyncedMeasurements() {
        return Collections.unmodifiableList(unsyncedMeasurements);
    }

    /**
     * <p>
     * Forces the service to synchronize all Measurements now if a connection is available. If this is not called the
     * service might wait for an opprotune moment to start synchronization.
     * </p>
     */
    public void forceSyncUnsyncedMeasurements() {
        unsyncedMeasurements.clear();
    }

    /**
     * <p>
     *     Deletes an unsynchronized {@link Measurement} from this device.
     * </p>
     * @param measurement The {@link Measurement} to delete.
     */
    public void deleteUnsyncedMeasurement(final Measurement measurement) {
        this.unsyncedMeasurements.remove(measurement);
    }

    /**
     * @return {@code true} if the data capturing service is running; {@code false} otherwise.
     */
    public boolean isRunning() {
        return isRunning;
    }
}
