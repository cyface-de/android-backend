package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;

/**
 * A handler that can be used to receive the event sent on shutdown finished and get the measurement identifier
 * transmitted with that event. You may assert on that identifier.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.2.0
 */
final class TestShutdownFinishedHandler extends ShutDownFinishedHandler {
    /**
     * The last measurement identifier received by this handler. If you reuse this handler for multiple times this value
     * is overwritten by the last call. If the handler was never called its value is -1.
     */
    long receivedMeasurementIdentifier = -1L;
    /**
     * The condition used to synchronize this handler with the calling test.
     */
    private final Condition condition;
    /**
     * The lock used to synchronize this handler with the calling test.
     */
    private final Lock lock;

    /**
     * Creates a new completely initialized object of this class
     *
     * @param lock The lock used to synchronize this handler with the calling test.
     * @param condition The condition used to synchronize this handler with the calling test.
     */
    TestShutdownFinishedHandler(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.condition = condition;
        this.lock = lock;
    }

    @Override
    public void shutDownFinished(long measurementIdentifier) {
        lock.lock();
        try {
            this.receivedMeasurementIdentifier = measurementIdentifier;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public Condition getCondition() {
        return condition;
    }

    public Lock getLock() {
        return lock;
    }
}
