package de.cyface.datacapturing;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Created by muthmann on 18.03.18.
 */

public class PongReceiver extends BroadcastReceiver {

    private static final String TAG = "de.cyface.capturing";
    private IsRunningCallback callback;
    private boolean isRunning;
    private boolean isTimedOut;
    private final Handler timeoutHandler;
    private final Lock lock;
    private final Context context;

    public PongReceiver(final @NonNull Context context) {
        timeoutHandler = new Handler();
        lock = new ReentrantLock();
        this.context = context;
        isRunning = false;
        isTimedOut = false;
    }

    public void pongAndReceive(final long timeout, final @NonNull TimeUnit unit,
            final @NonNull IsRunningCallback callback) {
        this.callback = callback;
        context.registerReceiver(this, new IntentFilter(MessageCodes.ACTION_PONG));
        context.sendBroadcast(new Intent(MessageCodes.ACTION_PING));
        timeoutHandler.postAtTime(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"PongReceiver.pongAndReceive(): timeout reached after "+unit.toMillis(timeout)+" milliseconds.");
                lock.lock();
                try {
                    if (!isRunning) {
                        Log.d(TAG,"PongReceiver.pongAndReceive(): Service seems not to be running. Timing out!");
                        PongReceiver.this.callback.timedOut();
                        isTimedOut = true;
                        context.unregisterReceiver(PongReceiver.this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }, unit.toMillis(timeout));
    }

    @Override
    public void onReceive(final @NonNull Context context, final @NonNull Intent intent) {
        Log.d(TAG,"PongReceiver.onReceive(): Received Pong Event.");
        lock.lock();
        try {
            if (!isTimedOut && MessageCodes.ACTION_PONG.equals(intent.getAction())) {
                Log.d(TAG,"PongReceiver.onReceive(): Timeout was not reached. Service seems to be active.");
                isRunning = true;
                callback.isRunning();
                this.context.unregisterReceiver(this);
            }
        } finally {
            lock.unlock();
        }
    }
}
