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
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import de.cyface.synchronization.Settings

/**
 * Factory for the migration which imports preferences from the previously used SharedPreferences.
 *
 * @author Armin Schnabel
 * @since 4.3.0
 */
object PreferencesMigrationFactory {

    /**
     * The filename, keys and defaults of the preferences, historically.
     *
     * *Don't change this, this is migration code!*
     */
    // FIXME: ensure this works, as multiple PreferencesMigrationFactories import data from that file
    private const val PREFERENCES_NAME = "AppPreferences"
    private const val SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint"
    private const val OAUTH_CONFIG_SETTINGS_KEY = "de.cyface.oauth.config"

    /**
     * @param context The context to search and access the old SharedPreferences from.
     * @return The migration code which imports preferences from the SharedPreferences if found.
     */
    fun create(context: Context): SharedPreferencesMigration<Settings> {
        return SharedPreferencesMigration(
            context,
            PREFERENCES_NAME,
            migrate = ::migratePreferences
        )
    }

    private fun migratePreferences(
        preferences: SharedPreferencesView,
        settings: Settings
    ): Settings {
        return settings.toBuilder()
            .setVersion(1) // Ensure the migrated values below are used instead of default values.
            //FIXME: test both because of null
            .setCollectorUrl(preferences.getString(SYNC_ENDPOINT_URL_SETTINGS_KEY, null))
            .setOAuthConfiguration(preferences.getString(OAUTH_CONFIG_SETTINGS_KEY, null))
            .build()
    }
}