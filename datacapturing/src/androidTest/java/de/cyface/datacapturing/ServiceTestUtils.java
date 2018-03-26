package de.cyface.datacapturing;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Created by muthmann on 26.03.18.
 */

class ServiceTestUtils {

    private ServiceTestUtils() {
        // Nothing to do here.
    }
    /**
     * Locks the test and waits until the timeout is reached or a signal to continue execution is received. Never do call this on the main thread or you will receive an Application Not Responding (ANR) error.
     * @param time The time to wait until the test lock is released and the test continues even if no signal was issued.
     * @param unit The unit of <code>time</code>. For example seconds or milliseconds.
     */
    static void lockAndWait(final long time, final @NonNull TimeUnit unit, final @NonNull Lock lock, final @NonNull Condition condition) {
        lock.lock();
        try {
            condition.await(time, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks for the current isRunning status of the object of class under test and runs that on the main thread.
     *
     * @param oocut
     * @param runningStatusCallback
     */
    static void callCheckForRunning(final @NonNull DataCapturingService oocut, final @NonNull IsRunningCallback runningStatusCallback) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                oocut.isRunning(1, TimeUnit.SECONDS, runningStatusCallback);
            }
        });
    }
}
