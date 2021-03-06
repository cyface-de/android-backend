package de.cyface.datacapturing;

import androidx.annotation.NonNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * An object of this class waits to receive a signal to its look. Please use a new
 * instance for each invocation.
 * <p>
 *
 *
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
class Synchronizer {

    /**
     * The <code>Lock</code> used to synchronize with the calling thread.
     */
    private final Lock lock;
    /**
     * The <code>Condition</code> used to synchronize with the calling thread.
     */
    private final Condition condition;

    /**
     * Creates a new completely initialized <code>Synchronizer</code>, capable of synchronizing using the
     * provided <code>Lock</code> and <code>Condition</code>. If any synchronization event occurs, this synchronizer
     * calls {@link Condition#signal()}.
     *
     * @param lock The <code>Lock</code> used to synchronize with the calling thread.
     * @param condition The <code>Condition</code> used to synchronize with the calling thread.
     */
    public Synchronizer(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    /**
     * Signals the calling thread by calling <code>signal</code> on the provided <code>Condition</code>.
     */
    protected void signal() {
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
