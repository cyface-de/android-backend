package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Callback used for reconnecting the foreground service controlled by an Android application, with a probably running
 * background process.
 * This class is used by a {@link PongReceiver} to inform a reconnection process about the status and wake it up.
 * 
 * @author Klemens Muthmann
 * @version 1.0.3
 * @since 2.0.1
 * @see DataCapturingService#reconnect()
 */
abstract class ReconnectCallback implements IsRunningCallback {

    /**
     * Flag indicating whether the background service was running after the callback has returned. This is
     * <code>true</code> if the service was running and <code>false</code> otherwise.
     */
    private boolean wasRunning = false;
    /**
     * Flag indicating whether the request to the background service on whether it was running or not timed out. This is
     * <code>true</code> if the request timed out and <code>false</code> otherwise.
     */
    private boolean hasTimedOut = false;
    /**
     * A <code>lock</code> provided to this callback to protect the wake up process of the calling thread.
     */
    private final Lock lock;
    /**
     * A <code>condition</code> used to wake up the calling process.
     */
    private final Condition condition;

    /**
     * Creates a new completely initialized callback.
     *
     * @param lock A <code>lock</code> provided to this callback to protect the wake up process of the calling thread.
     * @param condition A <code>condition</code> used to wake up the calling process.
     */
    ReconnectCallback(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        Log.v(TAG, "ReconnectCallback.isRunning(): Entering!");
        lock.lock();
        try {
            if (!hasTimedOut()) {
                wasRunning = true;
                hasTimedOut = false;
                onSuccess();
                condition.signal();
            }
        } finally {
            lock.unlock();
        }
        Log.v(TAG, "ReconnectCallback.isRunning(): Leaving!");
    }

    /**
     * Hook method called if the background service has reported back, that it is running.
     */
    public abstract void onSuccess();

    @Override
    public void timedOut() {
        Log.v(TAG, "ReconnectCallback.timedOut(): Entering!");
        lock.lock();
        try {
            if (!wasRunning()) {
                Log.w(TAG, "Unable to bind on reconnect. It seems the service is not running");
                hasTimedOut = true;
                wasRunning = false;
                condition.signal();
            }
        } finally {
            lock.unlock();
        }
        Log.v(TAG, "ReconnectCallback.timedOut(): Leaving!");
    }

    /**
     * @return Flag indicating whether the request to the background service on whether it was running or not timed out.
     *         This is <code>true</code> if the request timed out and <code>false</code> otherwise.
     */
    public boolean hasTimedOut() {
        return hasTimedOut;
    }

    /**
     * @return Flag indicating whether the background service was running after the callback has returned. This is
     *         <code>true</code> if the service was running and <code>false</code> otherwise.
     */
    public boolean wasRunning() {
        return wasRunning;
    }
}
