package de.cyface.synchronization

class MovebisAuth : Auth {
    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        action(null, null, null)
    }
}