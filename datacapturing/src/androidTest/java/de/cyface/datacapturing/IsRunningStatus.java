package de.cyface.datacapturing;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import android.support.annotation.NonNull;

/**
 * Created by muthmann on 26.03.18.
 */
class IsRunningStatus implements IsRunningCallback {

    private boolean wasRunning;
    private boolean didTimeOut;
    private Lock lock;
    private Condition condition;

    IsRunningStatus(final @NonNull Lock lock, final @NonNull Condition condition) {
        this.wasRunning = false;
        this.didTimeOut = false;
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void isRunning() {
        lock.lock();
        try {
            wasRunning = true;
            didTimeOut = false;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void timedOut() {
        lock.lock();
        try {
            didTimeOut = true;
            wasRunning = false;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public boolean wasRunning() {
        return wasRunning;
    }

    public boolean didTimeOut() {
        return didTimeOut;
    }
}
