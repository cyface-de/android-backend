package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.BundlesExtrasCodes.AUTHORITY_ID;
import static de.cyface.datacapturing.BundlesExtrasCodes.EVENT_HANDLING_STRATEGY_ID;
import static de.cyface.datacapturing.BundlesExtrasCodes.MEASUREMENT_ID;
import static de.cyface.datacapturing.BundlesExtrasCodes.STOPPED_SUCCESSFULLY;
import static de.cyface.datacapturing.DiskConsumption.spaceAvailable;
import static de.cyface.datacapturing.MessageCodes.ACTION_PING;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import de.cyface.datacapturing.BuildConfig;
import de.cyface.datacapturing.BundlesExtrasCodes;
import de.cyface.datacapturing.DataCapturingService;
import de.cyface.datacapturing.EventHandlingStrategy;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.model.GeoLocation;
import de.cyface.datacapturing.model.Point3D;
import de.cyface.datacapturing.persistence.MeasurementPersistence;
import de.cyface.datacapturing.persistence.WritingDataCompletedCallback;
import de.cyface.datacapturing.ui.CapturingNotification;
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
 * @version 4.0.10
 * @since 2.0.0
 */
public class DataCapturingBackgroundService extends Service implements CapturingProcessListener {
    /**
     * The tag used to identify logging messages send to logcat.
     */
    private final static String TAG = "de.cyface.background";
    /**
     * The maximum size of captured data transmitted to clients of this service in one call. If there are more captured
     * points they are split into multiple messages.
     */
    final static int MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE = 800;
    /**
     * Reference to the R files identifier for the notification channel name.
     */
    private final static int NOTIFICATION_CHANNEL_ID = BuildConfig.NOTIFICATION_CHANNEL;
    /**
     * Reference to the R files identifier for the notification title.
     */
    private final static int NOTIFICATION_TITLE_ID = BuildConfig.NOTIFICATION_TITLE;
    /**
     * Reference to the R files identifier for the notification text.
     */
    private final static int NOTIFICATION_TEXT_ID = BuildConfig.NOTIFICATION_TEXT;
    /**
     * Reference to the R files identifier for the notification logo.
     */
    private final static int NOTIFICATION_LOGO_ID = BuildConfig.NOTIFICATION_LOGO;
    // TODO: Add this! But not used for the moment.
    /**
     * Reference to the R files identifier for the large logo shown as part of the notification.
     */
    private final static int NOTIFICATION_LARGE_LOGO_ID = 0;
    /**
     * A wake lock used to keep the application active during data capturing.
     */
    private PowerManager.WakeLock wakeLock;
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
     * A <code>CapturingProcess</code> implementation which is responsible for actual data capturing.
     */
    private CapturingProcess dataCapturing;
    /**
     * A facade handling reading and writing data from and to the Android content provider used to store and retrieve
     * measurement data.
     */
    private MeasurementPersistence persistenceLayer;
    /**
     * Receiver for pings to the service. The receiver answers with a pong as long as this service is running.
     */
    private PingReceiver pingReceiver = new PingReceiver();
    /**
     * The identifier of the measurement to save all the captured data to.
     */
    private long currentMeasurementIdentifier;
    /**
     * The strategy used to respond to selected events triggered by this service.
     */
    private EventHandlingStrategy eventHandlingStrategy;

    /*
     * MARK: Service Lifecycle Methods
     */

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG, String.format("Binding to %s", this.getClass().getName()));
        return callerMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(final @NonNull Intent intent) {
        Log.d(TAG, "Unbinding from data capturing service.");
        return true; // I want to receive calls to onRebind
    }

    @Override
    public void onRebind(final @NonNull Intent intent) {
        Log.d(TAG, "Rebinding to data capturing service.");
        super.onRebind(intent);
    }

    @SuppressLint("WakelockTimeout") // We can not provide a timeout since our service might need to run for hours.
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        /*
         * Notification shown to the user while the data capturing is active.
         * This must be placed in onCreate or it will be called to late from time to time!
         */
        final CapturingNotification capturingNotification = new CapturingNotification(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_TITLE_ID, NOTIFICATION_TEXT_ID, NOTIFICATION_LOGO_ID, NOTIFICATION_LARGE_LOGO_ID);
        startForeground(capturingNotification.getNotificationId(), capturingNotification.getNotification(this));

        // Prevents this process from being killed by the system.
        final PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "de.cyface.wakelock");
            wakeLock.acquire();
        } else {
            Log.w(TAG, "Unable to acquire PowerManager. No wake lock set!");
        }

        // Allows other parties to ping this service to see if it is running
        Log.v(TAG, "Registering Ping Receiver");
        registerReceiver(pingReceiver, new IntentFilter(ACTION_PING));
        Log.d(TAG, "finishedOnCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        Log.v(TAG, "Unregistering Ping receiver.");
        unregisterReceiver(pingReceiver);
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
        sendStoppedMessage(currentMeasurementIdentifier);
    }

    /**
     * Sends an IPC message to interested parties that the service stopped successfully.
     * 
     * @param currentMeasurementIdentifier The id of the measurement which was stopped.
     */
    private void sendStoppedMessage(final long currentMeasurementIdentifier) {
        Log.v(TAG, "Sending IPC message: service stopped.");
        // Attention: the bundle is bundled again by informCaller !
        final Bundle bundle = new Bundle();
        bundle.putLong(MEASUREMENT_ID, currentMeasurementIdentifier);
        bundle.putBoolean(STOPPED_SUCCESSFULLY, true);
        informCaller(MessageCodes.SERVICE_STOPPED, bundle);
    }

    /**
     * Sends an IPC message to interested parties that the service stopped itself. This must be called
     * when the {@code stopSelf()} method is called on this service to unbind the {@link DataCapturingService},
     * e.g. from an {@link EventHandlingStrategy} implementation.
     */
    public void sendStoppedItselfMessage() {
        Log.v(TAG, "Sending IPC message: service stopped itself.");
        // Attention: the bundle is bundled again by informCaller !
        final Bundle bundle = new Bundle();
        bundle.putLong(MEASUREMENT_ID, currentMeasurementIdentifier);
        informCaller(MessageCodes.SERVICE_STOPPED_ITSELF, bundle);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.d(TAG, "Starting DataCapturingBackgroundService.");

        if (intent != null) { // i.e. this is the initial start command call init.
            // Loads measurement id
            final long measurementIdentifier = intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1);
            if (measurementIdentifier == -1) {
                throw new IllegalStateException("No valid measurement identifier for started service provided.");
            }
            this.currentMeasurementIdentifier = measurementIdentifier;

            // Loads authority / persistence layer
            if (!intent.hasExtra(AUTHORITY_ID)) {
                throw new IllegalStateException(
                        "Unable to start data capturing service without a valid content provider authority. Please provide one as extra to the starting intent using the extra identifier: "
                                + AUTHORITY_ID);
            }
            final String authority = intent.getCharSequenceExtra(AUTHORITY_ID).toString();
            persistenceLayer = new MeasurementPersistence(this.getContentResolver(), authority);

            // Loads EventHandlingStrategy
            this.eventHandlingStrategy = intent.getParcelableExtra(EVENT_HANDLING_STRATEGY_ID);
            Validate.notNull(eventHandlingStrategy);

            // Init capturing process
            dataCapturing = initializeCapturingProcess();
            dataCapturing.addCapturingProcessListener(this);
        }

        // Informs about the service start
        Log.v(TAG, "Sending broadcast service started.");
        final Intent serviceStartedIntent = new Intent(MessageCodes.BROADCAST_SERVICE_STARTED);
        serviceStartedIntent.putExtra(MEASUREMENT_ID, currentMeasurementIdentifier);
        LocalBroadcastManager.getInstance(this).sendBroadcast(serviceStartedIntent);
        return Service.START_STICKY;
    }

    /*
     * MARK: Methods
     */

    /**
     * Initializes this service
     *
     * @return the {@link GPSCapturingProcess}
     */
    private GPSCapturingProcess initializeCapturingProcess() {
        Log.d(TAG, "Initializing capturing process");
        final LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        final GeoLocationDeviceStatusHandler gpsStatusHandler = Build.VERSION_CODES.N <= Build.VERSION.SDK_INT
                ? new GnssStatusCallback(locationManager)
                : new GPSStatusListener(locationManager);
        final SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        return new GPSCapturingProcess(locationManager, sensorManager, gpsStatusHandler);
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
        final List<Point3D> accelerations = data.getAccelerations();
        final List<Point3D> rotations = data.getRotations();
        final List<Point3D> directions = data.getDirections();
        final int iterationSize = Math.max(accelerations.size(), Math.max(directions.size(), rotations.size()));
        for (int i = 0; i < iterationSize; i += MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE) {

            final CapturedData dataSublist = new CapturedData(sampleSubList(accelerations, i),
                    sampleSubList(rotations, i), sampleSubList(directions, i));
            informCaller(MessageCodes.DATA_CAPTURED, dataSublist);
            persistenceLayer.storeData(dataSublist, currentMeasurementIdentifier, new WritingDataCompletedCallback() {
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
     * @param completeList The {@link List<Point3D>} to extract a subset from
     * @param fromIndex The low endpoint (inclusive) of the subList
     * @return The extracted sublist
     */
    private @NonNull List<Point3D> sampleSubList(final @NonNull List<Point3D> completeList, final int fromIndex) {
        final int endIndex = fromIndex + MAXIMUM_CAPTURED_DATA_MESSAGE_SIZE;
        final int toIndex = Math.min(endIndex, completeList.size());
        return (fromIndex >= toIndex) ? Collections.<Point3D> emptyList() : completeList.subList(fromIndex, toIndex);
    }

    @Override
    public void onLocationCaptured(final @NonNull GeoLocation location) {
        Log.d(TAG, "Location captured");
        informCaller(MessageCodes.LOCATION_CAPTURED, location);
        persistenceLayer.storeLocation(location, currentMeasurementIdentifier);

        if (!spaceAvailable()) {
            Log.d(TAG, "Space warning event triggered.");
            eventHandlingStrategy.handleSpaceWarning(this);
        }
    }

    @Override
    public void onLocationFix() {
        informCaller(MessageCodes.GPS_FIX, null);
    }

    @Override
    public void onLocationFixLost() {
        informCaller(MessageCodes.NO_GPS_FIX, null);
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
            Log.d(TAG, String.format("Service received message %s", msg.what));

            final DataCapturingBackgroundService service = context.get();

            switch (msg.what) {
                case MessageCodes.REGISTER_CLIENT:
                    Log.d(TAG, "Registering client!");
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
