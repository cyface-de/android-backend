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

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.backend.PingReceiver;
import de.cyface.synchronization.BundlesExtrasCodes;

/**
 * A {@code BroadcastReceiver} and sender that send a {@code MessageCodes#GLOBAL_BROADCAST_PING} event to the system and
 * expects to receive a {@code MessageCodes#GLOBAL_BROADCAST_PONG} event if the background service is running.
 * If not a timeout will tell the caller, that the service is not running.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 */
public class PongReceiver extends BroadcastReceiver {

    /**
     * Logging TAG to identify logs associated with the {@link PingReceiver} or {@link PongReceiver}.
     */
    public static final String TAG = Constants.TAG + ".png";
    /**
     * The human readable name for the background thread handling response and timeout of the ping pong process between
     * background service and foreground facade.
     */
    private static final String BACKGROUND_THREAD_NAME = "de.cyface.thread.pongreceiver";
    /**
     * The callback called if either the <code>MessageCodes.PONG</code> event has been received or the timeout was
     * reached.
     */
    private IsRunningCallback callback;
    /**
     * Flag that is set if the <code>MessageCodes.PONG</code> event was received. This flag is required to synchronize
     * with
     * the timeout if both happen simultaneously.
     */
    private boolean isRunning;
    /**
     * Flag that is set if the <code>MessageCodes.PONG</code> event was not received. This flag is required to
     * synchronize with the isRunning handler if both happen simultaneously.
     */
    private boolean isTimedOut;
    /**
     * Lock used to synchronize the timeout and the isRunning callback if both happen to happen simultaneously.
     */
    private final Lock lock;
    /**
     * Current context used to send and receive broadcasts.
     */
    private final WeakReference<Context> context;
    /**
     * Background thread used to handle timeout or broadcast response without blocking the calling thread.
     */
    private final HandlerThread pongReceiverThread;
    /**
     * An app and device-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private final String pingActionId;
    /**
     * An app and device-wide unique identifier. Each service of this app needs to use a different id so that only the
     * service in question "replies" to the ping request
     */
    private final String pongActionId;

    /**
     * Creates a new completely <code>PongReceiver</code> for a certain context.
     *
     * @param context The context to use to send and receive broadcast messages.
     * @param pingActionId An app and device-wide unique identifier. Each service of this app needs to use
     *            a different id so that only the service in question "replies" to the ping request.
     * @param pongActionId An app and device-wide unique identifier. Each service of this app needs to use
     *            a different id so that only the service in question "replies" to the ping request.
     */
    public PongReceiver(@NonNull final Context context,
            @NonNull final String pingActionId, @NonNull final String pongActionId) {
        pongReceiverThread = new HandlerThread(BACKGROUND_THREAD_NAME, Process.THREAD_PRIORITY_DEFAULT);
        lock = new ReentrantLock();
        this.context = new WeakReference<>(context);
        isRunning = false;
        isTimedOut = false;
        this.pingActionId = pingActionId;
        this.pongActionId = pongActionId;
    }

    // TODO [CY-3950]: This should be called ping and receive, but maybe more meaningful names like: areYouRunning and
    // iAmRunning would be more readable. (But violates standard Java coding style.)
    /**
     * Sends the <code>MessageCodes.PING</code> message to the system and waits for the timeout to occur or the service
     * to answer with a <code>MessageCodes.PONG</code>.
     *
     * @param timeout The time to wait for the <code>MessageCodes.PONG</code> in the specified unit.
     * @param unit The unit of the <code>timeout</code>.
     * @param callback The callback to inform about either the timeout or the successful reception of the
     *            <code>MessageCodes.PONG</code> message.
     */
    public void checkIsRunningAsync(final long timeout, final @NonNull TimeUnit unit,
            final @NonNull IsRunningCallback callback) {
        this.callback = callback;

        // Run receiver on a different thread so it runs even if calling thread waits for it to return:
        pongReceiverThread.start();
        Handler receiverHandler = new Handler(pongReceiverThread.getLooper());
        context.get().registerReceiver(this, new IntentFilter(pongActionId), null,
                receiverHandler);

        long currentUptimeInMillis = SystemClock.uptimeMillis();
        long offset = unit.toMillis(timeout);

        final String pingPongIdentifier = UUID.randomUUID().toString();
        Log.v(TAG, "PongReceiver.checkIsRunningAsync(): Variable currentUptimeInMillis is " + currentUptimeInMillis);
        Log.v(TAG, "PongReceiver.checkIsRunningAsync(): Variable offset is " + offset);
        Log.v(TAG, "PongReceiver.checkIsRunningAsync(): Sending ping with identifier " + pingPongIdentifier);

        Handler timeoutHandler = new Handler(pongReceiverThread.getLooper());
        timeoutHandler.postAtTime(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG,
                        "PongReceiver.checkIsRunningAsync(): Timeout for pong " + pingPongIdentifier + " reached after "
                                + unit.toMillis(timeout) + " milliseconds. Executed at: " + SystemClock.uptimeMillis());
                lock.lock();
                try {
                    if (!isRunning) {
                        Log.d(TAG, "PongReceiver.checkIsRunningAsync(): Service seems not to be running. Timing out!");
                        PongReceiver.this.callback.timedOut();
                        isTimedOut = true;

                        final Context realContext = context.get();
                        if (realContext != null) {
                            realContext.unregisterReceiver(PongReceiver.this);
                        }
                        quitBackgroundHandlerThread();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }, currentUptimeInMillis + offset);

        final Intent broadcastIntent = new Intent(pingActionId);
        if (BuildConfig.DEBUG) {
            broadcastIntent.putExtra(BundlesExtrasCodes.PING_PONG_ID, pingPongIdentifier);
        }
        context.get().sendBroadcast(broadcastIntent);
        Log.d(TAG, "PongReceiver.checkIsRunningAsync(): Ping was sent!");
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Log.v(TAG, "PongReceiver.onReceive(): Received pong with identifier "
                + intent.getStringExtra(BundlesExtrasCodes.PING_PONG_ID));
        lock.lock();
        try {
            if (!isTimedOut && pongActionId.equals(intent.getAction())) {
                Log.d(TAG, "PongReceiver.onReceive(): Timeout was not reached. Service seems to be active.");
                isRunning = true;
                callback.isRunning();
                this.context.get().unregisterReceiver(this);
                quitBackgroundHandlerThread();
            }
        } finally {
            lock.unlock();
        }
    }

    private void quitBackgroundHandlerThread() {
        if (Thread.currentThread().getName().equals(pongReceiverThread.getName())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                pongReceiverThread.quitSafely();
            } else {
                pongReceiverThread.quit();
            }
        }
    }
}
