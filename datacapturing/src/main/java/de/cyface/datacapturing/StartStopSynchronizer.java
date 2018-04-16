package de.cyface.datacapturing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * An object of this class waits to receive a message from the service for a start or a stop event. Please use a new
 * instance for each invocation.
 * <p>
 * To work properly you must register this object as an Android <code>BroadcastReceiver</code> and as
 * <code>DataCapturingListener</code> with the <code>DataCapturingService</code>.
 *
 * @author Klemens Muthmann
 * @version 1.2.0
 * @since 2.0.0
 */
class StartStopSynchronizer extends BroadcastReceiver {
    /**
     * The tag used to identify Logcat messages from this class.
     */
    private static final String TAG = "de.cyface.capturing";
    /**
     * This is set to <code>true</code> if a <code>MessageCodes.BROADCAST_SERVICE_STARTED</code> broadcast has been
     * received and is <code>false</code> otherwise.
     */
    private boolean receivedServiceStarted;
    /**
     * This is set to <code>true</code> if either a <code>MessageCodes.BROADCAST_SERVICE_STOPPED</code> broadcast has
     * been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code> otherwise.
     */
    private boolean receivedServiceStopped;
    /**
     * The <code>Lock</code> used to synchronize with the calling thread.
     */
    private final Lock lock;
    /**
     * The <code>Condition</code> used to synchronize with the calling thread.
     */
    private final Condition condition;

    /**
     * Creates a new completely initialized <code>StartStopSynchronizer</code>, capable of synchronizing using the
     * provided <code>Lock</code> and <code>Condition</code>. If any synchronization event occurs, this synchronizer
     * calls {@link Condition#signal()}.
     *
     * @param lock The <code>Lock</code> used to synchronize with the calling thread.
     * @param condition The <code>Condition</code> used to synchronize with the calling thread.
     */
    public StartStopSynchronizer(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Log.v(TAG, "Start/Stop Synchronizer received an intent with action " + intent.getAction() + ".");
        if (intent.getAction() == null) {
            throw new IllegalStateException("Received broadcast with null action.");
        }
        switch (intent.getAction()) {
            case MessageCodes.BROADCAST_SERVICE_STARTED:
                Log.v(TAG, "Received Service started broadcast!");
                receivedServiceStarted = true;
                break;
            case MessageCodes.BROADCAST_SERVICE_STOPPED:
                Log.v(TAG, "Received Service stopped broadcast!");
                receivedServiceStopped = true;
                break;
            default:
                throw new IllegalStateException("Received undefined broadcast " + intent.getAction());
        }

        signal();
    }

    /**
     * @return This is set to <code>true</code> if a <code>MessageCodes.BROADCAST_SERVICE_STARTED</code> broadcast has been
     * received and is <code>false</code> otherwise.
     */
    public boolean receivedServiceStarted() {
        return receivedServiceStarted;
    }

    /**
     * @return This is set to <code>true</code> if either a <code>MessageCodes.BROADCAST_SERVICE_STOPPED</code> broadcast has
     * been received or a <code>MessageCodes.SERVICE_STOPPED</code> was issued. It is <code>false</code> otherwise.
     */
    public boolean receivedServiceStopped() {
        return receivedServiceStopped;
    }

    /**
     * Signals the calling thread by calling <code>signal</code> on the provided <code>Condition</code>.
     */
    private void signal() {
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
