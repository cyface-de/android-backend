package de.cyface.datacapturing;

import android.support.annotation.NonNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

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
