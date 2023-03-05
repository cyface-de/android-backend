/*
 * Copyright 2017-2023 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.DataCapturingService
import de.cyface.datacapturing.backend.DataCapturingBackgroundService
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.ui.Reason
import de.cyface.datacapturing.ui.UIListener
import de.cyface.persistence.strategy.DistanceCalculationStrategy
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.synchronization.ConnectionStatusListener
import de.cyface.synchronization.ConnectionStatusReceiver
import de.cyface.synchronization.SyncService
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * An object of this class handles the lifecycle of starting and stopping data capturing as well as transmitting results
 * to an appropriate server. To avoid using the users traffic or incurring costs, the service waits for Wifi access
 * before transmitting any data. You may however force synchronization if required, using
 * [.scheduleSyncNow] ()}.
 *
 * An object of this class is not thread safe and should only be used once per application. You may start and stop the
 * service as often as you like and reuse the object.
 *
 * If your app is suspended or shutdown, the service will continue running in the background. However you need to use
 * disconnect and reconnect as part of the `onStop` and the `onResume` method of your
 * `Activity` lifecycle.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 18.0.7
 * @since 1.0.0
 */
abstract class DataCapturingService(
    context: Context, authority: String,
    accountType: String, dataUploadServerAddress: String,
    eventHandlingStrategy: EventHandlingStrategy,
    persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>,
    distanceCalculationStrategy: DistanceCalculationStrategy,
    locationCleaningStrategy: LocationCleaningStrategy,
    capturingListener: DataCapturingListener, sensorFrequency: Int
) {
    /**
     * `true` if data capturing is running; `false` otherwise.
     */
    var isRunning = false
        get() {
            Log.v(Constants.TAG, "Getting isRunning with value $field")
            return field
        }
        private set(isRunning) {
            Log.v(Constants.TAG, "Setting isRunning to $isRunning")
            field = isRunning
        }

    /**
     * A flag indicating whether the background service is currently stopped or in the process of stopping. This flag is
     * used to prevent multiple lifecycle methods from interrupting a stop process or being called, while no service is
     * running.
     */
    private var isStoppingOrHasStopped = false
        get() {
            Log.v(Constants.TAG, "Getting isStoppingOrHasStopped with value $field")
            return field
        }
        private set(isStoppingOrHasStopped) {
            Log.v(Constants.TAG, "Setting isStoppingOrHasStopped to $isStoppingOrHasStopped")
            field = isStoppingOrHasStopped
        }

    /**
     * A weak reference to the calling context. This is a weak reference since the calling context (i.e.
     * `Activity`) might have been destroyed, in which case there is no context anymore.
     */
    private val context: WeakReference<Context?>

    /**
     * Connection used to communicate with the background service
     */
    private val serviceConnection: ServiceConnection

    /**
     * A facade object providing access to the data stored by this `DataCapturingService`.
     */
    @JvmField
    val persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    /**
     * Messenger that handles messages arriving from the `DataCapturingBackgroundService`.
     */
    private val fromServiceMessenger: Messenger

    /**
     * A handler for messages coming from the [DataCapturingBackgroundService].
     */
    private val fromServiceMessageHandler: FromServiceMessageHandler

    /**
     * Messenger used to send messages from this class to the `DataCapturingBackgroundService`.
     */
    private var toServiceMessenger: Messenger? = null

    /**
     * Provides the `WiFiSurveyor` responsible for switching data synchronization on and off, based on WiFi
     * state.
     *
     * This object observers the current WiFi state and starts and stops synchronization based on whether WiFi is active
     * or not. If the WiFi is active it should activate synchronization. If WiFi connectivity is lost it deactivates the
     * synchronization.
     */
    val wiFiSurveyor: WiFiSurveyor

    /**
     * A listener for events which the UI might be interested in. This might be `null` if there has
     * been no previous call to [.setUiListener].
     */
    var uiListener: UIListener? = null
        private set

    /**
     * The `ContentProvider` authority required to request a sync operation in the [WiFiSurveyor].
     * You should use something world wide unique, like your domain, to avoid collisions between different apps using
     * the Cyface SDK.
     */
    private val authority: String

    /**
     * Lock used to protect lifecycle events from each other. This for example prevents a reconnect to disturb a running
     * stop.
     */
    private val lifecycleLock: Lock

    /**
     * The identifier used to qualify [Measurement]s from this capturing service with the server receiving
     * the `Measurement`s. This needs to be world wide unique.
     */
    val deviceIdentifier: String

    /**
     * A receiver for synchronization events.
     */
    private val connectionStatusReceiver: ConnectionStatusReceiver

    /**
     * The strategy used to respond to selected events triggered by this service.
     */
    private val eventHandlingStrategy: EventHandlingStrategy

    /**
     * The strategy used to calculate the [Measurement.getDistance] from [ParcelableGeoLocation] pairs
     */
    private val distanceCalculationStrategy: DistanceCalculationStrategy

    /**
     * The strategy used to filter the [ParcelableGeoLocation]s
     */
    private val locationCleaningStrategy: LocationCleaningStrategy

    /**
     * The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     */
    private val sensorFrequency: Int

    /**
     * Creates a new completely initialized [DataCapturingService].
     *
     * @param context The context (i.e. `Activity`) handling this service.
     * @param authority The `ContentProvider` authority required to request a sync operation in the
     * [WiFiSurveyor]. You should use something world wide unique, like your domain, to avoid
     * collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data with.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     * this service. This must be in the format "https://some.url/optional/resource".
     * @param eventHandlingStrategy The [EventHandlingStrategy] used to react to selected events
     * triggered by the [DataCapturingBackgroundService].
     * @param persistenceLayer The [PersistenceLayer] required to access the device id
     * @param distanceCalculationStrategy The [DistanceCalculationStrategy] used to calculate the
     * [Measurement.getDistance]
     * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
     * [ParcelableGeoLocation]s
     * @param capturingListener A [DataCapturingListener] that is notified of important events during data
     * capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If writing the components preferences fails.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    init {
        if (!dataUploadServerAddress.startsWith("https://") && !dataUploadServerAddress.startsWith("http://")) {
            throw SetupException("Invalid URL protocol")
        }
        this.context = WeakReference(context)
        this.authority = authority
        this.persistenceLayer = persistenceLayer
        serviceConnection = BackgroundServiceConnection()
        connectionStatusReceiver = ConnectionStatusReceiver(context)
        this.eventHandlingStrategy = eventHandlingStrategy
        this.distanceCalculationStrategy = distanceCalculationStrategy
        this.locationCleaningStrategy = locationCleaningStrategy
        this.sensorFrequency = sensorFrequency
        this.deviceIdentifier = persistenceLayer.restoreOrCreateDeviceId()

        // Mark deprecated measurements
        for (m in persistenceLayer.loadMeasurements()) {
            if (m!!.fileFormatVersion < DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION && m.status != MeasurementStatus.DEPRECATED) {
                try {
                    markDeprecated(m.id, m.status)
                } catch (e: NoSuchMeasurementException) {
                    throw IllegalStateException(e) // Should not happen
                }
            } else require(m.fileFormatVersion <= DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION) {
                String.format(
                    "Invalid format version: %d",
                    m.fileFormatVersion
                )
            }
        }
        // FIXME: maybe use DataStore instead, see https://developer.android.com/topic/libraries/architecture/datastore
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sharedPreferencesEditor = preferences.edit()
        sharedPreferencesEditor.putString(
            SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY,
            dataUploadServerAddress
        )
        if (!sharedPreferencesEditor.commit()) {
            throw SetupException("Unable to write preferences!")
        }
        val connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            ?: throw SetupException("Android connectivity manager is not available!")
        wiFiSurveyor = WiFiSurveyor(context, connectivityManager, authority, accountType)
        fromServiceMessageHandler = FromServiceMessageHandler(context, this)
        // The listeners are automatically removed when the service is destroyed (e.g. app kill)
        fromServiceMessageHandler.addListener(capturingListener)
        fromServiceMessenger = Messenger(fromServiceMessageHandler)
        lifecycleLock = ReentrantLock()
        isRunning = false
        isStoppingOrHasStopped = false
    }

    /**
     * Starts the capturing process with a [DataCapturingListener], that is notified of important events occurring
     * while the capturing process is running.
     *
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the [StartUpFinishedHandler] to receive
     * a callback, when the service has been started.
     *
     * This method is thread safe to call.
     *
     * **ATTENTION:** If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     *
     * @param modality The [Modality] used to capture this data. If you have no way to know which kind of
     * `Modality` was used, just use [Modality.UNKNOWN].
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a [UIListener] to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     * @throws CorruptedMeasurementException when there are unfinished, dead measurements.
     */
    // This life-cycle method is called by sdk implementing apps (e.g. SR)
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        CursorIsNullException::class,
        CorruptedMeasurementException::class
    )
    open fun start(modality: Modality, finishedHandler: StartUpFinishedHandler) {
        Log.d(Constants.TAG, "Starting asynchronously.")
        if (getContext() == null) {
            Log.w(Constants.TAG, "Context is null, ignoring start command.")
            return
        }
        lifecycleLock.lock()
        Log.v(Constants.TAG, "Locking in asynchronous start.")
        try {
            if (isRunning) {
                Log.w(
                    Constants.TAG,
                    "DataCapturingService assumes that the service is running and thus returns."
                )
                return
            }
            // This is necessary to allow the App using the SDK to reconnect and prevent it from reconnecting while
            // stopping the service.
            isStoppingOrHasStopped = false

            // Ensure there are no unfinished measurements (wrong life-cycle call)
            if (persistenceLayer.hasMeasurement(MeasurementStatus.OPEN) || persistenceLayer.hasMeasurement(
                    MeasurementStatus.PAUSED
                )
            ) {
                throw CorruptedMeasurementException("Unfinished measurement on start() found.")
            }

            // Start new measurement
            val measurement = prepareStart(modality)
            val timestamp = System.currentTimeMillis()
            persistenceLayer.logEvent(EventType.LIFECYCLE_START, measurement, timestamp)
            persistenceLayer.logEvent(
                EventType.MODALITY_TYPE_CHANGE, measurement, timestamp,
                modality.databaseIdentifier
            )
            runService(measurement, finishedHandler)
        } finally {
            Log.v(Constants.TAG, "Unlocking lifecycle from asynchronous start.")
            lifecycleLock.unlock()
        }
    }

    /**
     * Stops the currently running data capturing process.
     *
     * This is an asynchronous method. You should not assume that the service has been stopped after the method returns.
     * The provided `finishedHandler` is called after the `DataCapturingBackgroundService` has
     * successfully shutdown.
     *
     * ATTENTION: It seems to be possible, that the service stopped signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     *
     * This method is thread safe.
     *
     * @param finishedHandler A handler that gets called after the process of finishing the current measurement has
     * completed.
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] while stopping the service. This usually occurs if
     * there was no call to [.start]
     * prior to stopping.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(
        NoSuchMeasurementException::class,
        CursorIsNullException::class
    )  // used by sdk implementing apps (e.g. SR)
    fun stop(finishedHandler: ShutDownFinishedHandler) {
        Log.d(Constants.TAG, "Stopping asynchronously!")
        if (getContext() == null) {
            return
        }
        lifecycleLock.lock()
        Log.v(Constants.TAG, "Locking in asynchronous stop.")
        try {
            isStoppingOrHasStopped = true
            val currentlyCapturedMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement()
            persistenceLayer.logEvent(EventType.LIFECYCLE_STOP, currentlyCapturedMeasurement)
            if (stopService(finishedHandler)) {
                persistenceLayer.persistenceBehaviour!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
            } else {
                handleStopFailed(currentlyCapturedMeasurement)
            }
        } finally {
            Log.v(Constants.TAG, "Unlocking in asynchronous stop.")
            lifecycleLock.unlock()
        }
    }

    /**
     * Pauses the current data capturing, but does not finish the current measurement.
     *
     * This is an asynchronous method. You should not assume that the service has been stopped after the method returns.
     * The provided `finishedHandler` is called after the `DataCapturingBackgroundService` has
     * successfully shutdown.
     *
     * ATTENTION: It seems to be possible, that the service stopped signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     *
     * @param finishedHandler A handler that is called as soon as the background service has send a message that it has
     * paused.
     * @throws NoSuchMeasurementException If no [Measurement] was [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED].
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(
        NoSuchMeasurementException::class,
        CursorIsNullException::class
    )  // used by sdk implementing apps (e.g. SR)
    fun pause(finishedHandler: ShutDownFinishedHandler) {
        Log.d(Constants.TAG, "Pausing asynchronously.")
        if (getContext() == null) {
            return
        }
        lifecycleLock.lock()
        Log.v(Constants.TAG, "Locking in asynchronous pause.")
        try {
            isStoppingOrHasStopped = true
            val currentlyCapturedMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement()
            persistenceLayer.logEvent(EventType.LIFECYCLE_PAUSE, currentlyCapturedMeasurement)
            if (stopService(finishedHandler)) {
                persistenceLayer.persistenceBehaviour!!.updateRecentMeasurement(MeasurementStatus.PAUSED)
            } else {
                handlePauseFailed(currentlyCapturedMeasurement)
            }
        } finally {
            Log.v(Constants.TAG, "Unlocking in asynchronous pause.")
            lifecycleLock.unlock()
        }
    }

    /**
     * Resumes the current data capturing after a previous call to
     * [.pause].
     *
     * This is an asynchronous method. You should not assume that the service has been started after the method returns.
     * The provided `finishedHandler` is called after the `DataCapturingBackgroundService` has
     * successfully resumed.
     *
     * ATTENTION: It seems to be possible, that the service started signal is never received. Under these circumstances
     * your handle might wait forever. You might want to consider using some timeout mechanism to prevent your app from
     * being caught in an infinite "loop".
     *
     * @param finishedHandler A handler that is called as soon as the background service sends a message that the
     * background service has resumed successfully.
     * @throws DataCapturingException If starting the background service was not successful.
     * @throws MissingPermissionException If permission to access geo location via satellite has not been granted or
     * revoked. The current measurement is closed if you receive this `Exception`. If you get the
     * permission in the future you need to start a new measurement and not call `resumeSync`
     * again.
     * @throws NoSuchMeasurementException If no measurement was [MeasurementStatus.OPEN] while pausing the
     * service. This usually occurs if there was no call to
     * [.start] prior to pausing.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        NoSuchMeasurementException::class,
        CursorIsNullException::class
    )  // used by sdk implementing apps (e.g. SR)
    fun resume(finishedHandler: StartUpFinishedHandler) {
        val persistenceBehavior = persistenceLayer.persistenceBehaviour
        Log.d(Constants.TAG, "Resuming asynchronously.")
        if (getContext() == null) {
            return
        }
        lifecycleLock.lock()
        Log.v(Constants.TAG, "Locking in asynchronous resume.")
        try {
            if (isRunning) {
                Log.w(
                    Constants.TAG,
                    "Ignoring duplicate resume call because service is already running"
                )
                return
            }
            // This is necessary to allow the App using the SDK to reconnect and prevent it from reconnecting while
            // stopping the service.
            isStoppingOrHasStopped = false
            if (!checkFineLocationAccess(getContext()!!)) {
                persistenceBehavior!!.updateRecentMeasurement(MeasurementStatus.FINISHED)
                throw MissingPermissionException()
            }

            // Ignore resume if there are no paused measurements (wrong life-cycle call which we support #MOV-460)
            if (!persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                Log.w(Constants.TAG, "Ignoring resume() as there is no paused measurement.")
                return
            }

            // Resume paused measurement
            val measurement = persistenceLayer.loadCurrentlyCapturedMeasurement()
            Validate.isTrue(measurement.fileFormatVersion == DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION)
            persistenceLayer.logEvent(EventType.LIFECYCLE_RESUME, measurement)
            runService(measurement, finishedHandler)

            // We only update the {@link MeasurementStatus} if {@link #runService()} was successful
            persistenceBehavior!!.updateRecentMeasurement(MeasurementStatus.OPEN)
        } finally {
            Log.v(Constants.TAG, "Unlocking in asynchronous resume.")
            lifecycleLock.unlock()
        }
    }

    /**
     * Handles cases where a [Measurement] failed to be [.stop]ped.
     *
     * We added this handling in because of MOV-788, see [.handlePauseFailed].
     *
     * The goal here is the resolve states which are sort of reasonable to reduce the number of crashes or get more
     * information how this state actually popped up.
     *
     * @param currentlyCapturedMeasurement The currently captured [Measurement]
     */
    private fun handleStopFailed(currentlyCapturedMeasurement: Measurement) {
        isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS, object : IsRunningCallback {
            override fun isRunning() {
                throw IllegalStateException("Capturing is still running.")
            }

            override fun timedOut() {
                try {
                    val hasOpenMeasurement = persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
                    val hasPausedMeasurement =
                        persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)
                    Validate.isTrue(!(hasOpenMeasurement && hasPausedMeasurement))
                    if (hasOpenMeasurement || hasPausedMeasurement) {
                        if (hasOpenMeasurement) {
                            // This _could_ mean that the {@link DataCapturingBackgroundService} died at some point.
                            Log.w(
                                Constants.TAG,
                                "handleStopFailed: open no-running measurement found, update finished."
                            )
                        }
                        // When a paused measurement is found, all is normal so no warning needed
                        persistenceLayer.persistenceBehaviour!!.updateRecentMeasurement(
                            MeasurementStatus.FINISHED
                        )
                    } else {
                        Log.w(
                            Constants.TAG,
                            "handleStopFailed: no unfinished measurement found, nothing to do."
                        )
                    }

                    // The background service was not active. This is normal when we stop a paused measurement.
                    // Thus, no broadcast was sent to the ShutDownFinishedHandler so we do this here:
                    sendServiceStoppedBroadcast(
                        getContext(),
                        currentlyCapturedMeasurement.id,
                        false
                    )
                } catch (e: NoSuchMeasurementException) {
                    throw IllegalStateException(e)
                } catch (e: CursorIsNullException) {
                    throw IllegalStateException(e)
                }
            }
        })
    }

    /**
     * Handles cases where a [Measurement] failed to be [.pause]d.
     *
     * This affected about 0.1% of the users in version 4.0.2 (MOV-788).
     *
     * The goal here is the resolve states which are sort of reasonable to reduce the number of crashes or get more
     * information how this state actually popped up.
     *
     * @param currentlyCapturedMeasurement the currently captured `Measurement`
     */
    private fun handlePauseFailed(currentlyCapturedMeasurement: Measurement) {
        isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS, object : IsRunningCallback {
            override fun isRunning() {
                throw IllegalStateException("Capturing is still running.")
            }

            override fun timedOut() {
                try {
                    val hasOpenMeasurement = persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
                    val hasPausedMeasurement =
                        persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)
                    Validate.isTrue(!(hasOpenMeasurement && hasPausedMeasurement))
                    // There is no good reason why pause is called when there is not even an unfinished measurement
                    Validate.isTrue(hasOpenMeasurement || hasPausedMeasurement)
                    if (hasOpenMeasurement) {
                        // This _could_ mean that the {@link DataCapturingBackgroundService} died at some point.
                        // We just update the {@link MeasurementStatus} and hope all will be okay.
                        Log.w(
                            Constants.TAG,
                            "handlePauseFailed: open no-running measurement found, update state to pause."
                        )
                        persistenceLayer.persistenceBehaviour!!.updateRecentMeasurement(
                            MeasurementStatus.PAUSED
                        )
                    } else {
                        Log.w(
                            Constants.TAG,
                            "handlePauseFailed: paused measurement found, nothing to do."
                        )
                    }

                    // The background service was not active and, thus, could not be stopped.
                    // Thus, no broadcast was sent to the ShutDownFinishedHandler so we do this here:
                    sendServiceStoppedBroadcast(
                        getContext(),
                        currentlyCapturedMeasurement.id,
                        false
                    )
                } catch (e: NoSuchMeasurementException) {
                    throw IllegalStateException(e)
                } catch (e: CursorIsNullException) {
                    throw IllegalStateException(e)
                }
            }
        })
    }

    /**
     * Schedules data synchronization for right now. This does not mean synchronization is going to start immediately.
     * The Android system still decides when it is convenient.
     */
    // Used by implementing app (CY)
    fun scheduleSyncNow() {
        wiFiSurveyor.scheduleSyncNow()
    }

    /**
     * This method checks whether the [DataCapturingBackgroundService] is currently running or not. Since this
     * requires an asynchronous inter process communication, it should be considered a long running operation.
     *
     * @param timeout The timeout of how long to wait for the service to answer before deciding it is not running. After
     * this timeout has passed the `IsRunningCallback#timedOut()` method is called. Since the
     * communication between this class and its background service is usually quite fast (almost
     * instantaneous), you may use pretty low values here. It still is a long running operation and should be
     * handled as such in the UI.
     * @param unit The unit of time specified by timeout.
     * @param callback Called as soon as the current state of the service has become clear.
     */
    // Used by SDK implementing apps (SR)
    fun isRunning(timeout: Long, unit: TimeUnit?, callback: IsRunningCallback) {
        Log.v(Constants.TAG, "Checking isRunning?")
        val pongReceiver = PongReceiver(
            getContext()!!,
            MessageCodes.GLOBAL_BROADCAST_PING,
            MessageCodes.GLOBAL_BROADCAST_PONG
        )
        pongReceiver.checkIsRunningAsync(timeout, unit!!, callback)
    }

    /**
     * Disconnects your app from the [DataCapturingService]. Data capturing will continue in the background
     * but you will not receive any updates about this. This frees some resources used for communication and cleanly
     * shuts down the connection. You should call this method in your [android.app.Activity] lifecycle
     * `Activity#onStop()`. You may call [.reconnect] if you would like to receive updates again, as
     * in `Activity#onRestart()`.
     *
     * @throws DataCapturingException If there was no ongoing capturing in the background so unbinding from the
     * background service failed. As there is currently no cleaner method you can capture this exception
     * softly for now (MOV-588).
     */
    @Throws(DataCapturingException::class)  // Used by DataCapturingListeners (CY)
    fun disconnect() {
        unbind()
    }

    /**
     * Reconnects your app to this service. This might be especially useful if your app has been disconnected in a
     * via `Activity#onStop()`. You must call this to receive [DataCapturingListener] events again.
     *
     * **ATTENTION**: This method might take some time to check for a running service. Always consider this to be a
     * long running operation and never call it on the main thread.
     *
     * @param isRunningTimeout the number of ms to wait for the callback, see
     * [.isRunning]. Default is [.IS_RUNNING_CALLBACK_TIMEOUT]
     * @return True if the background service was running and, thus, the binding method was called. The success of the
     * binding determines the `#getIsRunning()` value, see `#bind()`.
     * @throws IllegalStateException If communication with background service is not successful.
     */
    // Used by DataCapturingListeners (CY)
    fun reconnect(isRunningTimeout: Long): Boolean {
        val lock: Lock = ReentrantLock()
        val condition = lock.newCondition()

        // The condition is used to signal that we can unlock this thread
        val reconnectCallback: ReconnectCallback = object : ReconnectCallback(lock, condition) {
            override fun onSuccess() {
                try {
                    Log.v(Constants.TAG, "ReconnectCallback.onSuccess(): Binding to service!")
                    bind()
                } catch (e: DataCapturingException) {
                    throw IllegalStateException("Illegal state: unable to bind to background service!")
                }
            }
        }
        isRunning(isRunningTimeout, TimeUnit.MILLISECONDS, reconnectCallback)

        // Wait for isRunning to return.
        lock.lock()
        return try {
            Log.v(
                Constants.TAG,
                "DataCapturingService.reconnect(): Waiting for condition on isRunning!"
            )
            // We might not need the condition.await() as this should time out a bit later as the isRunning call
            if (!condition.await(
                    isRunningTimeout,
                    TimeUnit.MILLISECONDS
                ) || reconnectCallback.hasTimedOut()
            ) {
                Log.d(
                    Constants.TAG,
                    "DataCapturingService.reconnect(): Waiting for isRunning timed out!"
                )
                return false
            }
            true
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        } finally {
            lock.unlock()
        }
    }

    /**
     * @param uiListener A listener for events which the UI might be interested in.
     */
    fun setUiListener(uiListener: UIListener) {
        this.uiListener = uiListener
    }

    /**
     * @return The current Android `Context` used by this service or `null` if there currently is
     * none.
     */
    fun getContext(): Context? {
        return context.get()
    }

    /**
     * Starts the associated [DataCapturingBackgroundService] and calls the provided
     * `startedMessageReceiver`, after it successfully started.
     *
     * @param measurement The measurement to store the captured data to.
     * @param startUpFinishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If service could not be started.
     */
    @Synchronized
    @Throws(DataCapturingException::class)
    private fun runService(
        measurement: Measurement,
        startUpFinishedHandler: StartUpFinishedHandler
    ) {
        val context = getContext()
        context!!.registerReceiver(
            startUpFinishedHandler,
            IntentFilter(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED)
        )
        Log.d(
            StartUpFinishedHandler.TAG,
            "DataCapturingService: StartUpFinishedHandler registered for broadcasts."
        )
        Log.d(Constants.TAG, "Starting the background service for measurement $measurement!")
        val startIntent = Intent(context, DataCapturingBackgroundService::class.java)
        // Binding the intent to the package of the app which runs this SDK [DAT-1509].
        startIntent.setPackage(context.packageName)
        startIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, measurement.id)
        startIntent.putExtra(BundlesExtrasCodes.AUTHORITY_ID, authority)
        startIntent.putExtra(BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID, eventHandlingStrategy)
        startIntent.putExtra(
            BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID,
            distanceCalculationStrategy
        )
        startIntent.putExtra(
            BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID,
            locationCleaningStrategy
        )
        startIntent.putExtra(BundlesExtrasCodes.SENSOR_FREQUENCY, sensorFrequency)
        val serviceComponentName: ComponentName?
        serviceComponentName =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        if (serviceComponentName == null) {
            throw DataCapturingException("DataCapturingBackgroundService failed to start!")
        }
        bind()
    }

    /**
     * Stops the running `DataCapturingBackgroundService`, calling the provided `finishedHandler`
     * after successful execution.
     *
     * @param finishedHandler The handler to call after receiving the stop message from the
     * `DataCapturingBackgroundService`. There are some cases where this never happens, so be
     * careful when using this method.
     * @return True if there was a service running which was stopped
     */
    private fun stopService(finishedHandler: ShutDownFinishedHandler): Boolean {
        Log.d(Constants.TAG, "Stopping the background service.")
        isRunning = false
        val context = getContext()
        Log.v(
            Constants.TAG,
            "Registering finishedHandler for service stop synchronization broadcast."
        )
        LocalBroadcastManager.getInstance(context!!).registerReceiver(
            finishedHandler,
            IntentFilter(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED)
        )
        val serviceWasActive: Boolean
        try {
            // For some reasons we have to call the unbind here.
            // We tried to send the stopIntent to the BackgroundService first which calls it's onDestroy
            // method which sends an SERVICE_STOPPED message back to this service where we executed
            // the unbind method. This should have worked for both - stopping the BackgroundService from
            // this service and by itself (via eventHandlerStrategyImpl.handleSpaceWarning())
            unbind()
        } catch (e: DataCapturingException) {
            // We probably catch this silently as we only try to unbind and the stopService call follows
            Log.w(
                Constants.TAG,
                "Service was either paused or already stopped, so I was unable to unbind from it."
            )
        } finally {
            Log.v(Constants.TAG, String.format("Stopping using Intent with context %s", context))
            val stopIntent = Intent(context, DataCapturingBackgroundService::class.java)
            serviceWasActive = context.stopService(stopIntent)
        }
        return serviceWasActive
    }

    /**
     * This message is sent to the [ShutDownFinishedHandler] to inform callers that the async stop
     * command was executed.
     *
     * @param context The [Context] used to send the broadcast from
     * @param measurementIdentifier The id of the stopped measurement
     * @param stoppedSuccessfully True if the background service was still alive before stopped
     */
    private fun sendServiceStoppedBroadcast(
        context: Context?, measurementIdentifier: Long,
        stoppedSuccessfully: Boolean
    ) {
        val stoppedBroadcastIntent = Intent(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED)
        // The measurement id should always be set, also if `stoppedSuccessfully=false` [STAD-333]
        Validate.isTrue(measurementIdentifier > 0L)
        stoppedBroadcastIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, measurementIdentifier)
        stoppedBroadcastIntent.putExtra(
            BundlesExtrasCodes.STOPPED_SUCCESSFULLY,
            stoppedSuccessfully
        )
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(stoppedBroadcastIntent)
    }

    /**
     * Checks whether the user has granted the `ACCESS_FINE_LOCATION` permission and notifies the UI to ask
     * for it if not.
     *
     * @param context Current `Activity` context.
     * @return Either `true` if permission was or has been granted; `false` otherwise.
     */
    // BooleanMethodIsAlwaysInverted: Better readable this way
    // WeakerAccess: Used by MovebisDataCapturingService
    fun checkFineLocationAccess(context: Context): Boolean {
        val permissionAlreadyGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return if (!permissionAlreadyGranted && uiListener != null) {
            uiListener!!.onRequirePermission(
                Manifest.permission.ACCESS_FINE_LOCATION, Reason(
                    "This app uses the GNSS (GPS) receiver to display your position. If you would like your position to be shown as exactly as possible please allow access to the GNSS (GPS) sensors."
                )
            )
        } else {
            permissionAlreadyGranted
        }
    }

    /**
     * Prepares for starting the background service, by checking for the system to be in the correct state, creating a
     * new [Measurement] and initializing the message handler for messages from the data capturing background
     * service.
     *
     * @param modality The type of modality this method is called for. If you do not know which modality was used you
     * might use [Modality.UNKNOWN].
     * @return The prepared measurement, which is ready to receive data.
     * @throws DataCapturingException If this object has no valid Android `Context`.
     * @throws MissingPermissionException If permission `ACCESS_FINE_LOCATION` has not been granted or revoked.
     * @throws IllegalStateException If there are "dead" (open or paused) measurements or if access to the content
     * provider was impossible.
     * The first could maybe happen when the [DataCapturingBackgroundService] dies with a hard
     * exception and, thus, did not finish the [Measurement]. To find out if this can happen, we
     * explicitly do not handle this case softly. If this case occurs we need to write a handling to clean
     * up such measurements.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        CursorIsNullException::class
    )
    private fun prepareStart(modality: Modality): Measurement {
        if (context.get() == null) {
            throw DataCapturingException("No context to start service!")
        }
        if (!checkFineLocationAccess(getContext()!!)) {
            throw MissingPermissionException()
        }
        val hasOpenMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
        val hasPausedMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)
        Validate.isTrue(!hasOpenMeasurements, "There is a dead OPEN measurement!")
        Validate.isTrue(
            !hasPausedMeasurements,
            "There is a dead PAUSED measurement or wrong life-cycle call."
        )
        return persistenceLayer.newMeasurement(modality)
    }

    /**
     * Loads the currently captured [Measurement] from the cache, if possible, or from the
     * [DefaultPersistenceLayer].
     *
     * We offer this API through the [DataCapturingService] to allow the SDK implementor to load the
     * currentlyCapturedMeasurement from the cache as the [DefaultPersistenceBehaviour] does not have a cache
     * which is the only [PersistenceBehaviour] the implementor may use directly.
     *
     * @return the currently captured [Measurement]
     * @throws NoSuchMeasurementException If this method has been called while no `Measurement` was active. To
     * avoid this use [DefaultPersistenceLayer.hasMeasurement] to check whether there is
     * an actual [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] measurement.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    fun loadCurrentlyCapturedMeasurement(): Measurement {
        return persistenceLayer.loadCurrentlyCapturedMeasurement()
    }

    /**
     * Binds this `DataCapturingService` facade to the underlying [DataCapturingBackgroundService].
     *
     * @throws DataCapturingException If binding fails.
     */
    @Throws(DataCapturingException::class)
    private fun bind() {
        if (context.get() == null) {
            throw DataCapturingException("No valid context for binding!")
        }

        // This must not be interrupted or interrupt a call to stop the service.
        lifecycleLock.lock()
        Log.v(Constants.TAG, "Locking bind.")
        isRunning = try {
            Log.d(Constants.TAG, "Binding BackgroundServiceConnection")
            if (isStoppingOrHasStopped) {
                Log.w(
                    Constants.TAG,
                    "Ignoring BackgroundServiceConnection bind as getIsStoppingOrHasStopped() is true!"
                )
            }
            val bindIntent = Intent(context.get(), DataCapturingBackgroundService::class.java)
            val ret = context.get()!!
                .bindService(bindIntent, serviceConnection, 0)
            ret
        } finally {
            Log.v(Constants.TAG, "Unlocking bind.")
            lifecycleLock.unlock()
        }
    }

    /**
     * Unbinds this `DataCapturingService` facade from the underlying [DataCapturingBackgroundService].
     *
     * @throws DataCapturingException If no valid Android context is available or the service was not running.
     */
    @Throws(DataCapturingException::class)
    private fun unbind() {
        if (context.get() == null) {
            throw DataCapturingException("Context was null!")
        }
        try {
            context.get()!!.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            throw DataCapturingException(e)
        }
    }

    /**
     * Adds a new listener interested in events from the synchronization service.
     *
     * @param listener A listener that is notified of important events during synchronization.
     */
    // Used by implementing apps (CY)
    fun addConnectionStatusListener(listener: ConnectionStatusListener) {
        connectionStatusReceiver.addListener(listener)
    }

    /**
     * Removes the provided object as `ConnectionStatusListener` from the list of listeners notified by this
     * object.
     *
     * @param listener A listener that is notified of important events during synchronization.
     */
    // Used by implementing apps (CY)
    fun removeConnectionStatusListener(listener: ConnectionStatusListener) {
        connectionStatusReceiver.removeListener(listener)
    }

    /**
     * Unregisters the [ConnectionStatusReceiver] when no more needed.
     */
    fun  // Used by implementing apps (CY)
            shutdownConnectionStatusReceiver() {
        connectionStatusReceiver.shutdown(getContext())
    }

    /**
     * Handles the connection to a [DataCapturingBackgroundService]. For further information please refer to the
     * [Android documentation](https://developer.android.com/guide/components/bound-services.html).
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    private inner class BackgroundServiceConnection : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            Log.d(Constants.TAG, "DataCapturingService connected to background service.")
            toServiceMessenger = Messenger(binder)
            val registerClient = Message()
            registerClient.replyTo = fromServiceMessenger
            registerClient.what = MessageCodes.REGISTER_CLIENT
            try {
                toServiceMessenger!!.send(registerClient)
            } catch (e: RemoteException) {
                throw IllegalStateException(e)
            }
            Log.d(Constants.TAG, "ServiceConnection established!")
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(Constants.TAG, "Service disconnected!")
            toServiceMessenger = null
        }

        override fun onBindingDied(name: ComponentName) {
            checkNotNull(context.get()) { "Unable to rebind. Context was null." }
            Log.d(Constants.TAG, "Binding died, unbinding & rebinding ...")
            try {
                unbind()
            } catch (e: DataCapturingException) {
                throw IllegalStateException(e)
            }
            val rebindIntent = Intent(context.get(), DataCapturingBackgroundService::class.java)
            context.get()!!.bindService(rebindIntent, this, 0)
        }
    }

    /**
     * Adds a new [DataCapturingListener] interested in events from the [DataCapturingBackgroundService].
     *
     * All listeners are automatically removed when the [DataCapturingService] is killed.
     *
     * @param listener A listener that is notified of important events during data capturing.
     * @return true if this collection changed as a result of the call
     */
    // Used by SDK implementing apps (S, C)
    fun addDataCapturingListener(listener: DataCapturingListener): Boolean {
        return fromServiceMessageHandler.addListener(listener)
    }

    /**
     * Removes a registered [DataCapturingListener] from [DataCapturingBackgroundService] events.
     *
     * Listeners may be removed when on Android's onPause Lifecycle method or e.g. when the UI is disabled.
     *
     * @param listener A listener that was registered to be notified of important events during data capturing.
     * @return true if an element was removed as a result of this call
     */
    // Used by SDK implementing apps (S, C)
    fun removeDataCapturingListener(listener: DataCapturingListener): Boolean {
        return fromServiceMessageHandler.removeListener(listener)
    }

    /**
     * Called when the user switches the [Modality] via UI.
     *
     * In order to record multi-`Modality` `Measurement`s this method records `Modality` switches as
     * [Event]s when this occurs during an ongoing [Measurement]. Does nothing when no capturing
     * [.isRunning].
     *
     * @param newModality the identifier of the new [Modality]
     */
    fun changeModalityType(newModality: Modality) {
        val timestamp = System.currentTimeMillis()
        try {
            val hasOpenMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
            val hasPausedMeasurements = persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)
            if (!hasOpenMeasurements && !hasPausedMeasurements) {
                Log.v(
                    Constants.TAG,
                    "changeModalityType(): No unfinished measurement, event not recorded"
                )
                return
            }

            // Record modality-switch Event for ongoing Measurements
            Log.v(Constants.TAG, "changeModalityType(): Logging Modality type change!")
            val measurement: Measurement
            measurement = loadCurrentlyCapturedMeasurement()

            // Ensure the newModality is actually different to the current Modality
            val modalityChanges =
                persistenceLayer.loadEvents(measurement.id, EventType.MODALITY_TYPE_CHANGE)
            if (modalityChanges!!.size > 0) {
                val lastModalityChangeEvent = modalityChanges[modalityChanges.size - 1]
                val lastChangeValue = lastModalityChangeEvent!!.value
                Validate.notNull(lastChangeValue)
                if (lastChangeValue == newModality.databaseIdentifier) {
                    Log.d(
                        Constants.TAG,
                        "changeModalityType(): Doing nothing as current Modality equals the newModality."
                    )
                    return
                }
            }
            persistenceLayer.logEvent(
                EventType.MODALITY_TYPE_CHANGE, measurement, timestamp,
                newModality.databaseIdentifier
            )
        } catch (e: CursorIsNullException) {
            throw IllegalStateException(e)
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * Marks a measurement which is in an unsupported format as [MeasurementStatus.DEPRECATED].
     *
     * @param measurementIdentifier the id of the measurement to update
     * @param status the current [MeasurementStatus] of the measurement
     * @throws NoSuchMeasurementException If the [Measurement] does not exist.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    private fun markDeprecated(measurementIdentifier: Long, status: MeasurementStatus) {
        Log.d(
            Constants.TAG,
            String.format(
                "markDeprecated(): Updating measurement %d: %s -> %s",
                measurementIdentifier,
                status,
                MeasurementStatus.DEPRECATED
            )
        )
        when (status) {
            MeasurementStatus.OPEN, MeasurementStatus.PAUSED -> {
                // Mark as finished, to use `markFinishedAs` for cleaning afterwards
                persistenceLayer.setStatus(measurementIdentifier, MeasurementStatus.FINISHED, false)
                persistenceLayer.markFinishedAs(MeasurementStatus.DEPRECATED, measurementIdentifier)
            }
            MeasurementStatus.FINISHED -> persistenceLayer.markFinishedAs(
                MeasurementStatus.DEPRECATED,
                measurementIdentifier
            )
            MeasurementStatus.SKIPPED, MeasurementStatus.SYNCED ->                 // No need to clean the measurement using `markFinishedAs`
                persistenceLayer.setStatus(
                    measurementIdentifier,
                    MeasurementStatus.DEPRECATED,
                    false
                )
            MeasurementStatus.DEPRECATED -> {}
        }
    }

    /**
     * A handler for messages coming from the [DataCapturingBackgroundService].
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 2.0.1
     * @since 2.0.0
     */
    private class FromServiceMessageHandler internal constructor(
        /**
         * The Android context this handler is running under.
         */
        private val context: Context,
        dataCapturingService: DataCapturingService
    ) : Handler(context.mainLooper) {
        /**
         * A listener that is notified of important events during data capturing.
         */
        private val listener: MutableCollection<DataCapturingListener>

        /**
         * The service which calls this handler.
         */
        private val dataCapturingService: DataCapturingService

        /**
         * Creates a new completely initialized `FromServiceMessageHandler`.
         *
         * @param context The Android context this handler is running under.
         * @param dataCapturingService The service which calls this handler.
         */
        init {
            listener = HashSet()
            this.dataCapturingService = dataCapturingService
        }

        override fun handleMessage(msg: Message) {
            Log.v(Constants.TAG, String.format("Service facade received message: %d", msg.what))
            val parcel: Bundle
            parcel = msg.data
            parcel.classLoader = javaClass.classLoader
            if (msg.what == MessageCodes.SERVICE_STOPPED || msg.what == MessageCodes.SERVICE_STOPPED_ITSELF) {
                informShutdownFinishedHandler(msg.what, parcel)
            }

            // Inform all CapturingListeners (if any are registered) about events
            for (listener in listener) {
                informDataCapturingListener(listener, msg.what, parcel)
            }
        }

        /**
         * Informs a [DataCapturingListener] about events from [DataCapturingBackgroundService].
         *
         * @param listener the [DataCapturingListener] to inform
         * @param messageCode the [MessageCodes] code which identifies the `Message`
         * @param parcel the [Bundle] containing the parcel delivered with the message
         */
        private fun informDataCapturingListener(
            listener: DataCapturingListener, messageCode: Int,
            parcel: Bundle
        ) {
            when (messageCode) {
                MessageCodes.LOCATION_CAPTURED -> {
                    val location = parcel.getParcelable<ParcelableGeoLocation>("data")
                    if (location == null) {
                        listener.onErrorState(
                            DataCapturingException(context.getString(R.string.missing_data_error))
                        )
                    } else {
                        listener.onNewGeoLocationAcquired(location)
                    }
                }
                MessageCodes.DATA_CAPTURED -> {
                    val capturedData = parcel.getParcelable<CapturedData>("data")
                    if (capturedData == null) {
                        listener.onErrorState(
                            DataCapturingException(context.getString(R.string.missing_data_error))
                        )
                    } else {
                        Log.v(Constants.TAG, "Captured some sensor data.")
                        listener.onNewSensorDataAcquired(capturedData)
                    }
                }
                MessageCodes.GEOLOCATION_FIX -> listener.onFixAcquired()
                MessageCodes.NO_GEOLOCATION_FIX -> listener.onFixLost()
                MessageCodes.ERROR_PERMISSION -> listener.onRequiresPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION, Reason(
                        "Data capturing requires permission to access geo location via satellite. Was not granted or revoked!"
                    )
                )
                MessageCodes.SERVICE_STOPPED, MessageCodes.SERVICE_STOPPED_ITSELF -> listener.onCapturingStopped()
                else -> listener.onErrorState(
                    DataCapturingException(
                        context.getString(R.string.unknown_message_error, messageCode)
                    )
                )
            }
        }

        /**
         * Informs the [ShutDownFinishedHandler] that the [DataCapturingBackgroundService] stopped.
         *
         * @param messageCode the [MessageCodes] code identifying the `Message` type
         * @param parcel the [Bundle] containing the parcel delivered with the message
         */
        private fun informShutdownFinishedHandler(messageCode: Int, parcel: Bundle) {
            val dataBundle = parcel.getParcelable<Bundle>("data")
            Validate.notNull(dataBundle)
            val measurementId = dataBundle!!.getLong(BundlesExtrasCodes.MEASUREMENT_ID)
            when (messageCode) {
                MessageCodes.SERVICE_STOPPED -> {
                    val stoppedSuccessfully =
                        dataBundle.getBoolean(BundlesExtrasCodes.STOPPED_SUCCESSFULLY)
                    // Success means the background service was still alive. As this is the private
                    // IPC to the background service this must always be true.
                    Validate.isTrue(stoppedSuccessfully)

                    // Inform interested parties
                    dataCapturingService.sendServiceStoppedBroadcast(context, measurementId, true)
                }
                MessageCodes.SERVICE_STOPPED_ITSELF -> {
                    // Attention: This method is very rarely executed and so be careful when you change it's logic.
                    // The task for the missing test is CY-4111. Currently only tested manually.
                    val lock: Lock = ReentrantLock()
                    val condition = lock.newCondition()
                    val synchronizationReceiver = StopSynchronizer(
                        lock, condition,
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    )
                    // The background service already received a stopSelf command but as it's still
                    // bound to this service it should be still alive. We unbind it from this service via the
                    // stopService method (to reduce code duplicity).
                    Validate.isTrue(dataCapturingService.stopService(synchronizationReceiver))

                    // Thus, no broadcast was sent to the ShutDownFinishedHandler, so we do this here:
                    dataCapturingService.sendServiceStoppedBroadcast(context, measurementId, false)
                }
                else -> throw IllegalArgumentException("Unknown messageCode: $messageCode")
            }
        }

        /**
         * Adds a new [DataCapturingListener] interested in events from the
         * [DataCapturingBackgroundService].
         *
         * All listeners are automatically removed when the [DataCapturingService] is stopped.
         *
         * @param listener A listener that is notified of important events during data capturing.
         * @return `True` if this collection changed as a result of the call
         */
        fun addListener(listener: DataCapturingListener): Boolean {
            return this.listener.add(listener)
        }

        /**
         * Removes a registered [DataCapturingListener] from [DataCapturingBackgroundService] events.
         *
         * Listeners may be removed when on Android's onPause Lifecycle method or e.g. when the UI is disabled.
         *
         * @param listener A listener that was registered to be notified of important events during data capturing.
         * @return `True` if an element was removed as a result of this call
         */
        fun removeListener(listener: DataCapturingListener): Boolean {
            return this.listener.remove(listener)
        }
    }

    companion object {
        /**
         * The number of ms to wait for the callback, see [.isRunning].
         */
        // Used by SDK integrators (CY)
        const val IS_RUNNING_CALLBACK_TIMEOUT = 500L
    }
}