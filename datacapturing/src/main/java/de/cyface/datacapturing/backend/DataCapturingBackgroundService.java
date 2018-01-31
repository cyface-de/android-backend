package de.cyface.datacapturing.backend;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.model.CapturedData;

/**
 *
 * This is the implementation of the data capturing process running in the background while a Cyface measuring is
 * active. The service is started by a caller and sends messages to that caller, informing it about its status.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
public class DataCapturingBackgroundService extends Service implements CapturingProcessListener {

    /*
     * MARK: Properties
     */

    /**
     * The tag used to identify logging messages send to logcat.
     */
    private final static String TAG = "de.cyface.datacapturing";
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
     * The list of clients receiving messages from this service as well as sending controll messages.
     */
    private final List<Messenger> clients = new ArrayList<>();
    /**
     * A <code>CapturingProcess</code> implementation which is responsible for actual data capturing.
     */
    private CapturingProcess dataCapturing;

    /*
     * MARK: Service Lifecycle Methods
     */

    @Override
    public IBinder onBind(final Intent intent) {
        Log.d(TAG, String.format("Binding to %s", this.getClass().getName()));
        return callerMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        Log.d(TAG, "Unbinding from data capturing service.");
        return true; // I want to receive calls to onRebind
    }

    @Override
    public void onRebind(final Intent intent) {
        Log.d(TAG, "Rebinding to data capturing service.");
        super.onRebind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // Prevent this process from being killed by the system.
        PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "de.cyface.wakelock");
        wakeLock.acquire();

        Log.d(TAG, "finishedOnCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (dataCapturing != null) {
            dataCapturing.close();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.d(TAG, "Starting data capturing service.");
        // TODO old service checks if init has been called before? Why? Is this necessary?
        if (intent != null) { // If this is the initial start command call init.
            init();
        }
        return Service.START_STICKY;
    }

    /*
     * MARK: Methods
     */

    /**
     * Initializes this service when it is first started. Since the initialising {@code Intent} sometimes comes with
     * onBind and sometimes with
     * onStartCommand and since the {@code Intent} contains the details about the Bluetooth setup,
     * this method makes sure it is only called once and only if the correct {@code Intent} is
     * available.
     */
    private void init() {
        LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        GeoLocationDeviceStatusHandler gpsStatusHandler = Build.VERSION_CODES.N <= Build.VERSION.SDK_INT
                ? new GnssStatusCallback(locationManager)
                : new GPSStatusListener(locationManager);
        SensorManager sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        dataCapturing = new GPSCapturingProcess(locationManager, sensorManager, gpsStatusHandler);

        dataCapturing.addCapturingProcessListener(this);
    }

    /**
     * This method sends an inter process communication (IPC) message to all callers of this service.
     *
     * @param messageCode A code identifying the message that is send. See {@link MessageCodes} for further details.
     * @param data The data to send appended to this message. This may be <code>null</code> if no data needs to be send.
     */
    private void informCaller(final int messageCode, final Parcelable data) {
        Message msg = Message.obtain(null, messageCode);

        if (data != null) {
            Bundle dataBundle = new Bundle();
            dataBundle.putParcelable("data", data);
            msg.setData(dataBundle);
        }

        for (Messenger caller : clients) {
            try {
                caller.send(msg);
            } catch (RemoteException e) {
                Log.w(TAG,
                        String.format("Unable to send message (%s) to caller %s due to exception: %s", msg, caller, e));
            }
        }
    }

    /*
     * MARK: CapturingProcessListener Interface
     */

    @Override
    public void onPointCaptured(final CapturedData data) {
        informCaller(MessageCodes.POINT_CAPTURED, data);
    }

    @Override
    public void onGpsFix() {
        informCaller(MessageCodes.GPS_FIX, null);
    }

    @Override
    public void onGpsFixLost() {
        informCaller(MessageCodes.NO_GPS_FIX, null);
    }

    /**
     * Handles clients which are sending (private!) inter process messages to this service (e.g. the UI thread).
     * - The Handler code runs all on the same (e.g. UI) thread.
     * - We don't use Broadcasts here to reduce the amount of broadcasts.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
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
        MessageHandler(final DataCapturingBackgroundService context) {
            if (context == null) {
                throw new IllegalArgumentException("Illegal argument: context was null!");
            }

            this.context = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(final Message msg) {
            Log.d(TAG, String.format("Service received message %s", msg.what));
            if (msg == null) {
                throw new IllegalArgumentException("Illegal argument: msg was null!");
            }

            DataCapturingBackgroundService service = context.get();

            switch (msg.what) {
                case MessageCodes.REGISTER_CLIENT:
                    Log.d(TAG, "Registering client!");
                    if (service.clients.contains(msg.replyTo)) {
                        throw new IllegalStateException("Client " + msg.replyTo + " already registered.");
                    }
                    service.clients.add(msg.replyTo);
                    break;
                case MessageCodes.UNREGISTER_CLIENT:
                    Log.d(TAG, "Unregistering client!");
                    if (!service.clients.contains(msg.replyTo)) {
                        throw new IllegalStateException("Tried to unregister not registered client " + msg.replyTo);
                    }
                    service.clients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
