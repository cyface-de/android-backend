package de.cyface.synchronization;

import static de.cyface.synchronization.SharedConstants.TAG;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * The synchronisation <code>Service</code> used to bind the synchronisation adapter to the Android framework.
 * <p>
 * Further details are described in the <a href=
 * "https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService">Android
 * documentation</a>.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.4
 * @since 2.0.0
 */
public final class SyncService extends Service {

    /**
     * The settings key used to identify the settings storing the URL of the server to upload data to.
     */
    public static final String SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint";
    /**
     * The synchronisation adapter this service is supposed to call.
     */
    // TODO Ugh. Singleton is so ugly. Nevertheless this is how it is specified in the documentation. Maybe try to
    // change this after it runs.
    private static SyncAdapter syncAdapter = null;
    /**
     * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
     */
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        Log.d(TAG, "sync service on create");
        synchronized (LOCK) {
            if (syncAdapter == null) {
                syncAdapter = new SyncAdapter(getApplicationContext(), true, new HttpConnection());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "sync service on bind");
        return syncAdapter.getSyncAdapterBinder();
    }
}
