/*
 * Copyright 2018-2023 Cyface GmbH
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
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.uploader.Authenticator
import de.cyface.uploader.exception.AccountNotActivated
import de.cyface.uploader.exception.ForbiddenException
import de.cyface.uploader.exception.HostUnresolvable
import de.cyface.uploader.exception.LoginFailed
import de.cyface.uploader.exception.NetworkUnavailableException
import de.cyface.uploader.exception.ServerUnavailableException
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.uploader.exception.TooManyRequestsException
import de.cyface.uploader.exception.UnauthorizedException
import de.cyface.uploader.exception.UnexpectedResponseCode
import java.net.MalformedURLException

/**
 * The CyfaceAuthenticator is called by the [AccountManager] to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 *
 * **ATTENTION:** The [.getAuthToken] method is only
 * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call it
 * directly where we need a fresh token.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 2.0.0
 */
class CyfaceAuthenticator(private val context: Context, private val authenticator: Authenticator) :
    AbstractAccountAuthenticator(context) {
    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle? {
        return null
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse, accountType: String,
        authTokenType: String, requiredFeatures: Array<String>, options: Bundle
    ): Bundle {
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
     * **ATTENTION:** The `#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)` method is only
     * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call
     * it directly where we need a fresh token.
     *
     *
     * For documentation see
     * [AbstractAccountAuthenticator.getAuthToken]
     */
    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?, account: Account,
        authTokenType: String, options: Bundle
    ): Bundle {

        // Invalidate existing token. They expire after 60 seconds, so it's more resourceful to
        // invalidate request a new token for each request.
        val accountManager = AccountManager.get(context)
        accountManager.invalidateAuthToken(
            account.type,
            accountManager.peekAuthToken(account, authTokenType)
        )

        // Request login if no password is stored to get new authToken
        val freshAuthToken: String
        val password = accountManager.getPassword(account)
            ?: return getLoginActivityIntent(response, account, authTokenType)!!

        // Login to get a new authToken
        // Due to the interface we can only throw NetworkErrorException
        // Thus, we report the specific error type via sendErrorIntent()
        freshAuthToken = try {
            login(account.name, password)
        } catch (e: LoginFailed) {
            when (e.cause) {
                is ServerUnavailableException, is ForbiddenException -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.SERVER_UNAVAILABLE.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is MalformedURLException -> {
                    ErrorHandler.sendErrorIntent(context, ErrorCode.MALFORMED_URL.code, e.message)
                    throw NetworkErrorException(e)
                }

                is SynchronisationException -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.SYNCHRONIZATION_ERROR.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is UnauthorizedException -> {
                    ErrorHandler.sendErrorIntent(context, ErrorCode.UNAUTHORIZED.code, e.message)
                    throw NetworkErrorException(e)
                }

                is NetworkUnavailableException -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.NETWORK_UNAVAILABLE.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is TooManyRequestsException -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.TOO_MANY_REQUESTS.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is HostUnresolvable -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.HOST_UNRESOLVABLE.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is UnexpectedResponseCode -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.UNEXPECTED_RESPONSE_CODE.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                is AccountNotActivated -> {
                    ErrorHandler.sendErrorIntent(
                        context,
                        ErrorCode.ACCOUNT_NOT_ACTIVATED.code,
                        e.message
                    )
                    throw NetworkErrorException(e)
                }

                else -> {
                    // Unknown sub-type of `UploadFailed`
                    throw IllegalArgumentException(e)
                }
            }
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException(e)
        }

        // Return a bundle containing the token
        Log.v(TAG, "Fresh authToken: **" + freshAuthToken.substring(freshAuthToken.length - 7))
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        result.putString(AccountManager.KEY_AUTHTOKEN, freshAuthToken)
        return result
    }

    /**
     * Returns an [Intent] which displays the `AccountAuthenticatorActivity` to re-prompt for their
     * credentials.
     *
     * @param response the [AccountAuthenticatorResponse] requested by
     * [.getAuthToken]
     * @param account the [Account] for whom an authToken was requested
     * @param authTokenType the [AccountManager.KEY_AUTHTOKEN] type requested
     * @return the [Bundle] containing the requesting `Intent`
     */
    private fun getLoginActivityIntent(
        response: AccountAuthenticatorResponse?,
        account: Account, authTokenType: String
    ): Bundle? {
        if (LOGIN_ACTIVITY == null) {
            Log.w(TAG, "Please set LOGIN_ACTIVITY.")
            return null
        }
        Log.v(TAG, "Spawn LoginActivity as no password exists.")
        val intent = Intent(context, LOGIN_ACTIVITY)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, authTokenType)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
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

    /**
     * Logs into the server to get a valid authToken.
     *
     * @param username The username that is used by the application to login to the
     * server.
     * @param password The password belonging to the account with the `username`
     * logging in to the Cyface server.
     * @return The currently valid auth token to be used by further requests from this application.
     * @throws LoginFailed when an expected error occurred, so that the UI can handle this.
     * @throws MalformedURLException if the endpoint address provided is malformed.
     */
    @Throws(LoginFailed::class, MalformedURLException::class)
    private fun login(username: String, password: String): String {

        // Login to get JWT token
        Log.d(
            TAG,
            "Authenticating at " + authenticator.loginEndpoint() + " with " + username + " / " + password
        )
        return authenticator.authenticate(username, password)
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
    }
}