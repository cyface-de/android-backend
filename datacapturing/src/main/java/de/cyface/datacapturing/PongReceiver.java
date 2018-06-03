package de.cyface.datacapturing;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * A <code>BroadcastReceiver</code> and sender that send a <code>MessageCodes.PING</code> event to the system and
 * expects to receive a <code>MessageCodes.PONG</code> event if the background service is running. If not a timeout will
 * tell the caller, that the service is not running.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
public class PongReceiver extends BroadcastReceiver {

    /**
     * The tag used to identify messages in Logcat.
     */
    private static final String TAG = "de.cyface.capturing";
    /**
     * The callback called if either the <code>MessageCodes.PONG</code> event has been received or the timeout was
     * reached.
     */
    private IsRunningCallback callback;
    /**
     * Flag that is set if the <code>MessageCodes.PONG</code> event was received. This flag is required to synchronize
     * with
     * the timeout if both happen simulatenously.
     */
    private boolean isRunning;
    /**
     * Flag that is set if the <code>MessageCodes.PONG</code> event was not received. This flag is required to
     * synchronize with the isRunning handler if both happen simultaneously.
     */
    private boolean isTimedOut;
    /**
     * A <code>Handler</code> that manages to call the timeout callback after the timeout has passed.
     */
    private final Handler timeoutHandler;
    /**
     * Lock used to synchronize the timeout and the isRunning callback if both happen to happen simultaneously.
     */
    private final Lock lock;
    /**
     * Current context used to send and receive broadcasts.
     */
    private final Context context;

    /**
     * Creates a new completely <code>PongReceiver</code> for a certain context.
     *
     * @param context The context to use to send and receive broadcast messages.
     */
    public PongReceiver(final @NonNull Context context) {
        timeoutHandler = new Handler();
        lock = new ReentrantLock();
        this.context = context;
        isRunning = false;
        isTimedOut = false;
    }

    /**
     * Sends the <code>MessageCodes.PING</code> message to the system and waits for the timeout to occur or the service
     * to answer with a <code>MessageCodes.PONG</code>.
     *
     * @param timeout The time to wait for the <code>MessageCodes.PONG</code> in the specified unit.
     * @param unit The unit of the <code>timeout</code>.
     * @param callback The callback to inform about either the timeout or the successful reception of the
     *            <code>MessageCodes.PONG</code> message.
     */
    public void pongAndReceive(final long timeout, final @NonNull TimeUnit unit,
            final @NonNull IsRunningCallback callback) {
        this.callback = callback;
        context.registerReceiver(this, new IntentFilter(MessageCodes.ACTION_PONG));
        long currentUptimeInMillis = SystemClock.uptimeMillis();
        long offset = unit.toMillis(timeout);
        Log.v(TAG, "PongReceiver.pongAndReceive(): currentUptimeInMillis is " + currentUptimeInMillis);
        Log.v(TAG, "PongReceiver.pongAndReceive(): offest is " + offset);
        context.sendBroadcast(new Intent(MessageCodes.ACTION_PING));
        Log.v(TAG, "PongReceiver.pongAndReceive(): Ping was sent!");
        timeoutHandler.postAtTime(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "PongReceiver.pongAndReceive(): timeout reached after " + unit.toMillis(timeout)
                        + " milliseconds. Executed at: " + SystemClock.uptimeMillis());
                lock.lock();
                try {
                    if (!isRunning) {
                        Log.d(TAG, "PongReceiver.pongAndReceive(): Service seems not to be running. Timing out!");
                        PongReceiver.this.callback.timedOut();
                        isTimedOut = true;
                        context.unregisterReceiver(PongReceiver.this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }, currentUptimeInMillis + offset);
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Log.d(TAG, "PongReceiver.onReceive(): Received Pong Event.");
        lock.lock();
        try {
            if (!isTimedOut && MessageCodes.ACTION_PONG.equals(intent.getAction())) {
                Log.d(TAG, "PongReceiver.onReceive(): Timeout was not reached. Service seems to be active.");
                isRunning = true;
                callback.isRunning();
                this.context.unregisterReceiver(this);
            }
        } finally {
            lock.unlock();
        }
    }
}
