/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.datacapturing.backend

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.RemoteException
import android.util.Log
import de.cyface.datacapturing.Constants
import de.cyface.datacapturing.EventHandlingStrategy
import de.cyface.datacapturing.MessageCodes
import de.cyface.datacapturing.StartUpFinishedHandler
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.ParcelablePoint3D
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.persistence.strategy.DistanceCalculationStrategy
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.utils.DiskConsumption
import de.cyface.utils.PlaceholderNotificationBuilder
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * This is the implementation of the data capturing process running in the background while a Cyface measuring is
 * active. The service is started by a caller and sends messages to that caller, informing it about its status.
 *
 * The DataCapturingBackgroundService hides the complexity of the DataCapturingService in order to
 * hide most of the inter process communication from the SDK user who interact with the DataCapturingService.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 8.0.3
 * @since 2.0.0
 */
class DataCapturingBackgroundService : Service(), CapturingProcessListener {
    /**
     * The Android `Messenger` used to send IPC messages, informing the caller about the current status of
     * data capturing.
     */
    private var callerMessenger: Messenger? = null

    /**
     * The list of clients receiving messages from this service as well as sending control messages.
     */
    private val clients: MutableSet<Messenger> = HashSet()

    /**
     * A wake lock used to keep the application active during data capturing.
     */
    private lateinit var wakeLock: WakeLock

    /**
     * A `CapturingProcess` implementation which is responsible for actual data capturing.
     */
    private lateinit var dataCapturing: CapturingProcess

    /**
     * A facade handling reading and writing data from and to the Android content provider used to store and retrieve
     * measurement data.
     */
    lateinit var persistenceLayer: PersistenceLayer<CapturingPersistenceBehaviour>

    /**
     * Receiver for pings to the service. The receiver answers with a pong as long as this service is running.
     */
    private var pingReceiver: PingReceiver? = null

    /**
     * The identifier of the measurement to save all the captured data to.
     */
    private var currentMeasurementIdentifier: Long = 0

    /**
     * The strategy used to respond to selected events triggered by this service.
     */
    var eventHandlingStrategy: EventHandlingStrategy? = null

    /**
     * The strategy used to calculate the `Measurement.getDistance` from [ParcelableGeoLocation] pairs
     */
    var distanceCalculationStrategy: DistanceCalculationStrategy? = null

    /**
     * The strategy used to filter the received [ParcelableGeoLocation]s
     */
    var locationCleaningStrategy: LocationCleaningStrategy? = null

    /**
     * This `PersistenceBehaviour` is used to capture a `Measurement`s with when a
     * [DefaultPersistenceLayer].
     */
    var capturingBehaviour: CapturingPersistenceBehaviour? = null

    /**
     * The last captured [ParcelableGeoLocation] used to calculate the distance to the next `GeoLocation`.
     */
    private var lastLocation: ParcelableGeoLocation? = null

    /**
     * The `Measurement.getDistance` in meters until the last location update.
     */
    private var lastDistance = 0.0

    /**
     * The unix timestamp in milliseconds capturing the start of this service (i.e. of the tracking)
     * to filter out cached locations from distance calculation (STAD-140).
     */
    var startupTime: Long = 0

    override fun onBind(intent: Intent): IBinder? {
        Log.v(TAG, "onBind")

        return callerMessenger!!.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.v(TAG, "Unbinding from data capturing service.")
        return true // I want to receive calls to onRebind
    }

    override fun onRebind(intent: Intent) {
        Log.v(TAG, "Rebinding to data capturing service.")
        super.onRebind(intent)
    }

    @SuppressLint("WakelockTimeout") // We can not provide a timeout since our service might need to run for hours.
    override fun onCreate() {
        Log.v(TAG, "onCreate")
        // We only have 5 seconds to call startForeground before the service crashes, so we call it as early as possible
        // with a placeholder notification. This is substituted by the provided notification in onStartCommand. On most
        // devices the user should not even see this happening.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Requests the foreground service types as declared in the manifest (here: location)
            startForeground(
                NOTIFICATION_ID, PlaceholderNotificationBuilder.build(
                    this
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            startForeground(
                NOTIFICATION_ID, PlaceholderNotificationBuilder.build(
                    this
                )
            )
        }
        super.onCreate()

        // Prevents this process from being killed by the system.
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "de.cyface:wakelock")
        wakeLock.acquire()

        // We must register the receiver as soon as possible - onBind and onStartCommand are too late (race condition)
        if (pingReceiver != null) {
            Log.v(TAG, "onBind: Ping Receiver was already registered")
            return
        }

        callerMessenger = Messenger(
            MessageHandler(
                this
            )
        )

        // Allows other parties to ping this service to see if it is running
        pingReceiver =
            PingReceiver(MessageCodes.GLOBAL_BROADCAST_PING, MessageCodes.GLOBAL_BROADCAST_PONG)

        @SuppressWarnings("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Does not work with NOT_EXPORTED
            registerReceiver(
                pingReceiver,
                IntentFilter(MessageCodes.GLOBAL_BROADCAST_PING),
                RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(pingReceiver, IntentFilter(MessageCodes.GLOBAL_BROADCAST_PING))
        }
        Log.d(TAG, "onCreate: Ping Receiver registered")

        startupTime = System.currentTimeMillis()
        Log.v(TAG, "finishedOnCreate")
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy")
        Log.v(TAG, "onDestroy: Unregistering Ping receiver.")
        unregisterReceiver(pingReceiver)
        pingReceiver = null
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        dataCapturing.close()
        persistenceLayer.shutdown()

        // OnDestroy is called before the messages below to make sure it's semantic is right (stopped)
        super.onDestroy()
        sendStoppedMessage()
    }

    /**
     * Sends an IPC message to interested parties that the service stopped successfully.
     */
    private fun sendStoppedMessage() {
        Log.v(TAG, "Sending IPC message: service stopped.")

        // Attention: the bundle is bundled again by informCaller !
        val bundle = Bundle()
        bundle.putLong(BundlesExtrasCodes.MEASUREMENT_ID, currentMeasurementIdentifier)
        bundle.putBoolean(BundlesExtrasCodes.STOPPED_SUCCESSFULLY, true)
        informCaller(MessageCodes.SERVICE_STOPPED, bundle)
    }

    /**
     * Sends an IPC message to interested parties that the service stopped itself.
     *
     *
     * This method must be called when [stopSelf] is called on this service without a prior intent
     * from the `DataCapturingService`. It must be called e.g. from an
     * [EventHandlingStrategy.handleSpaceWarning] implementation
     * to unbind the [DataCapturingBackgroundService] from the `DataCapturingService`.
     *
     *
     * Attention: This method is very rarely executed and so be careful when you change it's logic.
     * The task for the missing test is CY-4111. Currently only tested manually.
     */
    @Suppress("unused") // Because must be callable by custom {@link EventHandlingStrategy} implementations
    fun sendStoppedItselfMessage() {
        Log.v(TAG, "Sending IPC message: service stopped itself.")
        // Attention: the bundle is bundled again by informCaller !
        val bundle = Bundle()
        bundle.putLong(BundlesExtrasCodes.MEASUREMENT_ID, currentMeasurementIdentifier)
        informCaller(MessageCodes.SERVICE_STOPPED_ITSELF, bundle)
    }

    /**
     * We don't use [startService] to pause or stop the service because we prefer to use a lock and
     * finally in the `DataCapturingService`'s life-cycle methods instead. This also avoids headaches with
     * replacing the return statement from [stopService] by the return by `#startService()`.
     *
     * For the remaining documentation see the overwritten `#onStartCommand(Intent, int, int)`
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v(TAG, "onStartCommand: Starting DataCapturingBackgroundService")

        // Loads persistence layer
        capturingBehaviour = CapturingPersistenceBehaviour()
        persistenceLayer = DefaultPersistenceLayer(this, capturingBehaviour!!)

        // Loads EventHandlingStrategy
        this.eventHandlingStrategy =
            intent.getParcelableExtra(BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID)
        requireNotNull(eventHandlingStrategy)
        val notification = eventHandlingStrategy!!.buildCapturingNotification(this)
        val notificationManager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager
        // Update the placeholder notification
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Loads DistanceCalculationStrategy
        this.distanceCalculationStrategy =
            intent.getParcelableExtra(BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID)
        requireNotNull(distanceCalculationStrategy)

        // Loads LocationCleaningStrategy
        this.locationCleaningStrategy =
            intent.getParcelableExtra(BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID)
        requireNotNull(locationCleaningStrategy)

        // Loads measurement id
        val measurementIdentifier = intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1)
        check(measurementIdentifier != -1L) { "No valid measurement identifier provided for started service ." }
        this.currentMeasurementIdentifier = measurementIdentifier

        // Load Distance (or else we would reset the distance when resuming a measurement)
        val measurement = persistenceLayer.loadMeasurement(currentMeasurementIdentifier)
        lastDistance = measurement!!.distance

        // Ensure we resume measurements with a known file format version
        val persistenceFileFormatVersion = measurement.fileFormatVersion
        require(persistenceFileFormatVersion == DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION) {
            "Resume a measurement of a previous persistence file format version is not supported!"
        }

        // Load sensor frequency
        val sensorCapture: SensorCapture? =
            intent.getParcelableExtra(BundlesExtrasCodes.SENSOR_CAPTURE)
        requireNotNull(sensorCapture) { "No sensor capture mode provided for started service ." }

        // Init capturing process
        dataCapturing = initializeCapturingProcess(sensorCapture)
        dataCapturing.addCapturingProcessListener(this)

        // Informs about the service start
        Log.d(
            StartUpFinishedHandler.TAG,
            "DataCapturingBackgroundService.onStartCommand: Sending broadcast service started."
        )
        val startedIntent = Intent(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED)
        // Binding the intent to the package of the app which runs this SDK [DAT-1509].
        startedIntent.setPackage(baseContext.packageName)
        startedIntent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, currentMeasurementIdentifier)
        sendBroadcast(startedIntent)

        // NOT_STICKY to avoid recreation of the process which could mess up the life-cycle
        return START_NOT_STICKY
    }

    /*
     * MARK: Methods
     */
    /**
     * Initializes this service
     *
     * @param sensorCapture The [SensorCapture] implementation which decides if sensor data should
     * be captured.
     */
    private fun initializeCapturingProcess(sensorCapture: SensorCapture):
            GeoLocationCapturingProcess {
        Log.v(TAG, "Initializing capturing process")

        val locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
        val locationCapture = LocationCapture()
        val statusHandler = GnssStatusCallback(locationManager)
        locationCapture.setup(locationManager, statusHandler)

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorCapture.setup(sensorManager)

        return GeoLocationCapturingProcess(locationCapture, sensorCapture)
    }

    /**
     * This method sends an inter process communication (IPC) message to all callers of this service.
     *
     * @param messageCode A code identifying the message that is send. See [MessageCodes] for further details.
     * @param data The data to send appended to this message. This may be `null` if no data needs to be send.
     */
    fun informCaller(messageCode: Int, data: Parcelable?) {
        val msg = Message.obtain(null, messageCode)

        if (data != null) {
            val dataBundle = Bundle()
            dataBundle.putParcelable("data", data)
            msg.data = dataBundle
        }

        Log.v(TAG, "Sending message $messageCode to ${clients.size} callers.")
        val temporaryCallerSet: Set<Messenger> = HashSet(clients)
        for (caller in temporaryCallerSet) {
            try {
                caller.send(msg)
            } catch (e: RemoteException) {
                Log.w(TAG, "Unable to send message ($msg) to caller $caller!", e)
                clients.remove(caller)
            } /* [STAD-496]: On devices with vertical accuracy = null this NPE was caught and unregistered
            the caller, i.e. the service stopped intent was not forwarded to the client. As no client uses
            React Native right now, we disable this catch completely, but if we have to re-enable it
            we need to ensure only `client = null` is caught, not all NPEs.

            catch (final NullPointerException e) {
                // Caller may be null in a typical React Native application.
                Log.w(TAG, String.format("Unable to send message (%s) to null caller!", msg), e);
                clients.remove(caller);
            }*/
        }
    }

    /*
     * MARK: CapturingProcessListener Interface
     */
    override fun onDataCaptured(data: CapturedData) {
        val accelerations = data.accelerations
        val rotations = data.rotations
        val directions = data.directions
        val pressures = data.pressures
        val iterationSize = max(
            accelerations.size.toDouble(),
            max(
                directions.size.toDouble(),
                max(rotations.size.toDouble(), pressures.size.toDouble())
            )
        ).toInt()
        var i = 0
        while (i < iterationSize) {
            val dataSublist = CapturedData(
                sampleSubList(accelerations, i),
                sampleSubList(rotations, i),
                sampleSubList(directions, i),
                pressureSubList(pressures, i)
            )
            informCaller(MessageCodes.DATA_CAPTURED, dataSublist)
            capturingBehaviour!!.storeData(dataSublist, currentMeasurementIdentifier) {}
            i += MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE
        }
    }

    /**
     * Extracts a subset of maximal `MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE` elements of captured data.
     *
     * @param completeList The [<] to extract a subset from
     * @param fromIndex The low endpoint (inclusive) of the subList
     * @return The extracted sublist
     */
    private fun sampleSubList(
        completeList: List<ParcelablePoint3D>,
        fromIndex: Int
    ): List<ParcelablePoint3D> {
        val endIndex = fromIndex + MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE
        val toIndex = min(endIndex.toDouble(), completeList.size.toDouble()).toInt()
        return if ((fromIndex >= toIndex)) emptyList() else completeList.subList(fromIndex, toIndex)
    }

    /**
     * Extracts a subset of maximal `MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE` elements of captured data.
     *
     * TODO: Copy of [sampleSubList] until `DataPoint` merges with `DataPoint`.
     *
     * @param completeList The [<] to extract a subset from
     * @param fromIndex The low endpoint (inclusive) of the subList
     * @return The extracted sublist
     */
    private fun pressureSubList(
        completeList: List<ParcelablePressure>,
        fromIndex: Int
    ): List<ParcelablePressure> {
        val endIndex = fromIndex + MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE
        val toIndex = min(endIndex.toDouble(), completeList.size.toDouble()).toInt()
        return if ((fromIndex >= toIndex)) emptyList() else completeList.subList(fromIndex, toIndex)
    }

    override fun onLocationCaptured(newLocation: ParcelableGeoLocation) {
        // Store raw, unfiltered track

        Log.d(TAG, "Location captured")
        capturingBehaviour!!.storeLocation(newLocation, currentMeasurementIdentifier)

        // Check available space
        if (!DiskConsumption.spaceAvailable()) {
            Log.d(TAG, "Space warning event triggered.")
            eventHandlingStrategy!!.handleSpaceWarning(this)
        }

        // Filter cached locations from before the measurement started [STAD-140]
        // while handling week-rollover-GPS-bug for old devices [STAD-515].
        // To be able to identify such devices, currently don't fix the timestamp in the locations.
        val isCachedLocation = isCachedLocation(newLocation.timestamp, startupTime)

        // Mark "unclean" locations as invalid and ignore it for distance calculation below
        if (!locationCleaningStrategy!!.isClean(newLocation) || isCachedLocation) {
            newLocation.isValid = false
            informCaller(MessageCodes.LOCATION_CAPTURED, newLocation)
            return
        }

        // Inform listeners
        informCaller(MessageCodes.LOCATION_CAPTURED, newLocation)

        // Skip distance calculation when there is only one location
        if (lastLocation == null) {
            this.lastLocation = newLocation
            return
        }

        // Update {@code Measurement#distance), {@code #lastDistance} and {@code #lastLocation}, in this order
        val distanceToAdd = distanceCalculationStrategy!!.calculateDistance(
            lastLocation!!, newLocation
        )
        val newDistance = lastDistance + distanceToAdd
        try {
            capturingBehaviour!!.updateDistance(newDistance)
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }
        lastDistance = newDistance
        Log.d(TAG, "Distance updated: $distanceToAdd")
        this.lastLocation = newLocation
    }

    override fun onLocationFix() {
        informCaller(MessageCodes.GEOLOCATION_FIX, null)
    }

    override fun onLocationFixLost() {
        informCaller(MessageCodes.NO_GEOLOCATION_FIX, null)
    }

    /**
     * Handles clients which are sending (private!) inter process messages to this service (e.g. the UI thread).
     * - The Handler code runs all on the same (e.g. UI) thread.
     * - We don't use Broadcasts here to reduce the amount of broadcasts.
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 2.0.0
     * @since 1.0.0
     */
    private class MessageHandler(context: DataCapturingBackgroundService) :
        Handler(context.mainLooper) {
        /**
         * A weak reference to the [DataCapturingBackgroundService] responsible for this message
         * handler. The weak reference is necessary to avoid memory leaks if the handler outlives
         * the service.
         *
         *
         * For reference see for example
         * [here](http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html).
         */
        private val context = WeakReference(context)

        override fun handleMessage(msg: Message) {
            Log.v(TAG, "Service received message ${msg.what}")

            val service = context.get()

            when (msg.what) {
                MessageCodes.REGISTER_CLIENT -> {
                    Log.v(TAG, "Registering client!")
                    if (service!!.clients.contains(msg.replyTo)) {
                        Log.w(TAG, "Client " + msg.replyTo + " already registered.")
                    }
                    service.clients.add(msg.replyTo)
                }

                else -> super.handleMessage(msg)
            }
        }
    }

    companion object {
        /**
         * The tag used to identify logging messages send to logcat.
         */
        private const val TAG = Constants.BACKGROUND_TAG

        /**
         * The maximum size of captured data transmitted to clients of this service in one call.
         * If there are more captured points they are split into multiple messages.
         */
        const val MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE: Int = 800

        /**
         * The Cyface notification identifier used to display system notification while the service
         * is running. This needs to be unique for the whole app, so we chose a very unlikely one.
         *
         * This is also the registration number of the starship Voyager.
         */
        private const val NOTIFICATION_ID = 74656

        /**
         * Identifies cached locations from before the measurement started [STAD-140].
         *
         * As there are old devices which have a "week-rollover-GPS-bug" [STAD-515] this method makes
         * sure this bug does not filter all locations.
         *
         * The 1024 week shift is identified by checking for a shift > ~992 weeks.
         *
         * @param gpsTime The Unix timestamp in milliseconds of the GNSS point to check.
         * @param startupTime The Unix timestamp in milliseconds from when the measurement started.
         * @return `true` if the GNSS point is from before the measurement started.
         */
        fun isCachedLocation(gpsTime: Long, startupTime: Long): Boolean {
            val startupTimeGpsTimeOffset = startupTime - gpsTime
            val isWeekRollOverBug = gpsTime > 0 && startupTimeGpsTimeOffset > 600000000000L
            val fixedTime = if (isWeekRollOverBug) gpsTime + 619315200000L else gpsTime
            return fixedTime < startupTime
        }
    }
}
