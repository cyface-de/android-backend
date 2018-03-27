package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.support.annotation.NonNull;

/**
 * A listener for the is running status from the system. This listener signals the test as soon as it receives an update
 * about the status of the background service. That way the test can wait for the service to actually return a status,
 * which can be asserted afterwards.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
class IsRunningStatus implements IsRunningCallback {

    /**
     * The flag set if this callback received a message from the service.
     */
    private boolean wasRunning;
    /**
     * The flag set if this callback did not receive an upate from the service or timed out prior to receiving one.
     */
    private boolean didTimeOut;
    /**
     * The lock to lock this thread and signal the condition.
     */
    private Lock lock;
    /**
     * The condition to signal as soon as this callback receives a status update.
     */
    private Condition condition;

    /**
     * Creates a new completely initialized <code>IsRunningStatus</code> callback. The provided lock and condition are
     * used to signal the creating process as soon as the callback receives an update.
     * 
     * @param lock The lock to lock this thread and signal the condition.
     * @param condition The condition to signal as soon as this callback receives a status update.
     */
    IsRunningStatus(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.wasRunning = false;
        this.didTimeOut = false;
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        lock.lock();
        try {
            wasRunning = true;
            didTimeOut = false;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void timedOut() {
        lock.lock();
        try {
            didTimeOut = true;
            wasRunning = false;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return A value of <code>true</code> if the service reported that it is running; <code>false</code> if it times
     *         out.
     */
    public boolean wasRunning() {
        return wasRunning;
    }

    /**
     * @return A value of <code>true</code> if the timeout was reached before receiving a notification from the service.
     *         This usually means the service is not running at the moment. Return <code>false</code> if a message has
     *         been received.
     */
    public boolean didTimeOut() {
        return didTimeOut;
    }
}
