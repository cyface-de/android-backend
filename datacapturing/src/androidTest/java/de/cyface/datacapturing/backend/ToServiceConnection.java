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
 * @author Armin Schnabel
 * @version 2.0.0
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
     * The <code>Messenger</code> handling messages coming from the <code>DataCapturingBackgroundService</code>.
     */
    private Messenger fromServiceMessenger;
    /**
     * A device-wide unique identifier for the application containing this SDK such as
     * {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     * <p>
     * <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    private final String appId;

    /**
     * Creates a new completely initialized <code>ToServiceConnection</code>.
     *
     * @param fromServiceMessenger The <code>Messenger</code> handling messages coming from the
     *            <code>DataCapturingBackgroundService</code>.
     * @param appId A device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    ToServiceConnection(@NonNull final Messenger fromServiceMessenger, @NonNull final String appId) {
        this.fromServiceMessenger = fromServiceMessenger;
        this.appId = appId;
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

        PongReceiver isRunningChecker = new PongReceiver(context, appId);
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
