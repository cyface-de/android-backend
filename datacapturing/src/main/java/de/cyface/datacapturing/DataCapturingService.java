package de.cyface.datacapturing;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.cyface.datacapturing.de.cyface.datacapturing.backend.DataCapturingBackgroundService;

/**
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * {@link #forceSyncUnsyncedMeasurements()}.
 * <p>
 * An object of this class is not thread safe and should only be used once per application. You may start and stop the
 * service as often as you like and reuse the object.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataCapturingService {

    /**
     * {@code true} if data capturing is running; {@code false} otherwise.
     */
    private boolean isRunning = false;
    /**
     * A listener that is notified of important events during data capturing.
     */
    private DataCapturingListener listener;
    /**
     * A poor mans data storage. This is only in memory and will be replaced by a database on persistent storage.
     */
    private final List<Measurement> unsyncedMeasurements;
    private final Context context;
    private ServiceConnection serviceConnection;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context
     */
    public DataCapturingService(final Context context) {
        unsyncedMeasurements = new ArrayList<>();
        this.context = context;
    }

    /**
     * Starts the capturing process. This operation is idempotent.
     */
    public void start() {
        isRunning = true;
        unsyncedMeasurements.add(new Measurement(unsyncedMeasurements.size()));
        Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        context.startService(startIntent);
        context.bindService(startIntent,serviceConnection);
    }

    /**
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running. This operation is idempotent.
     *
     * @param  context
     * @param listener A listener that is notified of important events during data capturing.
     */
    public void start(final Context context, final DataCapturingListener listener) {
        this.listener = listener;
        start();
    }

    /**
     * Stops the currently running data capturing process or does nothing if the process is not running.
     */
    public void stop() {
        isRunning = false;
        context.unbindService();
        context.stopService();
    }

    /**
     * @return A list containing the not yet synchronized measurements cached by this application.
     */
    public List<Measurement> getUnsyncedMeasurements() {
        return Collections.unmodifiableList(unsyncedMeasurements);
    }

    /**
     * Forces the service to synchronize all Measurements now if a connection is available. If this is not called the
     * service might wait for an opprotune moment to start synchronization.
     */
    public void forceSyncUnsyncedMeasurements() {
        unsyncedMeasurements.clear();
    }

    /**
     * Deletes an unsynchronized {@link Measurement} from this device.
     *
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
