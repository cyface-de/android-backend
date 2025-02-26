/*
 * Copyright 2017-2025 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
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
        receivedUpdates = mutableListOf()
    }

    /**
     * Creates a new completely initialized `TestUIListener`.
     */
    constructor() {
        lock = null
        condition = null
        receivedUpdates = mutableListOf()
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
