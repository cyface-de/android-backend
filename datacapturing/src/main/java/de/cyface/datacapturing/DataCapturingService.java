package de.cyface.datacapturing;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.NoSuchMeasurementException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.synchronization.CyfaceSyncAdapter;
import de.cyface.synchronization.SynchronisationException;
import de.cyface.synchronization.WiFiSurveyor;

/**
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * {@link #forceMeasurementSynchronisation(String)}.
 * <p>
 * An object of this class is not thread safe and should only be used once per application. You may start and stop the
 * service as often as you like and reuse the object.
 * <p>
 * If your app is suspended or shutdown, the service will continue running in the background. However you need to use
 * disconnect and reconnect as part of the <code>onStop</code> and the <code>onResume</code> method of your
 * <code>Activity</code> lifecycle.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public abstract class DataCapturingService {

    /**
     * Tag used to identify Logcat messages issued by instances of this class.
     */
    private static final String TAG = "de.cyface.capturing";
    /*
     * MARK: Properties
     */

    /**
     * {@code true} if data capturing is running; {@code false} otherwise.
     */
    private boolean isRunning = false;
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
     * A facade object providing access to the data stored by this <code>DataCapturingService</code>.
     */
    private final MeasurementPersistence persistenceLayer;
    /**
     * Messenger that handles messages arriving from the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger fromServiceMessenger;
    /**
     * Messenger used to send messages from this class to the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger toServiceMessenger;
    /**
     * This object observers the current WiFi state and starts and stops synchronization based on whether WiFi is active
     * or not. If the WiFi is active it should activate synchronization. If WiFi connectivity is lost it deactivates the
     * synchronization.
     */
    private final WiFiSurveyor surveyor;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @throws SetupException If writing the components preferences fails.
     */
    public DataCapturingService(final @NonNull Context context, final @NonNull String dataUploadServerAddress)
            throws SetupException {
        this.context = new WeakReference<>(context);

        this.serviceConnection = new BackgroundServiceConnection();
        this.persistenceLayer = new MeasurementPersistence(context.getContentResolver());

        // Setup required preferences including the device identifier, if not generated previously.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceIdentifier = preferences.getString(CyfaceSyncAdapter.DEVICE_IDENTIFIER_KEY, null);
        SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
        if (deviceIdentifier == null) {
            sharedPreferencesEditor.putString(CyfaceSyncAdapter.DEVICE_IDENTIFIER_KEY, UUID.randomUUID().toString());
        }
        sharedPreferencesEditor.putString(CyfaceSyncAdapter.SYNC_ENDPOINT_URL_SETTINGS_KEY, dataUploadServerAddress);
        if (!sharedPreferencesEditor.commit()) {
            throw new SetupException("Unable to write preferences!");
        }
        surveyor = new WiFiSurveyor(context);
    }

    /**
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running.
     *
     * @param listener A listener that is notified of important events during data capturing.
     * @throws DataCapturingException If the asynchronuous background service did not start successfully.
     */
    public void start(final @NonNull DataCapturingListener listener, final @NonNull Vehicle vehicle)
            throws DataCapturingException {
        if (context.get() == null) {
            return;
        }
        persistenceLayer.newMeasurement(vehicle);
        this.fromServiceMessenger = new Messenger(new FromServiceMessageHandler(context.get(), listener));

        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        ComponentName serviceComponentName = context.get().startService(startIntent);
        if (serviceComponentName == null) {
            throw new DataCapturingException("Illegal state: back ground service could not be started!");
        }

        bind();
    }

    /**
     * Stops the currently running data capturing process or does nothing if the process is not running.
     *
     * @throws DataCapturingException If service was not connected. The service will still be stopped if the exception
     *             occurs, but you have to handle it to prevent your application from crashing.
     */
    public void stop() throws DataCapturingException {
        if (context.get() == null) {
            return;
        }

        isRunning = false;
        try {
            unbind();
        } catch (IllegalArgumentException e) {
            throw new DataCapturingException(e);
        } finally {
            Intent stopIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            context.get().stopService(stopIntent);
            persistenceLayer.closeRecentMeasurement();
        }
    }

    /**
     * @return A list containing all measurements currently stored on this by this application. An empty list if there
     *         are no such measurements, but never <code>null</code>.
     */
    public @NonNull List<Measurement> getCachedMeasurements() {
        return persistenceLayer.loadMeasurements();
    }

    /**
     * Forces the service to synchronize all Measurements now if a connection is available. If this is not called the
     * service might wait for an opportune moment to start synchronization.
     *
     * @throws SynchronisationException If synchronisation account information is invalid or not available.
     */
    public void forceMeasurementSynchronisation(final @NonNull String username) throws SynchronisationException {
        Account account = getWiFiSurveyor().getOrCreateAccount(username);
        getWiFiSurveyor().scheduleSyncNow(account);
    }

    // TODO provide a custom list implementation that loads only small portions into memory.
    /**
     * Loads a track of geo locations for an existing {@link Measurement}. This method loads the complete track into
     * memory. For large tracks this could slow down the device or even reach the applications memory limit.
     *
     * @param measurement The <code>measurement</code> to load all the geo locations for.
     * @return The track associated with the measurement as a list of ordered (by timestamp) geo locations.
     */
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        return persistenceLayer.loadTrack(measurement);
    }

    /**
     * Deletes a {@link Measurement} from this device.
     *
     * @param measurement The {@link Measurement} to delete.
     */
    public void deleteMeasurement(final @NonNull Measurement measurement) {
        persistenceLayer.delete(measurement);
    }

    /**
     * This method checks for whether the service is currently running or not. Since this requires an asynchronous inter
     * process communication, it should be
     * considered a long running operation.
     *
     * @param timeout The timeout of how long to wait for the service to answer before deciding it is not running. After
     *            this timeout has passed the <code>IsRunningCallback#timedOut()</code> method is called. Since the
     *            communication between this class and its background service is usually quite fast (almost
     *            instantaneous), you may use pretty low values here. It still is a long running operation and should be
     *            handled as such in the UI.
     * @param unit The unit of time specified by timeout.
     * @param callback Called as soon as the current state of the service has become clear.
     */
    public void isRunning(final long timeout, final TimeUnit unit, final @NonNull IsRunningCallback callback) {
        final PongReceiver pongReceiver = new PongReceiver(getContext());
        pongReceiver.pongAndReceive(timeout, unit, callback);
    }

    /**
     * Disconnects your app from the <code>DataCapturingService</code>. Data capturing will continue in the background
     * but you will not receive any updates about this. This frees some resources used for communication and cleanly
     * shuts down the connection. You should call this method in your <code>Activity</code> lifecycle
     * <code>onStop</code>. You may call <code>reconnect</code> if you would like to receive updates again, like in your
     * <code>Activity</code> lifecycle <code>onRestart</code> method.
     *
     * @throws DataCapturingException If service was not connected.
     */
    public void disconnect() throws DataCapturingException {
        unbind();
    }

    /**
     * @return The current Android <code>Context</code> used by this service or <code>null</code> if there currently is
     *         none.
     */
    Context getContext() {
        return context.get();
    }

    /**
     * Binds this <code>DataCapturingService</code> facade to the underlying {@link DataCapturingBackgroundService}.
     *
     * @throws DataCapturingException If binding fails.
     */
    private void bind() throws DataCapturingException {
        if (context.get() == null) {
            throw new DataCapturingException("No valid context for binding!");
        }

        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        isRunning = context.get().bindService(startIntent, serviceConnection, 0);
        if (!isRunning) {
            throw new DataCapturingException("Illegal state: unable to bind to background service!");
        }
    }

    /**
     * Unbinds this <code>DataCapturingService</code> facade from the underlying {@link DataCapturingBackgroundService}.
     *
     * @throws DataCapturingException If no valid Android context is available.
     */
    private void unbind() throws DataCapturingException {
        if (context.get() == null) {
            throw new DataCapturingException("Context was null!");
        }

        context.get().unbindService(serviceConnection);
    }

    /**
     * Reconnects your app to the <code>DataCapturingService</code>. This might be especially useful if you have been
     * disconnected in a previous call to <code>onStop</code> in your <code>Activity</code> lifecycle.
     *
     * @throws DataCapturingException If rebinding to the background service fails.
     */
    public void reconnect() throws DataCapturingException {
        bind();
    }

    /**
     * Provides the <code>WiFiSurveyor</code> responsible for switching data synchronization on and off, based on WiFi
     * state.
     *
     * @return The currently active <code>WiFiSurveyor</code>.
     */
    WiFiSurveyor getWiFiSurveyor() {
        return surveyor;
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
        public void onServiceConnected(final @NonNull ComponentName componentName, final @NonNull IBinder binder) {
            Log.d(TAG, "DataCapturingService connected to background service.");
            toServiceMessenger = new Messenger(binder);
            Message registerClient = new Message();
            registerClient.replyTo = fromServiceMessenger;
            registerClient.what = MessageCodes.REGISTER_CLIENT;
            try {
                toServiceMessenger.send(registerClient);
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            }

            Log.d(TAG, "ServiceConnection established!");
        }

        @Override
        public void onServiceDisconnected(final @NonNull ComponentName componentName) {
            Log.d(TAG, "Service disconnected!");
            toServiceMessenger = null;

        }

        @Override
        public void onBindingDied(final @NonNull ComponentName name) {
            if (context.get() == null) {
                throw new IllegalStateException("Unable to rebind. Context was null.");
            }

            try {
                unbind();
            } catch (DataCapturingException e) {
                throw new IllegalStateException(e);
            }
            Intent rebindIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            context.get().bindService(rebindIntent, this, 0);
        }
    }

    /**
     * A handler for messages coming from the {@link DataCapturingBackgroundService}.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class FromServiceMessageHandler extends Handler {

        /**
         * A listener that is notified of important events during data capturing.
         */
        private final DataCapturingListener listener;

        /**
         * The Android context this handler is running under.
         */
        private final Context context;

        /**
         * Creates a new completely initialized <code>FromServiceMessageHandler</code>.
         *
         * @param listener A listener that is notified of important events during data capturing.
         */
        FromServiceMessageHandler(final @NonNull Context context, final @NonNull DataCapturingListener listener) {
            this.context = context;
            this.listener = listener;
        }

        @Override
        public void handleMessage(final @NonNull Message msg) {

            switch (msg.what) {
                case MessageCodes.LOCATION_CAPTURED:
                    Bundle dataBundle = msg.getData();
                    dataBundle.setClassLoader(getClass().getClassLoader());
                    GeoLocation location = dataBundle.getParcelable("data");
                    if (location == null) {
                        listener.onErrorState(
                                new DataCapturingException(context.getString(R.string.missing_data_error)));
                    } else {
                        listener.onNewGeoLocationAcquired(location);
                    }
                    break;
                case MessageCodes.DATA_CAPTURED:
                    Log.i(TAG, "Captured some sensor data, which is ignored for now.");
                    // TOD
                case MessageCodes.GPS_FIX:
                    listener.onFixAcquired();
                    break;
                case MessageCodes.NO_GPS_FIX:
                    listener.onFixLost();
                    break;
                case MessageCodes.WARNING_SPACE:
                    listener.onLowDiskSpace(null);
                    break;
                default:
                    listener.onErrorState(
                            new DataCapturingException(context.getString(R.string.unknown_message_error, msg.what)));

            }
        }
    }
}
