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
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.TestUtils.TAG;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.datacapturing.IsRunningCallback;

/**
 * A callback used to check whether the service has successfully started or not.
 * <p>
 * This callback is used by tests and is provided with a <code>lock</code> and <code>condition</code> to wake up the
 * test as soon as the callback receives some response. It also provides methods to check the current state of the
 * service under test after having been executed.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
public class TestCallback implements IsRunningCallback {

    /**
     * Flag indicating a successful startup if <code>true</code>.
     */
    private boolean isRunning = false;
    /**
     * Flag indicating an unsuccessful startup if <code>true</code>.
     */
    private boolean timedOut = false;
    /**
     * <code>Lock</code> used to synchronize the callback with the test case using it.
     */
    private final Lock lock;
    /**
     * <code>Condition</code> used to signal the test case to continue processing.
     */
    private final Condition condition;
    /**
     * A tag used to mark messages from different instances of this class.
     */
    private final String logTag;

    /**
     * Creates a new completely initialized <code>TestCallback</code>
     *
     * @param logTag A tag used to mark messages from different instances of this class.
     * @param lock <code>Lock</code> used to synchronize the callback with the test case using it.
     * @param condition <code>Condition</code> used to signal the test case to continue processing.
     */
    @SuppressWarnings("WeakerAccess") // DataCapturingServiceTest in the Cyface flavour requires this
    public TestCallback(final @NonNull String logTag, final @NonNull Lock lock, final @NonNull Condition condition) {
        this.logTag = logTag;
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        Log.v(TAG, logTag + ": Locking TestCallback isRunning!");
        lock.lock();
        Log.v(TAG, logTag + "Locked TestCallback isRunning!");
        try {
            isRunning = true;
            timedOut = false;
            Log.v(TAG, logTag + ": Set isRunning to true and signalling calling thread!");
            condition.signal();
        } finally {
            Log.v(TAG, logTag + ": Unlocking TestCallback isRunning!");
            lock.unlock();
        }
    }

    @Override
    public void timedOut() {
        Log.v(TAG, logTag + ": Locking TestCallback timedOut!");
        lock.lock();
        Log.v(TAG, logTag + ": Locked TestCallback timedOut!");
        try {
            timedOut = true;
            isRunning = false;
            Log.v(TAG, logTag + ": Set timedOut to true and signalling calling thread!");
            condition.signal();
        } finally {
            Log.v(TAG, logTag + ": Unlocking TestCallback timedOut!");
            lock.unlock();
        }
    }

    /**
     * @return A value of <code>true</code> if the service reported that it is running; <code>false</code> if it times
     *         out.
     */
    @SuppressWarnings("WeakerAccess") // DataCapturingServiceTest in the Cyface flavour requires this
    public boolean wasRunning() {
        return isRunning;
    }

    /**
     * @return A value of <code>true</code> if the timeout was reached before receiving a notification from the service.
     *         This usually means the service is not running at the moment. Return <code>false</code> if a message has
     *         been received.
     */
    @SuppressWarnings("WeakerAccess") // DataCapturingServiceTest in the Cyface flavour requires this
    public boolean didTimeOut() {
        return timedOut;
    }
}
