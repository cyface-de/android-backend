package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;

/**
 * Synchronizes the calling thread with the shut down of the
 * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}. This class is used by the synchronous calls
 * of the <code>DataCapturingService</code>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
public class StopSynchronizer extends ShutDownFinishedHandler {

    /**
     * Synchronizer handling the signalling to the provided <code>condition</code>.
     */
    private Synchronizer synchronizer;

    /**
     * Creates a new completely initialized <code>StopSynchronizer</code> using the provided <code>Lock</code> and
     * <code>Condition</code> to synchronize with the calling thread.
     *
     * @param lock The lock used for synchronization. Usually a <code>ReentrantLock</code>.
     * @param condition The condition waiting for a signal from this <code>StopSynchronizer</code>.
     * @param serviceStoppedActionId An app and device-wide unique identifier. Each service needs to use a different id
     *            so that only the service in question receives the expected ping-back.
     */
    public StopSynchronizer(@NonNull final Lock lock, @NonNull final Condition condition,
            @NonNull final String serviceStoppedActionId) {
        super(serviceStoppedActionId);
        synchronizer = new Synchronizer(lock, condition);
    }

    @Override
    public void shutDownFinished(final long measurementIdentifier) {
        synchronizer.signal();
    }
}
