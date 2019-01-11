package de.cyface.datacapturing;

import static de.cyface.datacapturing.BundlesExtrasCodes.ACCELERATION_POINT_COUNT;
import static de.cyface.datacapturing.BundlesExtrasCodes.DIRECTION_POINT_COUNT;
import static de.cyface.datacapturing.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.datacapturing.BundlesExtrasCodes.GEOLOCATION_COUNT;
import static de.cyface.datacapturing.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.datacapturing.BundlesExtrasCodes.ROTATION_POINT_COUNT;
import static de.cyface.datacapturing.BundlesExtrasCodes.STOPPED_SUCCESSFULLY;
import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.SharedConstants.DEVICE_IDENTIFIER_KEY;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.accounts.Account;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MetaFile;
import de.cyface.persistence.serialization.MetaFile.PointMetaData;
import de.cyface.synchronization.ConnectionStatusListener;
import de.cyface.synchronization.ConnectionStatusReceiver;
import de.cyface.synchronization.SyncService;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.synchronization.exceptions.SynchronisationException;
import de.cyface.utils.Validate;

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
 * @author Armin Schnabel
 * @version 9.0.0
 * @since 1.0.0
 */
public abstract class DataCapturingService {

    /*
     * MARK: Properties
     */

    /**
     * {@code true} if data capturing is running; {@code false} otherwise.
     */
    private boolean isRunning;

    /**
     * A flag indicating whether the background service is currently stopped or in the process of stopping. This flag is
     * used to prevent multiple lifecycle methods from interrupting a stop process or being called, while no service is
     * running.
     */
    private boolean isStoppingOrHasStopped;

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
    private final Messenger fromServiceMessenger;
    /**
     * <code>MessageHandler</code> receiving messages from the service via the <code>fromServiceMessenger</code>.
     */
    private final FromServiceMessageHandler fromServiceMessageHandler;
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
     * A listener for events which the UI might be interested in.
     */
    private UIListener uiListener;
    /**
     * The <code>ContentProvider</code> authority required to request a sync operation in the {@link WiFiSurveyor}.
     * You should use something world wide unique, like your domain, to avoid collisions between different apps using
     * the Cyface SDK.
     */
    private String authority;
    /**
     * Lock used to protect lifecycle events from each other. This for example prevents a reconnect to disturb a running
     * stop.
     */
    private final Lock lifecycleLock;
    /**
     * The identifier used to qualify measurements from this capturing service with the server receiving the
     * measurements. This needs to be world wide unique.
     */
    private final String deviceIdentifier;
    /**
     * A receiver for synchronization events.
     */
    private final ConnectionStatusReceiver connectionStatusReceiver;
    /**
     * The strategy used to respond to selected events triggered by this service.
     */
    private final EventHandlingStrategy eventHandlingStrategy;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param resolver The <code>ContentResolver</code> used to access the data layer.
     * @param authority The <code>ContentProvider</code> authority required to request a sync operation in the
     *            {@link WiFiSurveyor}.
     *            You should use something world wide unique, like your domain, to avoid collisions between different
     *            apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data with.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @throws SetupException If writing the components preferences fails.
     */
    public DataCapturingService(final @NonNull Context context, final @NonNull ContentResolver resolver,
            final @NonNull String authority, final @NonNull String accountType,
            final @NonNull String dataUploadServerAddress, final @NonNull EventHandlingStrategy eventHandlingStrategy)
            throws SetupException {
        this.context = new WeakReference<>(context);
        this.authority = authority;
        this.serviceConnection = new BackgroundServiceConnection();
        this.persistenceLayer = new MeasurementPersistence(context, authority);
        this.connectionStatusReceiver = new ConnectionStatusReceiver(context);
        this.eventHandlingStrategy = eventHandlingStrategy;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();

        // Setup required device identifier, if not generated previously
        this.deviceIdentifier = persistenceLayer.restoreOrCreateDeviceId();
        sharedPreferencesEditor.putString(DEVICE_IDENTIFIER_KEY, deviceIdentifier);
        Validate.notNull(deviceIdentifier,
                "Sync canceled: No installation identifier for this application set in its preferences.");
        // We cache the did in the preferences as it's hard or not possible to access the database
        // where it's persisted, e.g. in the getAuthToken method.

        sharedPreferencesEditor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, dataUploadServerAddress);
        if (!sharedPreferencesEditor.commit()) {
            throw new SetupException("Unable to write preferences!");
        }
        ConnectivityManager connectivityManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            throw new SetupException("Android connectivity manager is not available!");
        }
        surveyor = new WiFiSurveyor(context, connectivityManager, authority, accountType);
        this.fromServiceMessageHandler = new FromServiceMessageHandler(context, this);
        this.fromServiceMessenger = new Messenger(fromServiceMessageHandler);
        lifecycleLock = new ReentrantLock();
        setIsRunning(false);
        setIsStoppingOrHasStopped(false);
    }

    /**
     * Starts the capturing process with a listener, that is notified of important events occurring while the capturing
     * process is running.
     * <p>
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the {@link StartUpFinishedHandler} to receive
     * a callback, when the service has been started.
     * <p>
     * This method is thread safe to call.
     * <p>
     * ATTENTION: If there are errors while starting the service, your handler might never be called. You may need to
     * apply some timeout mechanism to not wait indefinitely.
     *
     * @param listener A listener that is notified of important events during data capturing.
     * @param vehicle The {@link Vehicle} used to capture this data. If you have no way to know which kind of
     *            <code>Vehicle</code> was used, just use {@link Vehicle#UNKNOWN}.
     * @throws DataCapturingException If the asynchronous background service did not start successfully.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     */
    public void start(final @NonNull DataCapturingListener listener, final @NonNull Vehicle vehicle,
            final @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException {
        Log.d(TAG, "Starting asynchronously and locking lifecycle!");
        lifecycleLock.lock();
        try {
            if (getIsRunning()) {
                Log.d(TAG, "DataCapturingService assumes that the service is running and thus returns.");
                return;
            }
            // This is necessary to allow the App using the SDK to reconnect and prevent it from reconnecting while
            // stopping the service.
            setIsStoppingOrHasStopped(false);

            // Ensure there are no dead measurements in the open measurement folder
            persistenceLayer.cleanCorruptedOpenMeasurements();

            // Ignore start command for paused measurements (wrong life-cycle call)
            if (persistenceLayer.hasOpenMeasurement()) {
                Log.w(TAG, "Ignoring start() as there is a paused measurement which needs to be resumed by resume().");
                return;
            }

            // Start new measurement
            Measurement measurement = prepareStart(listener, vehicle);
            runService(measurement, finishedHandler, new PointMetaData(0, 0, 0, 0));
        } finally {
            Log.v(TAG, "Unlocking lifecycle from asynchronous start.");
            lifecycleLock.unlock();
        }
    }

    /**
     * Stops the currently running data capturing process.
     * <p>
     * This is an asynchronous method. You should not assume that the service has been stopped after the method returns.
     * The provided <code>finishedHandler</code> is called after the <code>DataCapturingBackgroundService</code> has
     * successfully shutdown.
     * <p>
     * ATTENTION: It seems to be possible, that the service stopped signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     * <p>
     * This method is thread safe.
     *
     * @param finishedHandler A handler that gets called after the process of finishing the current measurement has
     *            completed.
     * @throws DataCapturingException If <code>DataCapturingBackgroundService</code> is not bound to this service.
     *             The service will still be stopped if the exception occurs, but you have to handle it anyways to
     *             prevent your application from crashing.
     * @throws NoSuchMeasurementException If no measurement was open while pausing the service. This usually occurs if
     *             there was no call to {@link #start(DataCapturingListener, Vehicle, StartUpFinishedHandler)}
     *             prior to pausing.
     */
    public void stop(final @NonNull ShutDownFinishedHandler finishedHandler)
            throws DataCapturingException, NoSuchMeasurementException {
        Log.d(TAG, "Stopping asynchronously!");
        if (getContext() == null) {
            return;
        }

        lifecycleLock.lock();
        Log.v(TAG, "Locking in asynchronous stop.");
        try {
            setIsStoppingOrHasStopped(true);
            Measurement measurement = persistenceLayer.loadCurrentlyCapturedMeasurement();

            if (measurement == null) {
                throw new NoSuchMeasurementException("Unable to stop service. There was no open measurement to close.");
            }

            if (!stopService(measurement, finishedHandler)) {
                // The background service was not active. This can happen when the measurement was paused.
                // Thus, no broadcast was sent to the ShutDownFinishedHandler, thus we do this here:
                sendServiceStoppedBroadcast(getContext(), measurement.getIdentifier(), false);
                // This can probably also happen when the DCBS died (exception) before or dies when
                // receiving this stop command. Thus, the closeRecentMeasurement() below would move it
                // to finish folder without the DCBS having completed the MetaFile. For this reason we
                // need to add another corruption test into the synchronization #MOV-453
            }
        } catch (IllegalArgumentException e) {
            throw new DataCapturingException(e);
        } finally {
            persistenceLayer.closeRecentMeasurement();
            Log.v(TAG, "Unlocking in asynchronous stop.");
            lifecycleLock.unlock();
        }
    }

    /**
     * Pauses the current data capturing, but does not finish the current measurement.
     * <p>
     * This is an asynchronous method. You should not assume that the service has been stopped after the method returns.
     * The provided <code>finishedHandler</code> is called after the <code>DataCapturingBackgroundService</code> has
     * successfully shutdown.
     * <p>
     * ATTENTION: It seems to be possible, that the service stopped signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     *
     * @param finishedHandler A handler that is called as soon as the background service has send a message that it has
     *            paused.
     * @throws DataCapturingException In case the service was not stopped successfully.
     * @throws NoSuchMeasurementException If no measurement was open while pausing the service. This usually occurs if
     *             there was no call to {@link #start(DataCapturingListener, Vehicle, StartUpFinishedHandler)}
     *             prior to pausing.
     */
    public void pause(final @NonNull ShutDownFinishedHandler finishedHandler)
            throws DataCapturingException, NoSuchMeasurementException {
        Log.d(TAG, "Pausing asynchronously.");
        Measurement currentMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement();

        if (currentMeasurement == null) {
            throw new NoSuchMeasurementException(
                    "There seems to be no open measurement, we can pause the capturing for.");
        }

        if (!stopService(currentMeasurement, finishedHandler)) {
            throw new DataCapturingException("No active service found to be paused.");
        }
    }

    /**
     * Resumes the current data capturing after a previous call to {@link #pause(ShutDownFinishedHandler)}.
     * <p>
     * This is an asynchronous method. You should not assume that the service has been resumed after the method returns.
     * The provided <code>finishedHandler</code> is called after the <code>DataCapturingBackgroundService</code> has
     * successfully resumed.
     * <p>
     * ATTENTION: It seems to be possible, that the service started signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     *
     * @param finishedHandler A handler that is called as soon as the background service sends a message that the
     *            background service has resumed successfully.
     * @throws DataCapturingException If starting the background service was not successful.
     * @throws MissingPermissionException If permission to access geo location via satellite has not been granted or
     *             revoked. The current measurement is closed if you receive this <code>Exception</code>. If you get the
     *             permission in the future you need to start a new measurement and not call <code>resumeSync</code>
     *             again.
     */
    public void resume(final @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException {

        if (getIsRunning()) {
            Log.w(TAG, "Ignoring duplicate resume call because service is already running");
            return;
        }

        Log.d(TAG, "Resume asynchronously.");
        if (!checkFineLocationAccess(getContext())) {
            try {
                persistenceLayer.closeRecentMeasurement();
            } catch (NoSuchMeasurementException e) {
                throw new IllegalStateException(e);
            }
            throw new MissingPermissionException();
        }

        // Ensure there are no dead measurements in the open measurement folder
        persistenceLayer.cleanCorruptedOpenMeasurements();

        // Ignore pause command if there are no paused measurements (wrong life-cycle call)
        if (!persistenceLayer.hasOpenMeasurement()) {
            Log.w(TAG,
                    "Ignoring resume() as there is no paused measurement. A start() would be expected by the life-cycle.");
            return;
        }

        // Resume paused measurement
        final Measurement currentlyOpenMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        Validate.notNull(currentlyOpenMeasurement);
        final MetaFile.MetaData metaData = MetaFile.resume(this.getContext(), currentlyOpenMeasurement.getIdentifier());
        runService(currentlyOpenMeasurement, finishedHandler, metaData.getPointMetaData());
    }

    // TODO: For at least the following two methods -> rename to load or remove completely from this class and expose
    // the interface of PersistenceLayer (like on iOS). *renames to load* for now. All FIXME and TODOs should be taken
    // care #MOV-459
    /**
     * Returns ALL measurements currently on this device. This includes currently running ones as well as paused and
     * finished measurements.
     *
     * @return A list containing all measurements currently stored on this device by this application. An empty list if
     *         there are no such measurements, but never <code>null</code>.
     */
    public @NonNull List<Measurement> loadMeasurements() {
        return persistenceLayer.loadMeasurements();
    }

    /**
     * @return A list containing all the finished (i.e. not running and not paused) but not yet uploaded measurements on
     *         this device. An empty list if there are no such measurements, but never <code>null</code>.
     */
    public @NonNull List<Measurement> loadFinishedMeasurements() {
        return persistenceLayer.loadFinishedMeasurements();
    }

    /**
     * Returns open measurements currently on this device.
     *
     * @return A list containing all open measurements currently stored on this device by this application. An empty
     *         list if
     *         there are no such measurements, but never <code>null</code>.
     */
    public @NonNull List<Measurement> loadOpenMeasurements() {
        return persistenceLayer.loadOpenMeasurements();
    }

    /**
     * @return The identifier used to qualify measurements from this capturing service with the server receiving the
     *         measurements. This needs to be world wide unique.
     */
    public @NonNull String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    /**
     * @param measurementIdentifier The identifier of the measurement to load.
     * @return The measurement corresponding to the provided <code>measurementIdentifier</code> or <code>null</code> if
     *         no such measurement exists.
     * @throws DataCapturingException If accessing the data storage fails.
     *             /
     *             public Measurement loadMeasurement(final long measurementIdentifier) throws DataCapturingException {
     *             return persistenceLayer.loadMeasurement(measurementIdentifier);
     *             }
     */

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

    /**
     * Loads a track of geo locations for an existing {@link Measurement}. This method loads the complete track into
     * memory. For large tracks this could slow down the device or even reach the applications memory limit.
     *
     * @param measurement The <code>measurement</code> to load all the geo locations for.
     * @return The track associated with the measurement as a list of ordered (by timestamp) geo locations.
     */
    @SuppressWarnings("unused") // Because we need to support this interface for the Movebis project
    public List<GeoLocation> loadTrack(final @NonNull Measurement measurement) throws NoSuchMeasurementException {
        return persistenceLayer.loadTrack(measurement);
    }

    /**
     * Deletes a {@link Measurement} from this device.
     *
     * @param measurement The {@link Measurement} to delete.
     *
     *            TODO [MOV-487]: do we still need to offer this interface or can we delete the method?
     *
     * @throws NoSuchMeasurementException If the provided measurement was <code>null</code> due to some unknown reasons.
     *             This is an API violation. You are not supposed to provide <code>null</code> measurements to this
     *             method.
     *             /
     *             public void deleteMeasurement(final @NonNull Measurement measurement) throws
     *             NoSuchMeasurementException {
     *             persistenceLayer.delete(measurement);
     *             }
     */

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
        Log.d(TAG, "Checking isRunning?");
        final PongReceiver pongReceiver = new PongReceiver(getContext());
        pongReceiver.checkIsRunningAsync(timeout, unit, callback);
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

    /*
     * TODO: This should probably throw an exception if reconnect was not possible. But we can do this only for the next
     * version release.
     */
    /**
     * Reconnects your app to the <code>DataCapturingService</code>. This might be especially useful if you have been
     * disconnected in a previous call to <code>onStop</code> in your <code>Activity</code> lifecycle.
     * <p>
     * <b>ATTENTION</b>: This method might take some time to check for a running service. Always consider this to be a
     * long running operation and never call it on the main thread.
     *
     * @throws DataCapturingException If communication with background service is not successful.
     */
    public void reconnect() throws DataCapturingException {

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();

        ReconnectCallback reconnectCallback = new ReconnectCallback(lock, condition) {
            @Override
            public void onSuccess() {
                try {
                    Log.v(TAG, "ReconnectCallback.onSuccess(): Binding to service!");
                    bind();
                } catch (DataCapturingException e) {
                    throw new IllegalStateException("Illegal state: unable to bind to background service!");
                }
            }
        };

        // TODO: Maybe move the timeout time to a parameter.
        try {
            isRunning(500L, TimeUnit.MILLISECONDS, reconnectCallback);
        } catch (IllegalStateException e) {
            throw new DataCapturingException(e);
        }

        // Wait for isRunning to return.
        lock.lock();
        try {
            Log.v(TAG, "DataCapturingService.reconnect(): Waiting for condition on isRunning!");
            if (!condition.await(500L, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "DataCapturingService.reconnect(): Waiting for isRunning timed out!");
            }
        } catch (InterruptedException e) {
            throw new DataCapturingException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param uiListener A listener for events which the UI might be interested in.
     */
    public void setUiListener(final @NonNull UIListener uiListener) {
        this.uiListener = uiListener;
    }

    /**
     * @return A listener for events which the UI might be interested in. This might be <code>null</code> if there has
     *         been no previous call to {@link #setUiListener(UIListener)}.
     */
    UIListener getUiListener() {
        return uiListener;
    }

    /**
     * @return The current Android <code>Context</code> used by this service or <code>null</code> if there currently is
     *         none.
     */
    Context getContext() {
        return context.get();
    }

    /**
     * Starts the associated {@link DataCapturingBackgroundService} and calls the provided
     * <code>startedMessageReceiver</code>, after it successfully started.
     *
     * @param measurement The measurement to store the captured data to.
     * @param startUpFinishedHandler A handler called if the service started successfully.
     * @param metaData The {@link PointMetaData} from when your paused the measurement. Required to continue the
     *            counters. If you do not resume but start a new measurement pass a new PointMetaData(0, 0, 0, 0).
     * @throws DataCapturingException If service could not be started.
     */
    private synchronized void runService(final Measurement measurement,
            final @NonNull StartUpFinishedHandler startUpFinishedHandler, PointMetaData metaData)
            throws DataCapturingException {
        final Context context = getContext();
        Log.v(TAG, "Registering startUpFinishedHandler as broadcast receiver.");
        context.registerReceiver(startUpFinishedHandler,
                new IntentFilter(MessageCodes.getServiceStartedActionId(context)));

        Log.d(TAG, "Starting the background service for measurement " + measurement + "!");
        final Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(MEASUREMENT_ID, measurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, authority);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, eventHandlingStrategy);
        startIntent.putExtra(GEOLOCATION_COUNT, metaData.getCountOfGeoLocations());
        startIntent.putExtra(ACCELERATION_POINT_COUNT, metaData.getCountOfAccelerations());
        startIntent.putExtra(ROTATION_POINT_COUNT, metaData.getCountOfRotations());
        startIntent.putExtra(DIRECTION_POINT_COUNT, metaData.getCountOfDirections());

        final ComponentName serviceComponentName;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            serviceComponentName = context.startForegroundService(startIntent);
        } else {
            serviceComponentName = context.startService(startIntent);
        }
        if (serviceComponentName == null) {
            throw new DataCapturingException("DataCapturingBackgroundService failed to start!");
        }
        bind();
    }

    /**
     * Stops the running <code>DataCapturingBackgroundService</code>, calling the provided <code>finishedHandler</code>
     * after successful execution.
     *
     * @param measurement The measurement that is currently captured.
     * @param finishedHandler The handler to call after receiving the stop message from the
     *            <code>DataCapturingBackgroundService</code>. There are some cases where this never happens, so be
     *            careful when using this method.
     * @return True if there was a service running which was stopped
     * @throws DataCapturingException In case the service was not stopped successfully.
     */
    private boolean stopService(final @NonNull Measurement measurement,
            final @NonNull ShutDownFinishedHandler finishedHandler) throws DataCapturingException {
        Log.d(TAG, "Stopping the background service.");
        setIsRunning(false);
        final Context context = getContext();
        Log.v(TAG, "Registering finishedHandler for service stop synchronization broadcast.");
        LocalBroadcastManager.getInstance(context).registerReceiver(finishedHandler,
                new IntentFilter(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED));

        boolean serviceWasActive;
        try {
            // For some reasons we have to call the unbind here.
            // We tried to send the stopIntent to the BackgroundService first which calls it's onDestroy
            // method which sends an SERVICE_STOPPED message back to this service where we executed
            // the unbind method. This should have worked for both - stopping the BackgroundService from
            // this service and by itself (via eventHandlerStrategyImpl.handleSpaceWarning())
            unbind();
        } catch (IllegalArgumentException e) {
            throw new DataCapturingException(e);
        } catch (DataCapturingException e) {
            Log.w(TAG, "Service was either paused or already stopped, so I was unable to unbind from it.");
        } finally {
            Log.v(TAG, String.format("Stopping using Intent with context %s", context));
            Intent stopIntent = new Intent(context, DataCapturingBackgroundService.class);
            serviceWasActive = context.stopService(stopIntent);
        }

        return serviceWasActive;
    }

    /**
     * This message is sent to the {@link ShutDownFinishedHandler} to inform callers that the async stop
     * command was executed.
     *
     * @param context The {@link Context} used to send the broadcast from
     * @param measurementIdentifier The id of the stopped measurement if {@code #stoppedSuccessfully}
     * @param stoppedSuccessfully True if the background service was still alive before stopped
     */
    private void sendServiceStoppedBroadcast(final Context context, final long measurementIdentifier,
            final boolean stoppedSuccessfully) {
        final Intent stoppedBroadcastIntent = new Intent(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED);
        if (stoppedSuccessfully) {
            Validate.isTrue(measurementIdentifier > 0L);
            stoppedBroadcastIntent.putExtra(MEASUREMENT_ID, measurementIdentifier);
        }
        stoppedBroadcastIntent.putExtra(STOPPED_SUCCESSFULLY, stoppedSuccessfully);
        LocalBroadcastManager.getInstance(context).sendBroadcast(stoppedBroadcastIntent);
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
     * Checks whether the user has granted the <code>ACCESS_FINE_LOCATION</code> permission and notifies the UI to ask
     * for it if not.
     *
     * @param context Current <code>Activity</code> context.
     * @return Either <code>true</code> if permission was or has been granted; <code>false</code> otherwise.
     */
    boolean checkFineLocationAccess(final @NonNull Context context) {
        boolean permissionAlreadyGranted = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permissionAlreadyGranted && uiListener != null) {
            return uiListener.onRequirePermission(Manifest.permission.ACCESS_FINE_LOCATION, new Reason(
                    "This app uses GPS sensors to display your position. If you would like your position to be shown as exactly as possible please allow access to the GPS sensors."));
        } else {
            return permissionAlreadyGranted;
        }
    }

    /**
     * Prepares for starting the background service, by checking for the system to be in the correct state, creating a
     * new {@link Measurement} and initializing the message handler for messages from the data capturing background
     * service.
     *
     * @param listener The <code>DataCapturingListener</code> receiving events during data capturing.
     * @param vehicle The type of vehicle this method is called for. If you do not know which vehicle was used you might
     *            use {@link Vehicle#UNKNOWN}.
     * @return The prepared measurement, which is ready to receive data.
     * @throws DataCapturingException If this object has no valid Android <code>Context</code>.
     * @throws MissingPermissionException If permission <code>ACCESS_FINE_LOCATION</code> has not been granted or
     *             revoked.
     */
    private Measurement prepareStart(final @NonNull DataCapturingListener listener, final @NonNull Vehicle vehicle)
            throws DataCapturingException, MissingPermissionException {
        if (context.get() == null) {
            throw new DataCapturingException("No context to start service!");
        }
        if (!checkFineLocationAccess(getContext())) {
            throw new MissingPermissionException();
        }
        Validate.isTrue(!persistenceLayer.hasOpenMeasurement());
        final Measurement measurement = persistenceLayer.newMeasurement(vehicle);
        fromServiceMessageHandler.addListener(listener);
        return measurement;
    }

    /**
     * @return A facade object providing access to the data stored by this <code>DataCapturingService</code>.
     */
    private MeasurementPersistence getPersistenceLayer() {
        return persistenceLayer;
    }

    /**
     * Binds this <code>DataCapturingService</code> facade to the underlying {@link DataCapturingBackgroundService}.
     *
     * @return <code>true</code> if successfully bound to a running service; <code>false</code> otherwise.
     * @throws DataCapturingException If binding fails.
     */
    private boolean bind() throws DataCapturingException {
        if (context.get() == null) {
            throw new DataCapturingException("No valid context for binding!");
        }

        // This must not be interrupted or interrupt a call to stop the service.
        lifecycleLock.lock();
        Log.v(TAG, "Locking bind.");
        try {
            if (getIsStoppingOrHasStopped()) {
                return false;
            }

            Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            boolean ret = context.get().bindService(startIntent, serviceConnection, 0);
            setIsRunning(ret);
            return ret;
        } finally {
            Log.v(TAG, "Unlocking bind.");
            lifecycleLock.unlock();
        }
    }

    /**
     * Unbinds this <code>DataCapturingService</code> facade from the underlying {@link DataCapturingBackgroundService}.
     *
     * @throws DataCapturingException If no valid Android context is available or the service was not running.
     */
    private void unbind() throws DataCapturingException {
        if (context.get() == null) {
            throw new DataCapturingException("Context was null!");
        }

        try {
            context.get().unbindService(serviceConnection);
        } catch (IllegalArgumentException e) {
            throw new DataCapturingException(e);
        }
    }

    /**
     * @param isRunning {@code true} if data capturing is running; {@code false} otherwise.
     */
    private void setIsRunning(final boolean isRunning) {
        Log.d(TAG, "Setting isRunning to " + isRunning);
        this.isRunning = isRunning;
    }

    /**
     * @return {@code true} if data capturing is running; {@code false} otherwise.
     */
    public boolean getIsRunning() {
        Log.d(TAG, "Getting isRunning with value " + isRunning);
        return isRunning;
    }

    /**
     * @param isStoppingOrHasStopped A flag indicating whether the background service is currently stopped or in the
     *            process of stopping. This flag is used to prevent multiple lifecycle method from interrupting a stop
     *            process or being called, while no service is running.
     */
    private void setIsStoppingOrHasStopped(final boolean isStoppingOrHasStopped) {
        Log.d(TAG, "Setting isStoppingOrHasStopped to " + isStoppingOrHasStopped);
        this.isStoppingOrHasStopped = isStoppingOrHasStopped;
    }

    /**
     * @return A flag indicating whether the background service is currently stopped or in the process of stopping. This
     *         flag is used to prevent multiple lifecycle method from interrupting a stop process or being called, while
     *         no service is running.
     */
    private boolean getIsStoppingOrHasStopped() {
        Log.d(TAG, "Getting isStoppingOrHasStopped with value " + isStoppingOrHasStopped);
        return isStoppingOrHasStopped;
    }

    /**
     * Adds a new listener interested in events from the synchronization service.
     *
     * @param listener A listener that is notified of important events during synchronization.
     */
    public void addConnectionStatusListener(final @NonNull ConnectionStatusListener listener) {
        this.connectionStatusReceiver.addListener(listener);
    }

    /**
     * Removes the provided object as <code>ConnectionStatusListener</code> from the list of listeners notified by this
     * object.
     *
     * @param listener A listener that is notified of important events during synchronization.
     */
    public void removeConnectionStatusListener(final @NonNull ConnectionStatusListener listener) {
        this.connectionStatusReceiver.removeListener(listener);
    }

    /**
     * Unregisters the {@link ConnectionStatusReceiver} when no more needed.
     */
    public void shutdownConnectionStatusReceiver() {
        this.connectionStatusReceiver.shutdown(getContext());
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

            Log.d(TAG, "Binding died, unbinding & rebinding ...");
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
        private Collection<DataCapturingListener> listener;

        /**
         * The Android context this handler is running under.
         */
        private final Context context;

        /**
         * The service which calls this handler.
         */
        private final DataCapturingService dataCapturingService;

        /**
         * Creates a new completely initialized <code>FromServiceMessageHandler</code>.
         */
        FromServiceMessageHandler(final @NonNull Context context,
                @NonNull final DataCapturingService dataCapturingService) {
            this.context = context;
            this.listener = new HashSet<>();
            this.dataCapturingService = dataCapturingService;
        }

        @Override
        public void handleMessage(final @NonNull Message msg) {
            Log.v(TAG, String.format("Service facade received message: %d", msg.what));

            for (final DataCapturingListener listener : this.listener) {
                final Bundle parcel;
                switch (msg.what) {
                    case MessageCodes.LOCATION_CAPTURED:
                        parcel = msg.getData();
                        parcel.setClassLoader(getClass().getClassLoader());
                        final GeoLocation location = parcel.getParcelable("data");
                        if (location == null) {
                            listener.onErrorState(
                                    new DataCapturingException(context.getString(R.string.missing_data_error)));
                        } else {
                            listener.onNewGeoLocationAcquired(location);
                        }
                        break;
                    case MessageCodes.DATA_CAPTURED:
                        parcel = msg.getData();
                        parcel.setClassLoader(getClass().getClassLoader());
                        CapturedData capturedData = parcel.getParcelable("data");
                        if (capturedData == null) {
                            listener.onErrorState(
                                    new DataCapturingException(context.getString(R.string.missing_data_error)));
                        } else {
                            Log.v(TAG, "Captured some sensor data.");
                            listener.onNewSensorDataAcquired(capturedData);
                        }
                        break;
                    case MessageCodes.GPS_FIX:
                        listener.onFixAcquired();
                        break;
                    case MessageCodes.NO_GPS_FIX:
                        listener.onFixLost();
                        break;
                    case MessageCodes.ERROR_PERMISSION:
                        listener.onRequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION, new Reason(
                                "Data capturing requires permission to access geo location via satellite. Was not granted or revoked!"));
                        break;
                    case MessageCodes.SERVICE_STOPPED:
                        parcel = msg.getData();
                        parcel.setClassLoader(getClass().getClassLoader());
                        // Due to the <code>DataCapturingBackgroundService#informCaller()</code> interface
                        // the bundle is bundled twice for re-usability
                        final Bundle stoppedInfoBundle = parcel.getParcelable("data");
                        Validate.notNull(stoppedInfoBundle);
                        final long measurementIdentifier = stoppedInfoBundle.getLong(MEASUREMENT_ID);
                        final boolean stoppedSuccessfully = stoppedInfoBundle.getBoolean(STOPPED_SUCCESSFULLY);
                        // Success means the background service was still alive. As this is the private
                        // IPC to the background service this must always be true.
                        Validate.isTrue(stoppedSuccessfully);

                        // Inform interested parties
                        dataCapturingService.sendServiceStoppedBroadcast(context, measurementIdentifier, true);
                        listener.onCapturingStopped();
                        break;
                    case MessageCodes.SERVICE_STOPPED_ITSELF:
                        // Attention: This method is very rarely executed and so be careful when you change it's logic.
                        // The task for the missing test is CY-4111. Currently only tested manually.
                        parcel = msg.getData();
                        parcel.setClassLoader(getClass().getClassLoader());
                        // Due to the <code>DataCapturingBackgroundService#informCaller()</code> interface
                        // the bundle is bundled twice for re-usability
                        final Bundle stoppedItselfInfoBundle = parcel.getParcelable("data");
                        Validate.notNull(stoppedItselfInfoBundle);
                        final long measurementId = stoppedItselfInfoBundle.getLong(MEASUREMENT_ID);

                        final Lock lock = new ReentrantLock();
                        final Condition condition = lock.newCondition();
                        final StopSynchronizer synchronizationReceiver = new StopSynchronizer(lock, condition);
                        try {
                            // The background service already received a stopSelf command but as it's still
                            // bound to this service it should be still alive. We unbind it from this service via the
                            // stopService method (to reduce code duplicity).
                            Validate.isTrue(dataCapturingService.stopService(new Measurement(measurementId),
                                    synchronizationReceiver));

                            // Thus, no broadcast was sent to the ShutDownFinishedHandler, so we do this here:
                            dataCapturingService.sendServiceStoppedBroadcast(context, measurementId, false);
                        } catch (final DataCapturingException e) {
                            throw new IllegalStateException(e);
                        }
                        break;
                    default:
                        listener.onErrorState(new DataCapturingException(
                                context.getString(R.string.unknown_message_error, msg.what)));

                }
            }
        }

        /**
         * Adds a new listener interested in events from the background service.
         *
         * @param listener A listener that is notified of important events during data capturing.
         */
        void addListener(final @NonNull DataCapturingListener listener) {
            this.listener.add(listener);
        }

        /**
         * Removes the provided object as <code>DataCapturingListener</code> from the list of listeners notified by this
         * object.
         *
         * @param listener A listener that is notified of important events during data capturing.
         */
        void removeListener(final @NonNull DataCapturingListener listener) {
            this.listener.remove(listener);
        }
    }
}
