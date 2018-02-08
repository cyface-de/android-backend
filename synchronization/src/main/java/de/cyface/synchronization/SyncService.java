package de.cyface.synchronization;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by muthmann on 06.02.18.
 */
public final class SyncService extends Service {
    private final static String TAG = "de.cyface.sync";
    // TODO Ugh. Singleton is so ugly. Nevertheless this is how it is specified in the documentation. Maybe try to
    // change this after it runs.
    private static CyfaceSyncAdapter syncAdapter = null;
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        Log.d(TAG,"sync service on create");
        synchronized (LOCK) {
            if (syncAdapter == null) {
                syncAdapter = new CyfaceSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"sync service on bind");
        return syncAdapter.getSyncAdapterBinder();
    }
}
