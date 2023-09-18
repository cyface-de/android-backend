package de.cyface.synchronization

import de.cyface.synchronization.Auth

class MockAuth: Auth {
    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        action("eyTestToken", null, null)
    }
}