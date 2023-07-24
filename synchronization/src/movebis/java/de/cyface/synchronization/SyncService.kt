package de.cyface.synchronization

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import de.cyface.uploader.DefaultUploader
import de.cyface.utils.Validate

/**
 * The synchronisation `Service` used to bind the synchronisation adapter to the Android framework.
 *
 * Further details are described in the [Android
 * documentation](https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService).
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.8
 * @since 2.0.0
 */
class SyncService : Service() {
    override fun onCreate() {
        synchronized(LOCK) {
            if (syncAdapter == null) {
                val collectorApi = collectorApi(applicationContext)
                syncAdapter = SyncAdapter(
                    applicationContext, true, MovebisAuth(),
                    DefaultUploader(collectorApi!!),
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return syncAdapter!!.syncAdapterBinder
    }

    /**
     * Reads the Collector API URL from the preferences.
     *
     * @param context The `Context` required to read the preferences
     * @return The URL as string
     */
    private fun collectorApi(context: Context): String? {
        val preferences = CustomPreferences(context)
        val apiEndpoint = preferences.getCollectorUrl()
        Validate.notNull(
            apiEndpoint,
            "Sync canceled: Server url not available. Please set the applications server url preference."
        )
        return apiEndpoint
    }

    companion object {
        /**
         * The synchronisation adapter this service is supposed to call.
         *
         *
         * Singleton isn't what they call a beauty. Nevertheless this is how it is specified in the documentation. Maybe try
         * to change this after it runs.
         */
        private var syncAdapter: SyncAdapter? = null

        /**
         * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
         */
        private val LOCK = Any()
    }
}