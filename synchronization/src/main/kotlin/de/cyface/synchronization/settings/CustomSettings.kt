/*
 * Copyright 2023 Cyface GmbH
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
import de.cyface.synchronization.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Custom settings used by this library.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.8.1
 */
class CustomSettings(context: Context) {

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
            File("${appContext.cacheDir.path}/synchronization.pb")
        },
        // TODO [RFR-788]: Add a test to ensure version is not set to 1 if no SharedPreferences file exist
        // TODO [RFR-788]: Add a test which ensures preferences migration works and not default values are used
        // TODO [RFR-788]: Add a test where the version is already 1 and SharedPreferences file is found
        // TODO [RFR-788]: Add a test where the version is 1 and ensure no migration is executed / defaults are set
        migrations = listOf(PreferencesMigrationFactory.create(appContext))
    )

    /**
     * Sets the URL of the server to upload data to.
     *
     * @param value The URL to save.
     */
    suspend fun setCollectorUrl(value: String) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setCollectorUrl(value)
                .build()
        }
    }

    /**
     * @return The URL of the server to upload data to.
     */
    val collectorUrlFlow: Flow<String> = dataStore.data
        .map { settings ->
            settings.collectorUrl
        }

    /**
     * Sets the OAuth configuration JsonObject as String.
     *
     * @param value The configuration to save.
     */
    suspend fun setOAuthConfiguration(value: String) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setOAuthConfiguration(value)
                .build()
        }
    }

    /**
     * @return The OAuth configuration JsonObject as String.
     */
    val oAuthConfigurationFlow: Flow<String> = dataStore.data
        .map { settings ->
            settings.oAuthConfiguration
        }
}