package de.cyface.synchronization

interface Auth {

    fun performActionWithFreshTokens(
        action: (
            accessToken: String?,
            idToken: String?,
            ex: Exception?
        ) -> Unit
    )
}