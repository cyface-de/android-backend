package de.cyface.datacapturing;

import android.location.Location;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;

/**
 * A test testUIListener receiving values to test agains.
 *
 * @author Klemens Muthmann
 * @version 1.2.0
 * @since 2.0.0
 */
class TestUIListener implements UIListener {

    /**
     * Synchronization lock with the main test.
     */
    private final Lock lock;
    /**
     * Synchronization condition with the main test.
     */
    private final Condition condition;
    /**
     * A list of the received locations during one test run.
     */
    final List<Location> receivedUpdates;
    boolean requiredPermission;

    /**
     * Creates a new completely initialized <code>TestUIListener</code>.
     *
     * @param lock Synchronization lock with the main test.
     * @param condition Synchronization condition with the main test.
     */
    TestUIListener(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
        receivedUpdates = new ArrayList<>();
    }

    /**
     * Creates a new completely initialized <code>TestUIListener</code>.
     */
    TestUIListener() {
        lock = null;
        condition = null;
        receivedUpdates = new ArrayList<>();
    }

    @Override
    public void onLocationUpdate(Location location) {
        receivedUpdates.add(location);

        if (lock != null && condition != null) {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean onRequirePermission(String permission, Reason reason) {
        requiredPermission = true;
        return false;
    }
}
