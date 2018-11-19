package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;

/**
 * Synchronizes the calling thread with the startup of the
 * {@link de.cyface.datacapturing.backend.DataCapturingBackgroundService}. This class is used by the synchronous calls
 * of the <code>DataCapturingService</code>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class StartSynchronizer extends StartUpFinishedHandler {

    /**
     * Synchronizer handling the signalling to the provided <code>condition</code>.
     */
    private final Synchronizer synchronizer;

    /**
     * Creates a new completely initialized <code>StartSynchronizer</code> using the provided <code>Lock</code> and
     * <code>Condition</code> to synchronize with the calling thread.
     *
     * @param lock The lock used for synchronization. Usually a <code>ReentrantLock</code>.
     * @param condition The condition waiting for a signal from this <code>StartSynchronizer</code>.
     */
    public StartSynchronizer(final @NonNull Lock lock, final @NonNull Condition condition) {
        synchronizer = new Synchronizer(lock, condition);
    }

    @Override
    public void startUpFinished(final @NonNull long measurementIdentifier) {
        synchronizer.signal();
    }
}
