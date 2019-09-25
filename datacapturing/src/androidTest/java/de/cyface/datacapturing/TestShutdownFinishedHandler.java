/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;

/**
 * A handler that can be used to receive the event sent on shutdown finished and get the measurement identifier
 * transmitted with that event. You may assert on that identifier.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
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
     * @param serviceStoppedActionId An app and device-wide unique identifier. Each service needs to use a different id
     *            so that only the service in question receives the expected ping-back.
     */
    TestShutdownFinishedHandler(@NonNull final Lock lock, @NonNull final Condition condition,
            @NonNull final String serviceStoppedActionId) {
        super(serviceStoppedActionId);
        this.condition = condition;
        this.lock = lock;
    }

    @Override
    public void shutDownFinished(final long measurementIdentifier) {
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
