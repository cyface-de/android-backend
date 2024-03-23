package de.cyface.synchronization.settings

import kotlinx.coroutines.flow.Flow

interface SynchronizationSettings {

    /**
     * Sets the URL of the server to upload data to.
     *
     * @param value The URL to save.
     */
    suspend fun setCollectorUrl(value: String)

    /**
     * @return The URL of the server to upload data to as a Kotlin Flow.
     */
    val collectorUrlFlow: Flow<String>

    /**
     * Sets the OAuth configuration JsonObject as String.
     *
     * @param value The configuration to save.
     */
    suspend fun setOAuthConfiguration(value: String)

    /**
     * @return The OAuth configuration JsonObject as String as a Kotlin Flow.
     */
    val oAuthConfigurationFlow: Flow<String>
}