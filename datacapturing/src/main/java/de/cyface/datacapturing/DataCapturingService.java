/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.PAUSED;
import static de.cyface.synchronization.BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.STOPPED_SUCCESSFULLY;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentProvider;
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
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Vehicle;
import de.cyface.synchronization.BundlesExtrasCodes;
import de.cyface.synchronization.ConnectionStatusListener;
import de.cyface.synchronization.ConnectionStatusReceiver;
import de.cyface.synchronization.SyncService;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * {@link #scheduleSyncNow()} ()}.
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
 * @version 14.0.0
 * @since 1.0.0
 */
public abstract class DataCapturingService {

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
    protected final PersistenceLayer<CapturingPersistenceBehaviour> persistenceLayer;
    /**
     * Messenger that handles messages arriving from the <code>DataCapturingBackgroundService</code>.
     */
    private final Messenger fromServiceMessenger;
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
     * The strategy used to calculate the {@code Measurement#distance} from {@link GeoLocation} pairs
     */
    private final DistanceCalculationStrategy distanceCalculationStrategy;
    /**
     * The number of ms to wait for the callback, see {@link #isRunning(long, TimeUnit, IsRunningCallback)}.
     */
    public final static long IS_RUNNING_CALLBACK_TIMEOUT = 500L;

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param authority The <code>ContentProvider</code> authority required to request a sync operation in the
     *            {@link WiFiSurveyor}. You should use something world wide unique, like your domain, to avoid
     *            collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data with.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param persistenceLayer The {@link PersistenceLayer} required to access the device id
     * @param distanceCalculationStrategy The {@link DistanceCalculationStrategy} used to calculate the
     *            {@link Measurement#distance}
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @throws SetupException If writing the components preferences fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    public DataCapturingService(@NonNull final Context context, @NonNull final String authority,
            @NonNull final String accountType, @NonNull final String dataUploadServerAddress,
            @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final PersistenceLayer<CapturingPersistenceBehaviour> persistenceLayer,
            @NonNull final DistanceCalculationStrategy distanceCalculationStrategy,
            @NonNull final DataCapturingListener capturingListener) throws SetupException, CursorIsNullException {
        this.context = new WeakReference<>(context);
        this.authority = authority;
        this.persistenceLayer = persistenceLayer;
        this.serviceConnection = new BackgroundServiceConnection();
        this.connectionStatusReceiver = new ConnectionStatusReceiver(context);
        this.eventHandlingStrategy = eventHandlingStrategy;
        this.distanceCalculationStrategy = distanceCalculationStrategy;

        // Setup required device identifier, if not already existent
        this.deviceIdentifier = persistenceLayer.restoreOrCreateDeviceId();
        Validate.notNull(deviceIdentifier,
                "Sync canceled: No installation identifier for this application set in its preferences.");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
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
        final FromServiceMessageHandler fromServiceMessageHandler = new FromServiceMessageHandler(context, this);
        // The listeners are automatically removed when the service is destroyed (e.g. app kill)
        fromServiceMessageHandler.addListener(capturingListener);
        this.fromServiceMessenger = new Messenger(fromServiceMessageHandler);
        lifecycleLock = new ReentrantLock();
        setIsRunning(false);
        setIsStoppingOrHasStopped(false);
    }

    /**
     * Starts the capturing process with a {@link DataCapturingListener}, that is notified of important events occurring
     * while the capturing process is running.
     * <p>
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the {@link StartUpFinishedHandler} to receive
     * a callback, when the service has been started.
     * <p>
     * This method is thread safe to call.
     * <p>
     * <b>ATTENTION:</b> If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     *
     * @param vehicle The {@link Vehicle} used to capture this data. If you have no way to know which kind of
     *            <code>Vehicle</code> was used, just use {@link Vehicle#UNKNOWN}.
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     *             Android context was available.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     * @throws CorruptedMeasurementException when there are unfinished, dead measurements.
     */
    // This life-cycle method is called by sdk implementing apps (e.g. SR)
    public void start(final @NonNull Vehicle vehicle, final @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException,
            CorruptedMeasurementException {
        Log.d(TAG, "Starting asynchronously.");
        if (getContext() == null) {
            return;
        }

        lifecycleLock.lock();
        Log.v(TAG, "Locking in asynchronous start.");
        try {
            if (getIsRunning()) {
                Log.d(TAG, "DataCapturingService assumes that the service is running and thus returns.");
                return;
            }
            // This is necessary to allow the App using the SDK to reconnect and prevent it from reconnecting while
            // stopping the service.
            setIsStoppingOrHasStopped(false);

            // Ensure there are no unfinished measurements (wrong life-cycle call)
            if (persistenceLayer.hasMeasurement(OPEN) || persistenceLayer.hasMeasurement(PAUSED)) {
                throw new CorruptedMeasurementException("Unfinished measurement on start() found.");
            }

            // Start new measurement
            Measurement measurement = prepareStart(vehicle);
            runService(measurement, finishedHandler);
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
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} or
     *             {@link MeasurementStatus#PAUSED} while stopping the service. This usually occurs if
     *             there was no call to {@link #start(Vehicle, StartUpFinishedHandler)}
     *             prior to stopping.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // This life-cycle method is called by sdk implementing apps (e.g. SR)
    public void stop(final @NonNull ShutDownFinishedHandler finishedHandler)
            throws NoSuchMeasurementException, CursorIsNullException {
        Log.d(TAG, "Stopping asynchronously!");
        if (getContext() == null) {
            return;
        }

        lifecycleLock.lock();
        Log.v(TAG, "Locking in asynchronous stop.");
        try {
            setIsStoppingOrHasStopped(true);
            final Measurement measurement = persistenceLayer.loadCurrentlyCapturedMeasurement();

            if (!stopService(finishedHandler)) {
                // The background service was not active. This can happen when the measurement was paused.
                // Thus, no broadcast was sent to the ShutDownFinishedHandler, thus we do this here:
                sendServiceStoppedBroadcast(getContext(), measurement.getIdentifier(), false);
            }
        } finally {
            // This try makes sure the unlock is also called when the finishRecentMeasurement() command fails
            try {
                // {@link #finishRecentMeasurement()} is even executed when {@link #stopService()} fails which should
                // not even happen. However, it's safer to finish the measurement anyway so that the {@link
                // DataCapturingBackgroundService} crashes and is reset afterwards.

                // We update the {@link MeasurementStatus} here to make sure the {@link Measurement} is also finished
                // is case the {@link DataCapturingBackgroundService} is already dead.
                persistenceLayer.getPersistenceBehaviour().updateRecentMeasurement(FINISHED);
            } finally {
                Log.v(TAG, "Unlocking in asynchronous stop.");
                lifecycleLock.unlock();
            }
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
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} while pausing the
     *             service. This usually occurs if there was no call to
     *             {@link #start(Vehicle, StartUpFinishedHandler)} prior to pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // This life-cycle method is called by sdk implementing apps (e.g. SR)
    public void pause(@NonNull final ShutDownFinishedHandler finishedHandler)
            throws DataCapturingException, NoSuchMeasurementException, CursorIsNullException {
        Log.d(TAG, "Pausing asynchronously.");
        if (getContext() == null) {
            return;
        }

        lifecycleLock.lock();
        Log.v(TAG, "Locking in asynchronous pause.");
        try {
            setIsStoppingOrHasStopped(true);

            if (!stopService(finishedHandler)) {
                throw new DataCapturingException("No active service found to be paused.");
            }
        } finally {
            // This try makes sure the unlock is also called when the pauseRecentMeasurement() command fails
            try {
                // {@link #pauseRecentMeasurement()} is even executed when {@link #stopService()} fails which should
                // not even happen. However, it's safer to pause the measurement anyway so that the {@link
                // DataCapturingBackgroundService} crashes and is reset afterwards.

                // We update the {@link MeasurementStatus} here to make sure the {@link Measurement} is also paused
                // is case the {@link DataCapturingBackgroundService} is already dead.
                persistenceLayer.getPersistenceBehaviour().updateRecentMeasurement(PAUSED);
            } finally {
                Log.v(TAG, "Unlocking in asynchronous pause.");
                lifecycleLock.unlock();
            }
        }
    }

    /**
     * Resumes the current data capturing after a previous call to
     * {@link #pause(ShutDownFinishedHandler)}.
     * <p>
     * This is an asynchronous method. You should not assume that the service has been started after the method returns.
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
     * @throws NoSuchMeasurementException If no measurement was {@link MeasurementStatus#OPEN} while pausing the
     *             service. This usually occurs if there was no call to
     *             {@link #start(Vehicle, StartUpFinishedHandler)} prior to pausing.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // This life-cycle method is called by sdk implementing apps (e.g. SR)
    public void resume(@NonNull final StartUpFinishedHandler finishedHandler) throws DataCapturingException,
            MissingPermissionException, NoSuchMeasurementException, CursorIsNullException {
        Log.d(TAG, "Resuming asynchronously.");
        if (getContext() == null) {
            return;
        }

        lifecycleLock.lock();
        Log.v(TAG, "Locking in asynchronous resume.");
        try {
            if (getIsRunning()) {
                Log.w(TAG, "Ignoring duplicate resume call because service is already running");
                return;
            }
            // This is necessary to allow the App using the SDK to reconnect and prevent it from reconnecting while
            // stopping the service.
            setIsStoppingOrHasStopped(false);

            if (!checkFineLocationAccess(getContext())) {
                persistenceLayer.getPersistenceBehaviour().updateRecentMeasurement(FINISHED);
                throw new MissingPermissionException();
            }

            // Ignore resume if there are no paused measurements (wrong life-cycle call which we support #MOV-460)
            if (!persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                Log.w(TAG, "Ignoring resume() as there is no paused measurement.");
                return;
            }

            // Resume paused measurement
            final Measurement currentMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
            runService(currentMeasurement, finishedHandler);

            // We only update the {@link MeasurementStatus} if {@link #runService()} was successful
            persistenceLayer.getPersistenceBehaviour().updateRecentMeasurement(OPEN);
        } finally {
            Log.v(TAG, "Unlocking in asynchronous resume.");
            lifecycleLock.unlock();
        }
    }

    /**
     * @return The identifier used to qualify {@link Measurement}s from this capturing service with the server receiving
     *         the {@code Measurement}s. This needs to be world wide unique.
     */
    @SuppressWarnings({"unused", "WeakerAccess"}) // sdk implementing apps (SR) uses this to access the device id
    public @NonNull String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    /**
     * Schedules data synchronization for right now. This does not mean synchronization is going to start immediately.
     * The Android system still decides when it is convenient.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Used by implementing app (CY)
    public void scheduleSyncNow() {
        getWiFiSurveyor().scheduleSyncNow();
    }

    /**
     * This method checks whether the {@link DataCapturingBackgroundService} is currently running or not. Since this
     * requires an asynchronous inter process communication, it should be considered a long running operation.
     *
     * @param timeout The timeout of how long to wait for the service to answer before deciding it is not running. After
     *            this timeout has passed the <code>IsRunningCallback#timedOut()</code> method is called. Since the
     *            communication between this class and its background service is usually quite fast (almost
     *            instantaneous), you may use pretty low values here. It still is a long running operation and should be
     *            handled as such in the UI.
     * @param unit The unit of time specified by timeout.
     * @param callback Called as soon as the current state of the service has become clear.
     */
    @SuppressWarnings("WeakerAccess") // Sdk implementing apps (SR) use this method to check for capturing after resume
    public void isRunning(final long timeout, final TimeUnit unit, final @NonNull IsRunningCallback callback) {
        Log.v(TAG, "Checking isRunning?");
        final PongReceiver pongReceiver = new PongReceiver(getContext(), deviceIdentifier);
        pongReceiver.checkIsRunningAsync(timeout, unit, callback);
    }

    /**
     * Disconnects your app from the {@link DataCapturingService}. Data capturing will continue in the background
     * but you will not receive any updates about this. This frees some resources used for communication and cleanly
     * shuts down the connection. You should call this method in your {@link android.app.Activity} lifecycle
     * {@code Activity#onStop()}. You may call {@link #reconnect(long)} if you would like to receive updates again, as
     * in {@code Activity#onRestart()}.
     *
     * @throws DataCapturingException If service was not connected.
     */
    @SuppressWarnings({"unused", "WeakerAccess"}) // Used by DataCapturingListeners (CY)
    public void disconnect() throws DataCapturingException {
        unbind();
    }

    /**
     * Reconnects your app to this service. This might be especially useful if your app has been disconnected in a
     * via {@code Activity#onStop()}. You must call this to receive {@link DataCapturingListener} events again.
     * <p>
     * <b>ATTENTION</b>: This method might take some time to check for a running service. Always consider this to be a
     * long running operation and never call it on the main thread.
     *
     * @param isRunningTimeout the number of ms to wait for the callback, see
     *            {@link #isRunning(long, TimeUnit, IsRunningCallback)}. Default is {@link #IS_RUNNING_CALLBACK_TIMEOUT}
     * @return True if the background service was running and, thus, the binding method was called. The success of the
     *         binding determines the {@code #getIsRunning()} value, see {@code #bind()}.
     * @throws IllegalStateException If communication with background service is not successful.
     */
    @SuppressWarnings("WeakerAccess") // Used by DataCapturingListeners (CY)
    public boolean reconnect(final long isRunningTimeout) {

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();

        // The condition is used to signal that we can unlock this thread
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

        isRunning(isRunningTimeout, TimeUnit.MILLISECONDS, reconnectCallback);

        // Wait for isRunning to return.
        lock.lock();
        try {
            Log.v(TAG, "DataCapturingService.reconnect(): Waiting for condition on isRunning!");
            // We might not need the condition.await() as this should time out a bit later as the isRunning call
            if (!condition.await(isRunningTimeout, TimeUnit.MILLISECONDS) || reconnectCallback.hasTimedOut()) {
                Log.d(TAG, "DataCapturingService.reconnect(): Waiting for isRunning timed out!");
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param uiListener A listener for events which the UI might be interested in.
     */
    void setUiListener(final @NonNull UIListener uiListener) {
        this.uiListener = uiListener;
    }

    /**
     * @return A listener for events which the UI might be interested in. This might be <code>null</code> if there has
     *         been no previous call to {@link #setUiListener(UIListener)}.
     */
    @SuppressWarnings("unused") // Used by MovebisDataCapturingService
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
     * @throws DataCapturingException If service could not be started.
     */
    private synchronized void runService(final Measurement measurement,
            final @NonNull StartUpFinishedHandler startUpFinishedHandler) throws DataCapturingException {
        final Context context = getContext();
        Log.v(TAG, "Registering startUpFinishedHandler as broadcast receiver.");
        context.registerReceiver(startUpFinishedHandler,
                new IntentFilter(MessageCodes.getServiceStartedActionId(deviceIdentifier)));

        Log.d(TAG, "Starting the background service for measurement " + measurement + "!");
        final Intent startIntent = new Intent(context, DataCapturingBackgroundService.class);
        startIntent.putExtra(MEASUREMENT_ID, measurement.getIdentifier());
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, authority);
        startIntent.putExtra(EVENT_HANDLING_STRATEGY_ID, eventHandlingStrategy);
        startIntent.putExtra(DISTANCE_CALCULATION_STRATEGY_ID, distanceCalculationStrategy);

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
     * @param finishedHandler The handler to call after receiving the stop message from the
     *            <code>DataCapturingBackgroundService</code>. There are some cases where this never happens, so be
     *            careful when using this method.
     * @return True if there was a service running which was stopped
     */
    private boolean stopService(final @NonNull ShutDownFinishedHandler finishedHandler) {
        Log.d(TAG, "Stopping the background service.");
        setIsRunning(false);
        final Context context = getContext();
        Log.v(TAG, "Registering finishedHandler for service stop synchronization broadcast.");
        LocalBroadcastManager.getInstance(context).registerReceiver(finishedHandler,
                new IntentFilter(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED));

        final boolean serviceWasActive;
        try {
            // For some reasons we have to call the unbind here.
            // We tried to send the stopIntent to the BackgroundService first which calls it's onDestroy
            // method which sends an SERVICE_STOPPED message back to this service where we executed
            // the unbind method. This should have worked for both - stopping the BackgroundService from
            // this service and by itself (via eventHandlerStrategyImpl.handleSpaceWarning())
            unbind();
        } catch (final DataCapturingException e) {
            // We probably catch this silently as we only try to unbind and the stopService call follows
            Log.w(TAG, "Service was either paused or already stopped, so I was unable to unbind from it.");
        } finally {
            Log.v(TAG, String.format("Stopping using Intent with context %s", context));
            final Intent stopIntent = new Intent(context, DataCapturingBackgroundService.class);
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
    // BooleanMethodIsAlwaysInverted: Better readable this way
    // WeakerAccess: Used by MovebisDataCapturingService
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "WeakerAccess"})
    boolean checkFineLocationAccess(final @NonNull Context context) {
        boolean permissionAlreadyGranted = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!permissionAlreadyGranted && uiListener != null) {
            return uiListener.onRequirePermission(Manifest.permission.ACCESS_FINE_LOCATION, new Reason(
                    "This app uses the GNSS (GPS) receiver to display your position. If you would like your position to be shown as exactly as possible please allow access to the GNSS (GPS) sensors."));
        } else {
            return permissionAlreadyGranted;
        }
    }

    /**
     * Prepares for starting the background service, by checking for the system to be in the correct state, creating a
     * new {@link Measurement} and initializing the message handler for messages from the data capturing background
     * service.
     *
     * @param vehicle The type of vehicle this method is called for. If you do not know which vehicle was used you might
     *            use {@link Vehicle#UNKNOWN}.
     * @return The prepared measurement, which is ready to receive data.
     * @throws DataCapturingException If this object has no valid Android <code>Context</code>.
     * @throws MissingPermissionException If permission <code>ACCESS_FINE_LOCATION</code> has not been granted or
     *             revoked.
     * @throws IllegalStateException If there are "dead" (open or paused) measurements or if access to the content
     *             provider was impossible.
     *             The first could maybe happen when the {@link DataCapturingBackgroundService} dies with a hard
     *             exception and, thus, did not finish the {@link Measurement}. To find out if this can happen, we
     *             explicitly do not handle this case softly. If this case occurs we need to write a handling to clean
     *             up such measurements.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    private Measurement prepareStart(final @NonNull Vehicle vehicle)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {
        if (context.get() == null) {
            throw new DataCapturingException("No context to start service!");
        }
        if (!checkFineLocationAccess(getContext())) {
            throw new MissingPermissionException();
        }
        final boolean hasOpenMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.OPEN);
        final boolean hasPausedMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED);
        Validate.isTrue(!hasOpenMeasurements, "There is a dead OPEN measurement!");
        Validate.isTrue(!hasPausedMeasurements, "There is a dead PAUSED measurement or wrong life-cycle call.");

        return persistenceLayer.newMeasurement(vehicle);
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

        // This must not be interrupted or interrupt a call to stop the service.
        lifecycleLock.lock();
        Log.v(TAG, "Locking bind.");
        try {
            Log.d(TAG, "Binding BackgroundServiceConnection");
            if (getIsStoppingOrHasStopped()) {
                Log.w(TAG, "Ignoring BackgroundServiceConnection bind as getIsStoppingOrHasStopped() is true!");
            }

            Intent startIntent = new Intent(context.get(), DataCapturingBackgroundService.class);
            boolean ret = context.get().bindService(startIntent, serviceConnection, 0);
            setIsRunning(ret);
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
        Log.v(TAG, "Setting isRunning to " + isRunning);
        this.isRunning = isRunning;
    }

    /**
     * @return {@code true} if data capturing is running; {@code false} otherwise.
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"}) // Used by a test in the cyface flavour
    boolean getIsRunning() {
        Log.v(TAG, "Getting isRunning with value " + isRunning);
        return isRunning;
    }

    /**
     * @param isStoppingOrHasStopped A flag indicating whether the background service is currently stopped or in the
     *            process of stopping. This flag is used to prevent multiple lifecycle method from interrupting a stop
     *            process or being called, while no service is running.
     */
    private void setIsStoppingOrHasStopped(final boolean isStoppingOrHasStopped) {
        Log.v(TAG, "Setting isStoppingOrHasStopped to " + isStoppingOrHasStopped);
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
    @SuppressWarnings("unused") // Used by implementing apps (CY)
    public void addConnectionStatusListener(final @NonNull ConnectionStatusListener listener) {
        this.connectionStatusReceiver.addListener(listener);
    }

    /**
     * Removes the provided object as <code>ConnectionStatusListener</code> from the list of listeners notified by this
     * object.
     *
     * @param listener A listener that is notified of important events during synchronization.
     */
    @SuppressWarnings("unused") // Used by implementing apps (CY)
    public void removeConnectionStatusListener(final @NonNull ConnectionStatusListener listener) {
        this.connectionStatusReceiver.removeListener(listener);
    }

    /**
     * Unregisters the {@link ConnectionStatusReceiver} when no more needed.
     */
    @SuppressWarnings({"unused"}) // Used by implementing apps (CY)
    void shutdownConnectionStatusReceiver() {
        this.connectionStatusReceiver.shutdown(getContext());
    }

    /**
     * see {@link WiFiSurveyor#isConnected()}
     */
    @SuppressWarnings("unused") // Allows implementing apps (CY) to only trigger sync manually when connected
    public boolean isConnected() {
        return getWiFiSurveyor().isConnected();
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
     * @version 1.0.1
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
                    case MessageCodes.GEOLOCATION_FIX:
                        listener.onFixAcquired();
                        break;
                    case MessageCodes.NO_GEOLOCATION_FIX:
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
                        // The background service already received a stopSelf command but as it's still
                        // bound to this service it should be still alive. We unbind it from this service via the
                        // stopService method (to reduce code duplicity).
                        Validate.isTrue(dataCapturingService.stopService(synchronizationReceiver));

                        // Thus, no broadcast was sent to the ShutDownFinishedHandler, so we do this here:
                        dataCapturingService.sendServiceStoppedBroadcast(context, measurementId, false);
                        break;
                    default:
                        listener.onErrorState(new DataCapturingException(
                                context.getString(R.string.unknown_message_error, msg.what)));

                }
            }
        }

        /**
         * Adds a new listener interested in events from the background service.
         * <p>
         * All listeners are automatically removed when the {@link DataCapturingService} is killed.
         *
         * @param listener A listener that is notified of important events during data capturing.
         * @throws IllegalStateException when the listener could not be added or was already added because it's
         *             essential that the registration is successful or else the UI does not receive capturing events
         */
        void addListener(final @NonNull DataCapturingListener listener) {
            Validate.isTrue(this.listener.add(listener));
        }
    }
}
