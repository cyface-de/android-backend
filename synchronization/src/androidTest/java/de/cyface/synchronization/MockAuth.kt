package de.cyface.synchronization

class MockAuth: Auth {
    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        action("eyTestToken", null, null)
    }
}