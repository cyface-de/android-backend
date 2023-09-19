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
import android.util.Log
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import de.cyface.synchronization.Constants.TAG
import de.cyface.synchronization.Settings
import de.cyface.utils.Constants
import org.json.JSONObject

/**
 * Factory for the migration which imports preferences from the previously used SharedPreferences.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.9.0
 */
object PreferencesMigrationFactory {

    /**
     * The filename, keys and defaults of the preferences, historically.
     *
     * *Don't change this, this is migration code!*
     */
    private const val PREFERENCES_NAME = "AppPreferences"
    private const val SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint"
    private const val OAUTH_CONFIG_SETTINGS_KEY = "de.cyface.oauth.config"

    /**
     * @param context The context to search and access the old SharedPreferences from.
     * @param collectorUrl The URL of the Collector API, e.g. "https://example.com/api/v4".
     * @param oAuthConfig The configuration required for the OAuth server.
     * @return The migration code which imports preferences from the SharedPreferences if found.
     */
    fun create(
        context: Context,
        collectorUrl: String,
        oAuthConfig: JSONObject
    ): SharedPreferencesMigration<Settings> {
        return SharedPreferencesMigration(
            context,
            PREFERENCES_NAME,
            migrate = { preferences, settings ->
                migratePreferences(preferences, settings, collectorUrl, oAuthConfig)
            }
        )
    }

    private fun migratePreferences(
        preferences: SharedPreferencesView,
        settings: Settings,
        defaultCollectorUrl: String,
        defaultOAuthConfig: JSONObject
    ): Settings {
        Log.i(TAG, "Migrating from shared preferences to version 1")
        return settings.toBuilder()
            // Setting version to 1 as it would else default to Protobuf default of 0 which would
            // trigger the StoreMigration from 0 -> 1 which ignores previous settings.
            // This way the last supported version of SharedPreferences is hard-coded here and
            // then the migration steps in StoreMigration starting at version 1 continues from here.
            .setVersion(1)
            .setCollectorUrl(
                preferences.getString(
                    SYNC_ENDPOINT_URL_SETTINGS_KEY,
                    defaultCollectorUrl
                )
            )
            .setOAuthConfiguration(
                preferences.getString(
                    OAUTH_CONFIG_SETTINGS_KEY,
                    defaultOAuthConfig.toString()
                )
            )
            .build()
    }
}