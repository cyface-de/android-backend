package de.cyface.datacapturing

import de.cyface.synchronization.settings.SynchronizationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class MockSynchronizationSettings : SynchronizationSettings {

    private var _collectorUrl = MutableStateFlow("https://TEST_URL/")
    private var _oAuthConfig = MutableStateFlow(JSONObject().put("discovery_uri", "https://TEST_URL/").toString())

    override suspend fun setCollectorUrl(value: String) {
        _collectorUrl.value = value
    }

    override val collectorUrlFlow: Flow<String>
        get() = _collectorUrl.asStateFlow()

    override suspend fun setOAuthConfiguration(value: String) {
        _oAuthConfig.value = value
    }

    override val oAuthConfigurationFlow: Flow<String>
        get() = _oAuthConfig.asStateFlow()
}
