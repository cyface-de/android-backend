package de.cyface.synchronization

interface Auth {

    /**
     * Makes sure that a valid access token is available before executing the provided action.
     *
     * @param action The method which will be executed.
     */
    fun performActionWithFreshTokens(
        action: (
            accessToken: String?,
            idToken: String?,
            ex: Exception?
        ) -> Unit
    )
}