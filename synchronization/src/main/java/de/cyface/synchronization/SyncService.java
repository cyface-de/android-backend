package de.cyface.synchronization;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The synchronistaion <code>Service</code> used to bind the synchronisation adapter to the Android framework.
 * <p>
 * Further details are described in the <a href=
 * "https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService">Android
 * documentation</a>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class SyncService extends Service {
    /**
     * The tag used to display log messages.
     */
    private final static String TAG = "de.cyface.sync";
    /**
     * The synchronisation adapter this service is supposed to call.
     */
    // TODO Ugh. Singleton is so ugly. Nevertheless this is how it is specified in the documentation. Maybe try to
    // change this after it runs.
    private static CyfaceSyncAdapter syncAdapter = null;
    /**
     * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
     */
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        Log.d(TAG, "sync service on create");
        synchronized (LOCK) {
            if (syncAdapter == null) {
                syncAdapter = new CyfaceSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "sync service on bind");
        return syncAdapter.getSyncAdapterBinder();
    }
}
