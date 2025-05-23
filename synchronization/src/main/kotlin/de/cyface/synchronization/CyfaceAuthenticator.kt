/*
 * Copyright 2018-2024 Cyface GmbH
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

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.cyface.synchronization.settings.SynchronizationSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The CyfaceAuthenticator is called by the [AccountManager] to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 *
 * **ATTENTION:** The [.getAuthToken] method is only
 * called by the system if no token is cached. As our logic to invalidate tokens is in this method,
 * we call it directly where we need a fresh token.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
class CyfaceAuthenticator(private val context: Context) :
    AbstractAccountAuthenticator(context), LoginActivityProvider {

    var auth: OAuth2 = OAuth2(context, settings, "CyfaceAuthenticator")

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle? {
        return null
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse, accountType: String,
        authTokenType: String, requiredFeatures: Array<String>?, options: Bundle
    ): Bundle {
        Log.d(TAG, "CyfaceAuthenticator.addAccount: start LoginActivity to authenticate")
        val intent = Intent(context, LOGIN_ACTIVITY)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle
    ): Bundle? {
        return null
    }

    /**
     * The `#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)` method is only
     * called by the system if no token is cached.
     *
     * But as we're calling `StateManager.current.performActionWithFreshTokens` the token should
     * always be automatically refreshed, so this method is probably not being called at all.
     */
    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?, account: Account,
        authTokenType: String, options: Bundle?
    ): Bundle {

        // Login to get a new authToken
        // Due to the interface we can only throw NetworkErrorException
        // Thus, we report the specific error type via sendErrorIntent()
        val freshAuthToken = try {
            fetchFreshTokenSync()
        } catch (e: RuntimeException) {
            ErrorHandler.sendErrorIntent(
                context,
                ErrorHandler.ErrorCode.UNKNOWN.code,
                e.message,
                false // login currently only happens while the user is active
            )
            throw NetworkErrorException(e)
        }

        // Refresh token in account manager, too.
        val accountManager = AccountManager.get(context)
        accountManager.setAuthToken(
            account,
            account.type,
            accountManager.peekAuthToken(account, authTokenType)
        )

        // Return a bundle containing the token
        //Log.v(TAG, "Fresh authToken: **" + freshAuthToken.substring(freshAuthToken.length - 7))
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        result.putString(AccountManager.KEY_AUTHTOKEN, freshAuthToken)
        Log.v(TAG, "getAuthToken: Token refresh requested (async)")
        return result
    }

    /**
     * `runBlocking` is necessary here as `getAuthToken()` is a synchronous method called by
     * Android `AccountAuthenticator` and, thus, er cannot make it suspend.
     */
    private fun fetchFreshTokenSync(): String = runBlocking(Dispatchers.IO) {
        val deferred = CompletableDeferred<String>()
        val success = auth.performTokenRequest(auth.createTokenRefreshRequest()) { resp, ex ->
            deferred.complete(auth.handleAccessTokenResponse(resp, ex))
        }
        require(success) { "Client authentication method is unsupported" }
        deferred.await()
    }

    override fun getAuthTokenLabel(authTokenType: String): String {
        return "JWT Token"
    }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse, account: Account, authTokenType: String,
        options: Bundle
    ): Bundle? {
        return null
    }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<String>
    ): Bundle? {
        return null
    }

    companion object {
        private const val TAG = "de.cyface.auth"

        /**
         * A reference to the implementation of the `AccountAuthenticatorActivity` which is called by Android and its
         * [AccountManager]. This happens e.g. when a token is requested while none is cached, using
         * [.getAuthToken].
         */
        @JvmField
        var LOGIN_ACTIVITY: Class<out Activity?>? = null

        /**
         * Custom settings used by this library.
         */
        lateinit var settings: SynchronizationSettings
    }

    override fun getLoginActivity(): Class<out Activity?>? {
        return LOGIN_ACTIVITY
    }
}