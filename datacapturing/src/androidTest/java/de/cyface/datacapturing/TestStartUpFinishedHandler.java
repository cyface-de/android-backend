package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;

/**
 * A handler that can be used to receive the event sent on shutdown started and get the measurement identifier
 * transmitted with that event. You may assert on that identifier.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.2.0
 */
final class TestStartUpFinishedHandler extends StartUpFinishedHandler {
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
     * @param deviceId The device id used to generate unique global broadcast ids.
     */
    TestStartUpFinishedHandler(final @NonNull Lock lock, final @NonNull Condition condition,
            @NonNull final String deviceId) {
        super(deviceId);
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void startUpFinished(final long measurementIdentifier) {
        lock.lock();
        try {
            receivedMeasurementIdentifier = measurementIdentifier;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
