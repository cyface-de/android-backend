package de.cyface.datacapturing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;

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
    /**
     * A weak reference to the calling context. This is a weak reference since the calling context (i.e.
     * <code>Activity</code>) might have been destroyed, in which case there is no context anymore.
     */
    private final WeakReference<Context> context;
    /**
     * Connection used to communicate with the background service
     */
    private final ServiceConnection serviceConnection;
    private Messenger backgroundServiceMessenger;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     */
    public DataCapturingService(final Context context) {
        this.unsyncedMeasurements = new ArrayList<>();
        this.context = new WeakReference<Context>(context);
        this.serviceConnection = new BackgroundServiceConnection();
        this.backgroundServiceMessenger = new Messenger(new BackgroundServiceMessageHandler());
    }

    /**
     * Starts the capturing process. This operation is idempotent.
     */
    public void start() {
        if(context.get()==null) {
            return;
        }

        isRunning = true;
        unsyncedMeasurements.add(new Measurement(unsyncedMeasurements.size()));
        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        context.get().startService(startIntent);
        context.get().bindService(startIntent, serviceConnection,0);
    }

    /**
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running. This operation is idempotent.
     *
     * @param listener A listener that is notified of important events during data capturing.
     */
    public void start(final DataCapturingListener listener) {
        this.listener = listener;
        start();
    }

    /**
     * Stops the currently running data capturing process or does nothing if the process is not running.
     */
    public void stop() {
        if(context.get()==null) {
            return;
        }

        isRunning = false;
        context.get().unbindService(serviceConnection);
        Intent stopIntent = new Intent(context.get(),DataCapturingBackgroundService.class);
        context.get().stopService(stopIntent);
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

    /**
     * Handles the connection to a {@link DataCapturingBackgroundService}. For further information please refer to the
     * <a href="https://developer.android.com/guide/components/bound-services.html">Android documentation</a>.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private class BackgroundServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder binder) {
            backgroundServiceMessenger = new Messenger(binder);
            Message registerClient = new Message();
            registerClient.replyTo = backgroundServiceMessenger;
            registerClient.what = MessageCodes.REGISTER_CLIENT;
            try {
                backgroundServiceMessenger.send(registerClient);
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            backgroundServiceMessenger = null;

        }

        @Override
        public void onBindingDied(ComponentName name) {
            if(context.get()==null) {
                throw new IllegalStateException("Unable to rebind. Context was null.");
            }

            context.get().unbindService(this);
            Intent rebindIntent = new Intent(context.get(),DataCapturingBackgroundService.class);
            context.get().bindService(rebindIntent,this,0);
        }
    }

    private class BackgroundServiceMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }
}
