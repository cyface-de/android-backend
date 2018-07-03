package de.cyface.datacapturing.backend;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import de.cyface.datacapturing.IsRunningCallback;

/**
 * A callback used to check whether the service has successfully started or not.
 *
 * @author Klemens Muthmann
 * @since 2.0.0
 * @version 2.0.0
 */
public class TestCallback implements IsRunningCallback {

    private final static String TAG = "de.cyface.test";

    /**
     * Flag indicating a successful startup if <code>true</code>.
     */
    public boolean isRunning = false;
    /**
     * Flag indicating an unsuccessful startup if <code>true</code>.
     */
    public boolean timedOut = false;
    /**
     * <code>Lock</code> used to synchronize the callback with the test case using it.
     */
    private final Lock lock;
    /**
     * <code>Condition</code> used to signal the test case to continue processing.
     */
    private final Condition condition;

    private final String logTag;

    public TestCallback(final @NonNull String logTag, final @NonNull Lock lock, final @NonNull Condition condition) {
        this.logTag = logTag;
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        Log.v(TAG, logTag + ": Locking TestCallback isRunning!");
        lock.lock();
        Log.v(TAG, logTag + "Locked TestCallback isRunning!");
        try {
            isRunning = true;
            Log.v(TAG, logTag + ": Set isRunning to true and signalling calling thread!");
            condition.signal();
        } finally {
            Log.v(TAG, logTag + ": Unlocking TestCallback isRunning!");
            lock.unlock();
        }
    }

    @Override
    public void timedOut() {
        Log.v(TAG, logTag + ": Locking TestCallback timedOut!");
        lock.lock();
        Log.v(TAG, logTag + ": Locked TestCallback timedOut!");
        try {
            timedOut = true;
            Log.v(TAG, logTag + ": Set timedOut to true and signalling calling thread!");
            condition.signal();
        } finally {
            Log.v(TAG, logTag + ": Unlocking TestCallback timedOut!");
            lock.unlock();
        }
    }
}
