package de.cyface.synchronization

import android.content.Context
import androidx.core.content.edit
import de.cyface.utils.AppPreferences

class CustomPreferences(context: Context): AppPreferences(context) {

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