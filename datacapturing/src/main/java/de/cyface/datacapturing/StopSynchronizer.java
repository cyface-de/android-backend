package de.cyface.datacapturing;

import android.support.annotation.NonNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Synchronizes the calling thread with the shut down of the
 * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}. This class is used by the synchronous calls
 * of the <code>DataCapturingService</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
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
     */
    public StopSynchronizer(final @NonNull Lock lock, final Condition condition) {
        synchronizer = new Synchronizer(lock, condition);
    }

    @Override
    void shutDownFinished() {
        synchronizer.signal();
    }
}
