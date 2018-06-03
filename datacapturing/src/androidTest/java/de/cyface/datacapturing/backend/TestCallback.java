package de.cyface.datacapturing.backend;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import de.cyface.datacapturing.IsRunningCallback;

/**
 * A callback used to check whether the service has successfully started or not.
 *
 * @author Klemens Muthmann
 * @since 2.0.0
 * @version 1.0.0
 */
class TestCallback implements IsRunningCallback {

    /**
     * Flag indicating a successful startup if <code>true</code>.
     */
    boolean isRunning = false;
    /**
     * Flag indicating an unsuccessful startup if <code>true</code>.
     */
    boolean timedOut = false;
    /**
     * <code>Lock</code> used to synchronize the callback with the test case using it.
     */
    Lock lock;
    /**
     * <code>Condition</code> used to signal the test case to continue processing.
     */
    Condition condition;

    @Override
    public void isRunning() {
        lock.lock();
        try {
            isRunning = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void timedOut() {
        lock.lock();
        try {
            timedOut = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
