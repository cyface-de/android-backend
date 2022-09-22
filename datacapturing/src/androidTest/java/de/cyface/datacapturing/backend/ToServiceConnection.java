/*
 * Copyright 2017-2022 Cyface GmbH
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
package de.cyface.datacapturing.backend;

import static de.cyface.datacapturing.MessageCodes.GLOBAL_BROADCAST_PING;
import static de.cyface.datacapturing.MessageCodes.GLOBAL_BROADCAST_PONG;
import static de.cyface.datacapturing.TestUtils.TAG;

import java.util.concurrent.TimeUnit;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.PongReceiver;

/**
 * Connection from the test to the capturing service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
class ToServiceConnection implements ServiceConnection {

    /**
     * The context this <code>ServiceConnection</code> runs with.
     */
    Context context;
    /**
     * Callback used to check the success or non success of the service startup.
     */
    TestCallback callback;
    /**
     * The <code>Messenger</code> handling messages coming from the <code>DataCapturingBackgroundService</code>.
     */
    private final Messenger fromServiceMessenger;

    /**
     * Creates a new completely initialized <code>ToServiceConnection</code>.
     *
     * @param fromServiceMessenger The <code>Messenger</code> handling messages coming from the
     *            <code>DataCapturingBackgroundService</code>.
     */
    ToServiceConnection(@NonNull final Messenger fromServiceMessenger) {
        this.fromServiceMessenger = fromServiceMessenger;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(TAG, "onServiceConnected");
        /*
         * The messenger used to send messages to the data capturing service.
         */
        Messenger toServiceMessenger = new Messenger(iBinder);

        try {
            Message msg = Message.obtain(null, MessageCodes.REGISTER_CLIENT);
            msg.replyTo = fromServiceMessenger;
            toServiceMessenger.send(msg);
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }

        final var isRunningChecker = new PongReceiver(context, GLOBAL_BROADCAST_PING, GLOBAL_BROADCAST_PONG);
        isRunningChecker.checkIsRunningAsync(1, TimeUnit.MINUTES, callback);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(TAG, "onServiceDisconnected");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.d(TAG, "bindingDied");
    }
}
