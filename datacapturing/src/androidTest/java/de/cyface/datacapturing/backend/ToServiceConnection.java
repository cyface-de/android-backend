package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.TestUtils.TAG;

import java.util.concurrent.TimeUnit;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;

/**
 * Connection from the test to the capturing service.
 *
 * @author Klemens Muthmann
 * @version 1.1.4
 * @since 2.0.0
 */
class ToServiceConnection implements ServiceConnection {
    /**
     * The context this <code>ServiceConnection</code> runs with.
     */
    Context context;
    /**
     * Callback used to check the success or non success of the service startup.
     */
    TestCallback callback;
    /**
     * The <code>Messenger</code> handling messages comming from the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger fromServiceMessenger;
    /**
     * The device id used to generate an unique broadcast id
     */
    private final String deviceId;

    /**
     * Creates a new completely initialized <code>ToServiceConnection</code>.
     *
     * @param fromServiceMessenger The <code>Messenger</code> handling messages comming from the
     *            <code>DataCapturingBackgroundService</code>.
     * @param deviceId The device id used to generate an unique broadcast id
     */
    ToServiceConnection(final @NonNull Messenger fromServiceMessenger, @NonNull final String deviceId) {
        this.fromServiceMessenger = fromServiceMessenger;
        this.deviceId = deviceId;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(TAG, "onServiceConnected");
        /*
         * The messenger used to send messages to the data capturing service.
         */
        Messenger toServiceMessenger = new Messenger(iBinder);

        try {
            Message msg = Message.obtain(null, MessageCodes.REGISTER_CLIENT);
            msg.replyTo = fromServiceMessenger;
            toServiceMessenger.send(msg);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }

        PongReceiver isRunningChecker = new PongReceiver(context, deviceId);
        isRunningChecker.checkIsRunningAsync(1, TimeUnit.MINUTES, callback);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(TAG, "onServiceDisconnected");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.d(TAG, "bindingDied");
    }
}
