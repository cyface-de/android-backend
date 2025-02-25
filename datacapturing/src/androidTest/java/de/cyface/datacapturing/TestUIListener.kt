package de.cyface.datacapturing

import android.location.Location
import de.cyface.datacapturing.ui.Reason
import de.cyface.datacapturing.ui.UIListener
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

/**
 * A test testUIListener receiving values to test against.
 *
 * @author Klemens Muthmann
 * @version 1.2.0
 * @since 2.0.0
 */
internal class TestUIListener : UIListener {
    /**
     * Synchronization lock with the main test.
     */
    private val lock: Lock?

    /**
     * Synchronization condition with the main test.
     */
    private val condition: Condition?

    /**
     * A list of the received locations during one test run.
     */
    val receivedUpdates: MutableList<Location>
    var requiredPermission: Boolean = false

    /**
     * Creates a new completely initialized `TestUIListener`.
     *
     * @param lock Synchronization lock with the main test.
     * @param condition Synchronization condition with the main test.
     */
    constructor(lock: Lock, condition: Condition) {
        this.lock = lock
        this.condition = condition
        receivedUpdates = ArrayList()
    }

    /**
     * Creates a new completely initialized `TestUIListener`.
     */
    constructor() {
        lock = null
        condition = null
        receivedUpdates = ArrayList()
    }

    override fun onLocationUpdate(location: Location) {
        receivedUpdates.add(location)

        if (lock != null && condition != null) {
            lock.lock()
            try {
                condition.signal()
            } finally {
                lock.unlock()
            }
        }
    }

    override fun onRequirePermission(permission: String, reason: Reason): Boolean {
        requiredPermission = true
        return false
    }
}
