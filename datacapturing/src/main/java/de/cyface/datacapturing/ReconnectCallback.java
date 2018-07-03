package de.cyface.datacapturing;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

abstract class ReconnectCallback implements IsRunningCallback {

    private final static String TAG = "de.cyface.capturing";
    private boolean wasRunning = false;
    private boolean hasTimedOut = false;
    private final Lock lock;
    private final Condition condition;

    ReconnectCallback(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        lock.lock();
        try {
            if(!hasTimedOut) {
                wasRunning = true;
                hasTimedOut = false;
                condition.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public abstract void onSuccess();

    @Override
    public void timedOut() {
        lock.lock();
        try {
            if(!wasRunning) {
                Log.w(TAG, "Unable to bind on reconnect. It seems the service is not running");
                hasTimedOut = true;
                wasRunning = false;
                condition.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasTimedOut() {
        return hasTimedOut;
    }

    public boolean wasRunning() {
        return wasRunning;
    }
}
