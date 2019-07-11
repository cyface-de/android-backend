/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.Constants.BACKGROUND_TAG;
import static de.cyface.datacapturing.DiskConsumption.spaceAvailable;
import static de.cyface.synchronization.BundlesExtrasCodes.AUTHORITY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.DISTANCE_CALCULATION_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.LOCATION_CLEANING_STRATEGY_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.synchronization.BundlesExtrasCodes.STOPPED_SUCCESSFULLY;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.DataCapturingService;
import de.cyface.datacapturing.EventHandlingStrategy;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.StartUpFinishedHandler;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.persistence.DistanceCalculationStrategy;
import de.cyface.persistence.LocationCleaningStrategy;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.synchronization.BundlesExtrasCodes;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * This is the implementation of the data capturing process running in the background while a Cyface measuring is
 * active. The service is started by a caller and sends messages to that caller, informing it about its status.
 *
 * The DataCapturingBackgroundService hides the complexity of the DataCapturingService in order to
 * hide most of the inter process communication from the SDK user who interact with the DataCapturingService.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.1
 * @since 2.0.0
 */
public class DataCapturingBackgroundService extends Service implements CapturingProcessListener {

    /**
     * The tag used to identify logging messages send to logcat.
     */
    private static final String TAG = BACKGROUND_TAG;
    /**
     * The maximum size of captured data transmitted to clients of this service in one call. If there are more captured
     * points they are split into multiple messages.
     */
    final static int MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE = 800;
    /**
     * The Cyface notification identifier used to display system notification while the service is running. This needs
     * to be unique for the whole app, so we chose a very unlikely one.
     *
     * This is also the registration number of the starship Voyager.
     */
    private static final int NOTIFICATION_ID = 74656;
    /**
     * The Android <code>Messenger</code> used to send IPC messages, informing the caller about the current status of
     * data capturing.
     */
    private final Messenger callerMessenger = new Messenger(new MessageHandler(this));
    /**
     * The list of clients receiving messages from this service as well as sending control messages.
     */
    private final Set<Messenger> clients = new HashSet<>();
    /**
     * A wake lock used to keep the application active during data capturing.
     */
    private PowerManager.WakeLock wakeLock;
    /**
     * A <code>CapturingProcess</code> implementation which is responsible for actual data capturing.
     */
    private CapturingProcess dataCapturing;
    /**
     * A facade handling reading and writing data from and to the Android content provider used to store and retrieve
     * measurement data.
     */
    PersistenceLayer<CapturingPersistenceBehaviour> persistenceLayer;
    /**
     * Receiver for pings to the service. The receiver answers with a pong as long as this service is running.
     */
    private PingReceiver pingReceiver = null;
    /**
     * The identifier of the measurement to save all the captured data to.
     */
    private long currentMeasurementIdentifier;
    /**
     * The strategy used to respond to selected events triggered by this service.
     */
    EventHandlingStrategy eventHandlingStrategy;
    /**
     * The strategy used to calculate the {@link Measurement#getDistance()} from {@link GeoLocation} pairs
     */
    DistanceCalculationStrategy distanceCalculationStrategy;
    /**
     * The strategy used to filter the received {@link GeoLocation}s
     */
    LocationCleaningStrategy locationCleaningStrategy;
    /**
     * This {@link PersistenceBehaviour} is used to capture a {@link Measurement}s with when a {@link PersistenceLayer}.
     */
    CapturingPersistenceBehaviour capturingBehaviour;
    /**
     * The last captured {@link GeoLocation} used to calculate the distance to the next {@code GeoLocation}.
     */
    private GeoLocation lastLocation = null;
    /**
     * The {@link Measurement#getDistance()} in meters until the last location update.
     */
    private double lastDistance;
    /**
     * The unix timestamp in milliseconds capturing the start of this service (i.e. of the tracking)
     * to filter out cached locations from distance calculation (STAD-140).
     */
    private long startupTime;

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.v(TAG, "onBind");

        return callerMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(final @NonNull Intent intent) {
        Log.v(TAG, "Unbinding from data capturing service.");
        return true; // I want to receive calls to onRebind
    }

    @Override
    public void onRebind(final @NonNull Intent intent) {
        Log.v(TAG, "Rebinding to data capturing service.");
        super.onRebind(intent);
    }

    @SuppressLint("WakelockTimeout") // We can not provide a timeout since our service might need to run for hours.
    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");
        // We only have 5 seconds to call startForeground before the service crashes, so we call it as early as possible
        // with a placeholder notification. This is substituted by the provided notification in onStartCommand. On most
        // devices the user should not even see this happening.
        startForeground(NOTIFICATION_ID, PlaceholderNotificationBuilder.build(this));
        super.onCreate();

        // Prevents this process from being killed by the system.
        final PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "de.cyface:wakelock");
            wakeLock.acquire();
        } else {
            Log.w(TAG, "Unable to acquire PowerManager. No wake lock set!");
        }

        // We must register the receiver as soon as possible - onBind and onStartCommand are too late (race condition)
        if (pingReceiver != null) {
            Log.v(TAG, "onBind: Ping Receiver was already registered");
            return;
        }

        // Allows other parties to ping this service to see if it is running
        // We cannot use the deviceId as device-unique app identifier as we need the authority (persistence) for this
        // which we cannot pass via bind() as documented by the {@link #onBind()} method.
        final String appId = getBaseContext().getPackageName();
        pingReceiver = new PingReceiver(appId);
        registerReceiver(pingReceiver, new IntentFilter(MessageCodes.getPingActionId(appId)));
        Log.d(TAG, "onCreate: Ping Receiver registered");

        startupTime = System.currentTimeMillis();
        Log.v(TAG, "finishedOnCreate");
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        Log.v(TAG, "onDestroy: Unregistering Ping receiver.");
        unregisterReceiver(pingReceiver);
        pingReceiver = null;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (dataCapturing != null) {
            dataCapturing.close();
        }
        if (persistenceLayer != null) {
            persistenceLayer.shutdown();
        }

        // OnDestroy is called before the messages below to make sure it's semantic is right (stopped)
        super.onDestroy();
        sendStoppedMessage();
    }

    /**
     * Sends an IPC message to interested parties that the service stopped successfully.
     */
    private void sendStoppedMessage() {
        Log.v(TAG, "Sending IPC message: service stopped.");

        // Attention: the bundle is bundled again by informCaller !
        final Bundle bundle = new Bundle();
        bundle.putLong(MEASUREMENT_ID, currentMeasurementIdentifier);
        bundle.putBoolean(STOPPED_SUCCESSFULLY, true);
        informCaller(MessageCodes.SERVICE_STOPPED, bundle);
    }

    /**
     * Sends an IPC message to interested parties that the service stopped itself.
     * <p>
     * This method must be called when {@link #stopSelf()} is called on this service without a prior intent
     * from the {@link DataCapturingService}. It must be called e.g. from an
     * {@link EventHandlingStrategy#handleSpaceWarning(DataCapturingBackgroundService)} implementation
     * to unbind the {@link DataCapturingBackgroundService} from the {@link DataCapturingService}.
     * <p>
     * Attention: This method is very rarely executed and so be careful when you change it's logic.
     * The task for the missing test is CY-4111. Currently only tested manually.
     */
    @SuppressWarnings("unused") // Because must be callable by custom {@link EventHandlingStrategy} implementations
    public void sendStoppedItselfMessage() {
        Log.v(TAG, "Sending IPC message: service stopped itself.");
        // Attention: the bundle is bundled again by informCaller !
        final Bundle bundle = new Bundle();
        bundle.putLong(MEASUREMENT_ID, currentMeasurementIdentifier);
        informCaller(MessageCodes.SERVICE_STOPPED_ITSELF, bundle);
    }

    /**
     * We don't use {@link #startService(Intent)} to pause or stop the service because we prefer to use a lock and
     * finally in the {@link DataCapturingService}'s life-cycle methods instead. This also avoids headaches with
     * replacing the return statement from {@link #stopService(Intent)} by the return by {@code #startService()}.
     *
     * For the remaining documentation see the overwritten {@code #onStartCommand(Intent, int, int)}
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Validate.notNull("The process should not be automatically recreated without START_STICKY!", intent);
        Log.v(TAG, "onStartCommand: Starting DataCapturingBackgroundService");

        // Loads authority / persistence layer
        if (!intent.hasExtra(AUTHORITY_ID)) {
            throw new IllegalStateException(
                    "Unable to start data capturing service without a valid content provider authority. Please provide one as extra to the starting intent using the extra identifier: "
                            + AUTHORITY_ID);
        }
        final String authority = intent.getCharSequenceExtra(AUTHORITY_ID).toString();
        capturingBehaviour = new CapturingPersistenceBehaviour();
        persistenceLayer = new PersistenceLayer<>(this, this.getContentResolver(), authority, capturingBehaviour);

        // Loads EventHandlingStrategy
        this.eventHandlingStrategy = intent.getParcelableExtra(EVENT_HANDLING_STRATEGY_ID);
        Validate.notNull(eventHandlingStrategy);
        final Notification notification = eventHandlingStrategy.buildCapturingNotification(this);
        final NotificationManager notificationManager = (NotificationManager)getSystemService(
                Context.NOTIFICATION_SERVICE);
        // Update the placeholder notification
        Validate.notNull(notificationManager);
        notificationManager.notify(NOTIFICATION_ID, notification);

        // Loads DistanceCalculationStrategy
        this.distanceCalculationStrategy = intent.getParcelableExtra(DISTANCE_CALCULATION_STRATEGY_ID);
        Validate.notNull(distanceCalculationStrategy);

        // Loads LocationCleaningStrategy
        this.locationCleaningStrategy = intent.getParcelableExtra(LOCATION_CLEANING_STRATEGY_ID);
        Validate.notNull(locationCleaningStrategy);

        // Loads measurement id
        final long measurementIdentifier = intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1);
        if (measurementIdentifier == -1) {
            throw new IllegalStateException("No valid measurement identifier provided for started service .");
        }
        this.currentMeasurementIdentifier = measurementIdentifier;

        // Load Distance (or else we would reset the distance when resuming a measurement)
        try {
            final Measurement measurement = persistenceLayer.loadMeasurement(currentMeasurementIdentifier);
            lastDistance = measurement.getDistance();

            // Ensure we resume measurements with a known file format version
            final short persistenceFileFormatVersion = measurement.getFileFormatVersion();
            Validate.isTrue(persistenceFileFormatVersion == MeasurementSerializer.PERSISTENCE_FILE_FORMAT_VERSION,
                    "Resume a measurement of a previous persistence file format version is not yet supported!");
        } catch (final CursorIsNullException e) {
            // because onStartCommand is called by Android so we can't throw soft exception.
            throw new IllegalStateException(e);
        }

        // Load sensor frequency
        final int sensorFrequency = intent.getIntExtra(BundlesExtrasCodes.SENSOR_FREQUENCY, -1);
        if (sensorFrequency == -1) {
            throw new IllegalStateException("No sensor frequency provided for started service .");
        }

        // Init capturing process
        dataCapturing = initializeCapturingProcess(sensorFrequency);
        dataCapturing.addCapturingProcessListener(this);

        // Informs about the service start
        Log.d(StartUpFinishedHandler.TAG,
                "DataCapturingBackgroundService.onStartCommand: Sending broadcast service started.");
        final String appId = getBaseContext().getPackageName();
        final Intent serviceStartedIntent = new Intent(MessageCodes.getServiceStartedActionId(appId));
        serviceStartedIntent.putExtra(MEASUREMENT_ID, currentMeasurementIdentifier);
        sendBroadcast(serviceStartedIntent);

        // NOT_STICKY to avoid recreation of the process which could mess up the life-cycle
        return Service.START_NOT_STICKY;
    }

    /*
     * MARK: Methods
     */

    /**
     * Initializes this service
     *
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @return the {@link GeoLocationCapturingProcess}
     */
    private GeoLocationCapturingProcess initializeCapturingProcess(final int sensorFrequency) {
        Log.v(TAG, "Initializing capturing process");
        final LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        Validate.notNull(locationManager);
        final GeoLocationDeviceStatusHandler locationStatusHandler = Build.VERSION_CODES.N <= Build.VERSION.SDK_INT
                ? new GnssStatusCallback(locationManager)
                : new GeoLocationStatusListener(locationManager);
        final SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Validate.notNull(sensorManager);
        final HandlerThread geoLocationEventHandlerThread = new HandlerThread("de.cyface.locationhandler");
        final HandlerThread sensorEventHandlerThread = new HandlerThread("de.cyface.sensoreventhandler");
        return new GeoLocationCapturingProcess(locationManager, sensorManager, locationStatusHandler,
                geoLocationEventHandlerThread, sensorEventHandlerThread, sensorFrequency);
    }

    /**
     * This method sends an inter process communication (IPC) message to all callers of this service.
     *
     * @param messageCode A code identifying the message that is send. See {@link MessageCodes} for further details.
     * @param data The data to send appended to this message. This may be <code>null</code> if no data needs to be send.
     */
    void informCaller(final int messageCode, final Parcelable data) {
        final Message msg = Message.obtain(null, messageCode);

        if (data != null) {
            final Bundle dataBundle = new Bundle();
            dataBundle.putParcelable("data", data);
            msg.setData(dataBundle);
        }

        Log.v(TAG, String.format("Sending message %d to %d callers.", messageCode, clients.size()));
        final Set<Messenger> temporaryCallerSet = new HashSet<>(clients);
        for (final Messenger caller : temporaryCallerSet) {
            try {
                caller.send(msg);
            } catch (final RemoteException e) {
                Log.w(TAG, String.format("Unable to send message (%s) to caller %s!", msg, caller), e);
                clients.remove(caller);
            } catch (final NullPointerException e) {
                // Caller may be null in a typical React Native application.
                Log.w(TAG, String.format("Unable to send message (%s) to null caller!", msg), e);
                clients.remove(caller);
            }
        }
    }

    /*
     * MARK: CapturingProcessListener Interface
     */

    @Override
    public void onDataCaptured(final @NonNull CapturedData data) {
        final List<Point3d> accelerations = data.getAccelerations();
        final List<Point3d> rotations = data.getRotations();
        final List<Point3d> directions = data.getDirections();
        final int iterationSize = Math.max(accelerations.size(), Math.max(directions.size(), rotations.size()));
        for (int i = 0; i < iterationSize; i += MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE) {

            final CapturedData dataSublist = new CapturedData(sampleSubList(accelerations, i),
                    sampleSubList(rotations, i), sampleSubList(directions, i));
            informCaller(MessageCodes.DATA_CAPTURED, dataSublist);
            capturingBehaviour.storeData(dataSublist, currentMeasurementIdentifier, new WritingDataCompletedCallback() {
                @Override
                public void writingDataCompleted() {
                    // Nothing to do here!
                }
            });
        }
    }

    /**
     * Extracts a subset of maximal {@code MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE} elements of captured data.
     *
     * @param completeList The {@link List<Point3d>} to extract a subset from
     * @param fromIndex The low endpoint (inclusive) of the subList
     * @return The extracted sublist
     */
    private @NonNull List<Point3d> sampleSubList(final @NonNull List<Point3d> completeList, final int fromIndex) {
        final int endIndex = fromIndex + MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
        final int toIndex = Math.min(endIndex, completeList.size());
        return (fromIndex >= toIndex) ? Collections.<Point3d> emptyList() : completeList.subList(fromIndex, toIndex);
    }

    @Override
    public void onLocationCaptured(@NonNull final GeoLocation newLocation) {

        // Store raw, unfiltered track
        Log.d(TAG, "Location captured");
        capturingBehaviour.storeLocation(newLocation, currentMeasurementIdentifier);

        // Check available space
        if (!spaceAvailable()) {
            Log.d(TAG, "Space warning event triggered.");
            eventHandlingStrategy.handleSpaceWarning(this);
        }

        // Mark "unclean" locations as invalid and ignore it for distance calculation below
        if (!locationCleaningStrategy.isClean(newLocation) || newLocation.getTimestamp() < startupTime) {
            newLocation.setValid(false);
            informCaller(MessageCodes.LOCATION_CAPTURED, newLocation);
            return;
        }

        // Inform listeners
        informCaller(MessageCodes.LOCATION_CAPTURED, newLocation);

        // Skip distance calculation when there is only one location
        if (lastLocation == null) {
            this.lastLocation = newLocation;
            return;
        }

        // Update {@code Measurement#distance), {@code #lastDistance} and {@code #lastLocation}, in this order
        final double distanceToAdd = distanceCalculationStrategy.calculateDistance(lastLocation, newLocation);
        final double newDistance = lastDistance + distanceToAdd;
        try {
            capturingBehaviour.updateDistance(newDistance);
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        lastDistance = newDistance;
        Log.d(TAG, "Distance updated: " + distanceToAdd);
        this.lastLocation = newLocation;
    }

    @Override
    public void onLocationFix() {
        informCaller(MessageCodes.GEOLOCATION_FIX, null);
    }

    @Override
    public void onLocationFixLost() {
        informCaller(MessageCodes.NO_GEOLOCATION_FIX, null);
    }

    /**
     * Handles clients which are sending (private!) inter process messages to this service (e.g. the UI thread).
     * - The Handler code runs all on the same (e.g. UI) thread.
     * - We don't use Broadcasts here to reduce the amount of broadcasts.
     *
     * @author Klemens Muthmann
     * @version 1.0.1
     * @since 1.0.0
     */
    private final static class MessageHandler extends Handler {
        /**
         * A weak reference to the {@link DataCapturingBackgroundService} responsible for this message
         * handler. The weak reference is necessary to avoid memory leaks if the handler outlives
         * the service.
         * <p>
         * For reference see for example
         * <a href="http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html">here</a>.
         */
        private final WeakReference<DataCapturingBackgroundService> context;

        /**
         * Creates a new completely initialized {@link MessageHandler} for messages to this
         * service.
         *
         * @param context The {@link DataCapturingBackgroundService} receiving messages via this handler.
         */
        MessageHandler(final @NonNull DataCapturingBackgroundService context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(final @NonNull Message msg) {
            Log.v(TAG, String.format("Service received message %s", msg.what));

            final DataCapturingBackgroundService service = context.get();

            // noinspection SwitchStatementWithTooFewBranches
            switch (msg.what) {
                case MessageCodes.REGISTER_CLIENT:
                    Log.v(TAG, "Registering client!");
                    if (service.clients.contains(msg.replyTo)) {
                        Log.w(TAG, "Client " + msg.replyTo + " already registered.");
                    }
                    service.clients.add(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
