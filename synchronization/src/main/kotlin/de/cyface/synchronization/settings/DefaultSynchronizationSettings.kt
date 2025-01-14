/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.synchronization.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import de.cyface.persistence.SetupException
import de.cyface.synchronization.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File

data class SyncConfig(val collectorUrl: String, val oAuthConfig: JSONObject)

/**
 * Settings used by this library.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 7.8.1
 */
class DefaultSynchronizationSettings private constructor(
    context: Context,
    private val config: SyncConfig
) : SynchronizationSettings {

    /**
     * Use Singleton to ensure only one instance per process is created. [LEIP-294]
     *
     * It should be okay to use a Singleton as this is also suggested in the documentation:
     * https://developer.android.com/topic/libraries/architecture/datastore#multiprocess
     */
    companion object {
        @Volatile
        private var instance: DefaultSynchronizationSettings? = null

        fun getInstance(context: Context, config: SyncConfig):
                DefaultSynchronizationSettings {
            return instance ?: synchronized(this) {
                instance ?: DefaultSynchronizationSettings(
                    context.applicationContext,
                    config
                ).also {
                    instance = it
                }
            }.also {
                if (it.config != config) {
                    throw IllegalStateException("Already initialized with different configuration.")
                }
            }
        }
    }

    /**
     * This avoids leaking the context when this object outlives the Activity of Fragment.
     */
    private val appContext = context.applicationContext

    /**
     * The data store with multi-process support.
     *
     * The reason for multi-process support is that some settings are accessed by a background
     * service which runs in another process.
     *
     * Attention:
     * - Never mix SingleProcessDataStore with MultiProcessDataStore for the same file.
     * - We use MultiProcessDataStore, i.e. the preferences can be accessed from multiple processes.
     * - Only create one instance of `DataStore` per file in the same process.
     * - We use ProtoBuf to ensure type safety. Rebuild after changing the .proto file.
     */
    private val dataStore: DataStore<Settings> = MultiProcessDataStoreFactory.create(
        serializer = SettingsSerializer,
        produceFile = {
            // With cacheDir the settings are lost on app restart [RFR-799]
            File("${appContext.filesDir.path}/synchronization.pb")
        },
        migrations = listOf(
            PreferencesMigrationFactory.create(appContext, config.collectorUrl, config.oAuthConfig),
            StoreMigration(config.collectorUrl, config.oAuthConfig)
        )
    )

    init {
        if (!config.collectorUrl.startsWith("https://") && !config.collectorUrl.startsWith("http://")) {
            throw SetupException("Invalid URL protocol")
        }
        if (!config.oAuthConfig.getString("discovery_uri")
                .startsWith("https://") && !config.oAuthConfig.getString("discovery_uri")
                .startsWith("http://")
        ) {
            throw SetupException("Invalid URL protocol")
        }
    }

    /**
     * Sets the URL of the server to upload data to.
     *
     * @param value The URL to save.
     */
    override suspend fun setCollectorUrl(value: String) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setCollectorUrl(value)
                .build()
        }
    }

    /**
     * @return The URL of the server to upload data to.
     */
    override val collectorUrlFlow: Flow<String> = dataStore.data
        .map { settings ->
            settings.collectorUrl
        }

    /**
     * Sets the OAuth configuration JsonObject as String.
     *
     * @param value The configuration to save.
     */
    override suspend fun setOAuthConfiguration(value: String) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setOAuthConfiguration(value)
                .build()
        }
    }

    /**
     * @return The OAuth configuration JsonObject as String.
     */
    override val oAuthConfigurationFlow: Flow<String> = dataStore.data
        .map { settings ->
            settings.oAuthConfiguration
        }
}