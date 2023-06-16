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
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import de.cyface.uploader.Authenticator
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse

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

    /**
     * The service used for authorization.
     */
    private var mAuthService: AuthorizationService

    /**
     * The authorization state.
     */
    private var mStateManager: AuthStateManager

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private var mConfiguration: Configuration

    init {
        // Authorization
        mStateManager = AuthStateManager.getInstance(context)
        //mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(context)
        val config = Configuration.getInstance(context)
        //if (config.hasConfigurationChanged()) {
            // This happens when starting the app after a fresh installation
            //throw IllegalArgumentException("config changed (CyfaceAuthenticator)")
            /*show("Authentifizierung ist abgelaufen")
            Handler().postDelayed({signOut()}, 2000)*/
            //return
        //}
        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
    }

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
     * **ATTENTION:** The `#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)` method is only
     * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call
     * it directly where we need a fresh token.
     *
     *
     * For documentation see
     * [AbstractAccountAuthenticator.getAuthToken]
     */
    @Throws(NetworkErrorException::class)
    override fun getAuthToken( // FIXME move previous implementation to package v6 (cyface app)
        response: AccountAuthenticatorResponse?, account: Account,
        authTokenType: String, options: Bundle?
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
        /*val password = accountManager.getPassword(account)
            ?: return getLoginActivityIntent(response, account, authTokenType)!!*/

        // Login to get a new authToken
        // Due to the interface we can only throw NetworkErrorException
        // Thus, we report the specific error type via sendErrorIntent()
        //freshAuthToken = try {
            //login(account.name, password)
            // FIXME: remove this? While we use performActionWithFreshToken the token is automatically renewed when needed
            performTokenRequest(mStateManager.current.createTokenRefreshRequest()) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
                handleAccessTokenResponse(
                    tokenResponse,
                    authException
                )
            }
        /*} catch (e: LoginFailed) {
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
        }*/

        // Return a bundle containing the token
        //Log.v(TAG, "Fresh authToken: **" + freshAuthToken.substring(freshAuthToken.length - 7))
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        //result.putString(AccountManager.KEY_AUTHTOKEN, freshAuthToken)
        Log.v(TAG, "getAuthToken: Token refresh requested (async)")
        return result
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

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {
        val clientAuthentication = try {
            mStateManager.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(
                TAG, "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)", ex
            )
            throw IllegalArgumentException("Client authentication method is unsupported")
            //displayNotAuthorized("Client authentication method is unsupported")
            return
        }
        mAuthService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mStateManager.updateAfterTokenResponse(tokenResponse, authException)
        //runOnUiThread { displayAuthorized("handleAccessTokenResponse") }
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