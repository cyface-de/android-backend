package de.cyface.datacapturing.backend;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;

import static de.cyface.datacapturing.ServiceTestUtils.TAG;

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
     * Creates a new completely initialized <code>ToServiceConnection</code>.
     *
     * @param fromServiceMessenger The <code>Messenger</code> handling messages comming from the <code>DataCapturingBackgroundService</code>.
     */
    public ToServiceConnection(final @NonNull Messenger fromServiceMessenger) {
        this.fromServiceMessenger = fromServiceMessenger;
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

        PongReceiver isRunningChecker = new PongReceiver(context);
        isRunningChecker.asyncIsRunningCheck(1, TimeUnit.MINUTES, callback);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(TAG, "onServiceDisonnected");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.d(TAG, "bindingDied");
    }
}
