/*
 * Copyright 2017 Cyface GmbH
 * This file is part of the Cyface SDK for Android.
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * @author Armin Schnabel
 * @version 1.1.1
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
     * @param appId used to make the global broadcast id in {@link StartUpFinishedHandler} unique.
     *            This must be a device-wide unique identifier for the application containing this SDK such as
     *            {@code Context#getPackageName()} which is required to generate unique global broadcasts for this app.
     *            <b>Attention:</b> The identifier must be identical in the global broadcast sender and receiver.
     */
    public StartSynchronizer(final @NonNull Lock lock, final @NonNull Condition condition,
            @NonNull final String appId) {
        super(appId);
        synchronizer = new Synchronizer(lock, condition);
    }

    @Override
    public void startUpFinished(final long measurementIdentifier) {
        synchronizer.signal();
    }
}
