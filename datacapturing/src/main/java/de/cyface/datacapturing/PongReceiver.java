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

/**
 * Created by muthmann on 18.03.18.
 */

public class PongReceiver extends BroadcastReceiver {

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
                lock.lock();
                try {
                    if (!isRunning) {
                        callback.timedOut();
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
        lock.lock();
        try {
            if (!isTimedOut) {
                isRunning = true;
                callback.isRunning();
                this.context.unregisterReceiver(this);
            }
        } finally {
            lock.unlock();
        }
    }
}
