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
package de.cyface.synchronization

import android.content.Context
import androidx.core.content.edit
import de.cyface.utils.AppPreferences

/**
 * Preferences persisted with SharedPreferences.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.8.1
 */
class CustomPreferences(context: Context) : AppPreferences(context) {

    fun saveCollectorUrl(collectorUrl: String) {
        preferences.edit {
            putString(SYNC_ENDPOINT_URL_SETTINGS_KEY, collectorUrl)
            apply()
        }
    }

    fun getCollectorUrl(): String? {
        return preferences.getString(SYNC_ENDPOINT_URL_SETTINGS_KEY, null)
    }

    fun saveOAuthUrl(oAuthUrl: String?) {
        preferences.edit {
            putString(OAUTH_CONFIG_SETTINGS_KEY, oAuthUrl)
            apply()
        }
    }

    fun getOAuthUrl(): String? {
        return preferences.getString(OAUTH_CONFIG_SETTINGS_KEY, null)
    }

    companion object {
        /**
         * The settings key used to identify the settings storing the URL of the server to upload data to.
         */
        const val SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint"

        /**
         * The settings key used to identify the settings storing the OAuth configuration JsonObject as String.
         */
        const val OAUTH_CONFIG_SETTINGS_KEY = "de.cyface.oauth.config"
    }
}