/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.synchronization

import android.app.Service
import android.content.Intent
import android.os.IBinder
import de.cyface.uploader.DefaultUploader
import de.cyface.utils.Validate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The synchronisation `Service` used to bind the synchronisation adapter to the Android framework.
 *
 * Further details are described in the [Android
 * documentation](https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService).
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.9
 * @since 2.0.0
 */
class SyncService : Service() {
    override fun onCreate() {
        synchronized(LOCK) {
            if (syncAdapter == null) {
                val collectorApi = collectorApi()
                syncAdapter = SyncAdapter(
                    applicationContext,
                    true,
                    OAuth2(applicationContext, CyfaceAuthenticator.settings),
                    DefaultUploader(collectorApi),
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
     * @return The URL as string
     */
    private fun collectorApi(): String {
        val apiEndpoint =
            runBlocking { CyfaceAuthenticator.settings.collectorUrlFlow.first() }
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