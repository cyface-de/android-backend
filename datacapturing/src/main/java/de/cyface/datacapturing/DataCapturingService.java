package de.cyface.datacapturing;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.ContentResolver;
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
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.Vehicle;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.persistence.BuildConfig;
import de.cyface.synchronization.CyfaceSyncAdapter;

/**
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * {@link #forceSyncUnsyncedMeasurements()}.
 * <p>
 * An object of this class is not thread safe and should only be used once per application. You may start and stop the
 * service as often as you like and reuse the object.
 * <p>
 * If your app is suspended or shutdown, the service will continue running in the background. However you need to use
 * disconnect and reconnect as part of the <code>onStop</code> and the <code>onResume</code> method of your
 * <code>Activity</code> lifecycle.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class DataCapturingService {

    private static final String TAG = "de.cyface.capturing";
    private final static String ACCOUNT = "default_account";
    private final static String ACCOUNT_TYPE = "de.cyface";
    private final static String AUTHORITY = BuildConfig.provider;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SYNC_INTERVAL_IN_MINUTES = 60L;
    private static final long SYNC_INTERVAL = SYNC_INTERVAL_IN_MINUTES * SECONDS_PER_MINUTE;

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
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     */
    public DataCapturingService(final @NonNull Context context, final @NonNull String dataUploadServerAddress) {
        this.context = new WeakReference<Context>(context);

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
            throw new IllegalStateException("Unable to write preferences!");
        }
    }

    /**
     * Starts the capturing process with a listener that is notified of important events occuring while the capturing
     * process is running.
     *
     * @param listener A listener that is notified of important events during data capturing.
     */
    public void start(final @NonNull DataCapturingListener listener, final @NonNull Vehicle vehicle) {
        if (context.get() == null) {
            return;
        }
        persistenceLayer.newMeasurement(vehicle);
        this.fromServiceMessenger = new Messenger(new FromServiceMessageHandler(context.get(), listener));

        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        ComponentName serviceComponentName = context.get().startService(startIntent);
        if (serviceComponentName == null) {
            throw new IllegalStateException("Illegal state: back ground service could not be started!");
        }

        bind();
    }

    /**
     * Stops the currently running data capturing process or does nothing if the process is not running.
     *
     * @throws IllegalStateException If service was not connected. The service will still be stopped if the exception
     *             occurs, but you have to handle it to prevent your application from crashing.
     */
    public void stop() {
        if (context.get() == null) {
            return;
        }

        isRunning = false;
        try {
            unbind();
        } catch (RemoteException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        } finally {
            Intent stopIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            context.get().stopService(stopIntent);
            persistenceLayer.closeRecentMeasurement();
            // TODO schedule periodic sync after measurement has been finished.
            activateDataSynchronisation();
        }
    }

    /**
     * @return A list containing the not yet synchronized measurements cached by this application. An empty list if
     *         there are no such measurements, but never <code>null</code>.
     */
    public @NonNull List<Measurement> getUnsyncedMeasurements() {
        return persistenceLayer.loadMeasurements();
    }

    /**
     * Forces the service to synchronize all Measurements now if a connection is available. If this is not called the
     * service might wait for an opportune moment to start synchronization.
     */
    public void forceSyncUnsyncedMeasurements() {
        Account account = getAccount();
        ContentResolver.requestSync(account, AUTHORITY, Bundle.EMPTY);
    }

    /**
     * Deletes an unsynchronized {@link Measurement} from this device.
     *
     * @param measurement The {@link Measurement} to delete.
     */
    public void deleteUnsyncedMeasurement(final @NonNull Measurement measurement) {
        persistenceLayer.delete(measurement);
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
     *
     * @throws IllegalStateException If service was not connected.
     */
    public void disconnect() {
        try {
            unbind();
        } catch (RemoteException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        }
    }

    private void activateDataSynchronisation() {
        if (context.get() == null) {
            throw new IllegalStateException("No valid context to enable data synchronization!");
        }

        Account account = getAccount();

        boolean cyfaceAccountSyncIsEnabled = ContentResolver.getSyncAutomatically(account, AUTHORITY);
        boolean masterAccountSyncIsEnabled = ContentResolver.getMasterSyncAutomatically();

        if (cyfaceAccountSyncIsEnabled && masterAccountSyncIsEnabled) {
            ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, SYNC_INTERVAL);
        }
    }

    private Account getAccount() {
        AccountManager am = AccountManager.get(context.get());
        Account[] cyfaceAccounts = am.getAccountsByType(ACCOUNT_TYPE);
        if (cyfaceAccounts.length == 0) {
            synchronized (this) {
                Account newAccount = new Account(ACCOUNT_TYPE, ACCOUNT_TYPE);
                boolean newAccountAdded = am.addAccountExplicitly(newAccount, null, Bundle.EMPTY);
                if (!newAccountAdded) {
                    throw new IllegalStateException("Unable to add dummy account!");
                }
                ContentResolver.setIsSyncable(newAccount, AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(newAccount, AUTHORITY, true);
                return newAccount;
            }
        } else {
            return cyfaceAccounts[0];
        }
    }

    /**
     * Binds this <code>DataCapturingService</code> facade to the underlying {@link DataCapturingBackgroundService}.
     */
    private void bind() {
        if (context.get() == null) {
            throw new IllegalStateException("No valid context for binding!");
        }

        Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
        isRunning = context.get().bindService(startIntent, serviceConnection, 0);
        if (!isRunning) {
            throw new IllegalStateException("Illegal state: unable to bind to background service!");
        }
    }

    /**
     * Unbinds this <code>DataCapturingService</code> facade from the underlying {@link DataCapturingBackgroundService}.
     *
     * @throws RemoteException If <code>DataCapturingBackgroundService</code> was not bound previously or is not
     *             reachable.
     */
    private void unbind() throws RemoteException {
        if (context.get() == null) {
            throw new IllegalStateException("Context was null!");
        }
        context.get().unbindService(serviceConnection);
    }

    /**
     * Reconnects your app to the <code>DataCapturingService</code>. This might be especially useful if you have been
     * disconnected in a previous call to <code>onStop</code> in your <code>Activity</code> lifecycle.
     */
    public void reconnect() {
        bind();
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
            } catch (RemoteException e) {
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
                case MessageCodes.POINT_CAPTURED:
                    Bundle dataBundle = msg.getData();
                    dataBundle.setClassLoader(getClass().getClassLoader());
                    CapturedData data = dataBundle.getParcelable("data");
                    if (data == null) {
                        throw new IllegalStateException(context.getString(R.string.missing_data_error));
                    }

                    GeoLocation geoLocation = new GeoLocation(data.getLat(), data.getLon(), data.getGpsSpeed(),
                            data.getGpsAccuracy());

                    listener.onNewGeoLocationAcquired(geoLocation);
                    break;
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
                    throw new IllegalStateException(context.getString(R.string.unknown_message_error, msg.what));

            }
        }
    }
}
