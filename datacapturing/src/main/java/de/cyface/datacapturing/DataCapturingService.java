package de.cyface.datacapturing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
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
import de.cyface.datacapturing.model.CapturedData;

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

    /*
     * MARK: Properties
     */

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
    /**
     * Messenger that handles messages arriving from the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger fromServiceMessenger;
    /**
     * Messenger used to send messages from this class to the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger toServiceMessenger;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     */
    public DataCapturingService(final Context context) {
        this.unsyncedMeasurements = new ArrayList<>();
        this.context = new WeakReference<Context>(context);
        this.serviceConnection = new BackgroundServiceConnection();
    }

    /**
     * Starts the capturing process. This operation is idempotent.
     */
    public void start() {
        start(null);

    }

    /**
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running. This operation is idempotent.
     *
     * @param listener A listener that is notified of important events during data capturing.
     */
    public void start(final DataCapturingListener listener) {
        if (context.get() == null) {
            return;
        }
        this.listener = listener;
        this.fromServiceMessenger = new Messenger(new FromServiceMessageHandler(listener));

        unsyncedMeasurements.add(new Measurement(unsyncedMeasurements.size()));
        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        ComponentName serviceComponentName = context.get().startService(startIntent);
        if (serviceComponentName == null) {
            throw new IllegalStateException("Illegal state: back ground service could not be started!");
        }
        isRunning = context.get().bindService(startIntent, serviceConnection, 0);
        if (!isRunning) {
            throw new IllegalStateException("Illegal state: unable to bind to background service!");
        }
    }

    /**
     * Stops the currently running data capturing process or does nothing if the process is not running.
     */
    public void stop() {
        if (context.get() == null) {
            return;
        }

        isRunning = false;
        try {
            unbind();
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
        Intent stopIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
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
     * Disconnects your app from the <code>DataCapturingService</code>. Data capturing will continue in the background
     * but you will not receive any updates about this. This frees some resources used for communication and cleanly
     * shuts down the connection. You should call this method in your <code>Activity</code> lifecycle
     * <code>onStop</code>. You may call <code>reconnect</code> if you would like to receive updates again, like in your
     * <code>Activity</code> lifecycle <code>onRestart</code> method.
     */
    public void disconnect() {
        try {
            unbind();
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
    }

    private void unbind() throws RemoteException {
        if (context.get() == null) {
            throw new IllegalStateException("Illegal state: context was null!");
        }

        Message msg = Message.obtain(null, MessageCodes.UNREGISTER_CLIENT);
        toServiceMessenger.send(msg);
        context.get().unbindService(serviceConnection);
    }

    /**
     * Reconnects your app to the <code>DataCapturingService</code>. This might be especially useful if you have been
     * disconnected in a previous call to <code>onStop</code> in your <code>Activity</code> lifecycle.
     */
    public void reconnect() {
        if (context.get() == null) {
            throw new IllegalStateException("Illegal state: context was null!");
        }

        Intent bindServiceIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        boolean bindingWasSuccessful = context.get().bindService(bindServiceIntent, serviceConnection, 0);
        if (!bindingWasSuccessful) {
            throw new IllegalStateException("Unable to reconnect to data capturing service!");
        }
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
            toServiceMessenger = new Messenger(binder);
            Message registerClient = new Message();
            registerClient.replyTo = fromServiceMessenger;
            registerClient.what = MessageCodes.REGISTER_CLIENT;
            try {
                toServiceMessenger.send(registerClient);
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            toServiceMessenger = null;

        }

        @Override
        public void onBindingDied(ComponentName name) {
            if (context.get() == null) {
                throw new IllegalStateException("Unable to rebind. Context was null.");
            }

            try {
                unbind();
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            }
            Intent rebindIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            context.get().bindService(rebindIntent, this, 0);
        }
    }

    private static class FromServiceMessageHandler extends Handler {

        private DataCapturingListener listener;

        public FromServiceMessageHandler(final DataCapturingListener listener) {
            if(listener==null) {
                throw new IllegalArgumentException("Illegal argument: listener was null!");
            }

            this.listener = listener;
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MessageCodes.POINT_CAPTURED:
                    Bundle dataBundle = msg.getData();
                    CapturedData data = dataBundle.getParcelable("data");
                    if(data==null) {
                        throw new IllegalStateException("Illegal state: captured data was missing from message!");
                    }

                    GpsPosition geoLocation = new GpsPosition(data.getLat(),data.getLon(),data.getGpsSpeed(),data.getGpsAccuracy());

                    listener.onNewGpsPositionAcquired(geoLocation);
                    break;
                case MessageCodes.GPS_FIX:
                    listener.onGpsFixAcquired();
                    break;
                case MessageCodes.NO_GPS_FIX:
                    listener.onGpsFixLost();
                    break;
                case MessageCodes.WARNING_SPACE:
                    listener.onLowDiskSpace(null);
                    break;
                default:
                    throw new IllegalStateException(
                            String.format("Received unknown message %d from data capturing service!", msg.what));

            }
        }
    }
}
