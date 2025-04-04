/*
 * Copyright 2023-2025 Cyface GmbH
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

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentActivity
import de.cyface.synchronization.Constants.TAG
import de.cyface.synchronization.CyfaceSyncService.Companion.AUTH_TOKEN_TYPE
import de.cyface.synchronization.settings.SynchronizationSettings
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONObject

/**
 * The OAuth2 class facilitates the authorization process in Android using OAuth2.0 mechanism.
 *
 * It helps in managing tokens, performing code exchanges, updating account credentials, creating
 * accounts, and handling sessions.
 *
 * @author Armin Schnabel
 * @param context The context to load settings and accounts from.
 * @param settings The settings which store the user preferences.
 */
class OAuth2(context: Context, settings: SynchronizationSettings, caller: String) : Auth {
    /**
     * The service used for authorization.
     */
    private var authService: AuthorizationService

    /**
     * The authorization state.
     */
    private var stateManager: AuthStateManager = AuthStateManager.getInstance(context)

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private var configuration: Configuration = Configuration.getInstance(context, settings)

    init {
        /*if (config.hasConfigurationChanged()) {
            //throw IllegalArgumentException("config changed (SyncAdapter)")
            Toast.makeText(context, "Ignoring: config changed (SyncAdapter)", Toast.LENGTH_SHORT).show()
            //Handler().postDelayed({signOut()}, 2000)
            //return
        }*/
        Log.i(TAG, "OAuth2 init ($caller)")
        authService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(configuration.connectionBuilder)
                .build()
        )
    }

    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        stateManager.current.performActionWithFreshTokens(authService, action)
    }

    @WorkerThread
    fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?,
        accountType: String,
        context: Context,
        authority: String
    ): Boolean {
        stateManager.updateAfterTokenResponse(tokenResponse, authException)
        return if (!stateManager.current.isAuthorized) {
            false // UI calls `onUnauthorized`
        } else {
            // This is called when the code exchange was successful (after user just logged in)
            // updateAccount() as we did in the pre-OAuth2 `LoginActivity.attemptLogin()` flow

            // The CyfaceAuthenticator reads the credentials from the account so we store them there
            val username =
                stateManager.current.parsedIdToken!!.additionalClaims["preferred_username"].toString()
            val accessToken = stateManager.current.accessToken.toString()
            val refreshToken = stateManager.current.refreshToken.toString()
            updateAccount(context, username, accessToken, refreshToken, accountType, authority)

            true // UI calls `onAuthorized`
        }
    }

    override fun userId(): String? {
        return stateManager.current.parsedIdToken?.subject
    }

    @MainThread
    fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ): Boolean {
        val clientAuthentication = try {
            stateManager.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(
                TAG, "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)", ex
            )
            return false // UI calls `onUnauthorized`
        }
        authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
        return true
    }

    @WorkerThread
    fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ): String {
        stateManager.updateAfterTokenResponse(tokenResponse, authException)
        return stateManager.current.accessToken!!
    }

    /**
     * Updates the credentials
     *
     * @param context The [Context] required to add an [Account]
     * @param username The username of the account
     * @param accessToken The token to generate new tokens
     * @param accessToken The token to access user data
     */
    private fun updateAccount(
        context: Context,
        username: String,
        accessToken: String,
        refreshToken: String,
        accountType: String,
        authority: String
    ) {
        require(username.isNotEmpty())
        require(accessToken.isNotEmpty())
        require(refreshToken.isNotEmpty())
        val accountManager = AccountManager.get(context)
        val account = Account(username, accountType)

        // Update credentials if the account already exists
        var accountUpdated = false
        val existingAccounts = accountManager.getAccountsByType(accountType)
        for (existingAccount in existingAccounts) {
            if (existingAccount == account) {
                accountManager.setUserData(account, "refresh_token", refreshToken)
                accountManager.setAuthToken(account, AUTH_TOKEN_TYPE, accessToken)
                accountUpdated = true
                Log.d(TAG, "Updated existing account.")
            }
        }

        // Add new account when it does not yet exist
        if (!accountUpdated) {

            // Delete unused Cyface accounts
            for (existingAccount in existingAccounts) {
                accountManager.removeAccountExplicitly(existingAccount)
                Log.d(TAG, "Removed existing account: $existingAccount")
            }
            createAccount(context, username, accessToken, refreshToken, accountType, authority)
        }
        require(accountManager.getAccountsByType(accountType).size == 1)
    }

    /**
     * Creates a temporary `Account` which can only be used to check the credentials.
     *
     * **ATTENTION:** If the login is successful you need to use `WiFiSurveyor.makeAccountSyncable`
     * to ensure the `WifiSurveyor` works as expected. We cannot inject the `WiFiSurveyor` as the
     * `LoginActivity` is called by Android.
     *
     * @param context The current Android context (i.e. Activity or Service).
     * @param username The username of the account to be created.
     * @param accessToken The token to access user data.
     * @param refreshToken The token to generate new tokens.
     * @param accountType The type of account to create.
     * @param authority The provider to create an account for.
     */
    private fun createAccount(
        context: Context,
        username: String,
        accessToken: String,
        refreshToken: String,
        accountType: String,
        authority: String
    ) {
        val accountManager = AccountManager.get(context)
        val newAccount = Account(username, accountType)
        val userData = Bundle()
        userData.putString("refresh_token", refreshToken)
        // As we use OAuth2 the password is not known to this client and is set to `null`.
        // The same occurs in alternative Authenticators such as in `SRDataCapturingService`.
        require(accountManager.addAccountExplicitly(newAccount, null, userData))
        accountManager.setAuthToken(newAccount, AUTH_TOKEN_TYPE, accessToken)
        require(accountManager.getAccountsByType(accountType).size == 1)
        Log.v(TAG, "New account added")
        ContentResolver.setSyncAutomatically(newAccount, authority, false)
        // Synchronization can be disabled via {@link CyfaceDataCapturingService#setSyncEnabled}
        ContentResolver.setIsSyncable(newAccount, authority, 1)
        // Do not use validateAccountFlags in production code as periodicSync flags are set async

        // PeriodicSync and syncAutomatically is set dynamically by the {@link WifiSurveyor}
    }

    fun isAuthorized(): Boolean {
        return stateManager.current.isAuthorized
    }

    fun updateAfterAuthorization(response: AuthorizationResponse?, ex: AuthorizationException?) {
        stateManager.updateAfterAuthorization(response, ex)
    }

    fun dispose() {
        authService.dispose()
    }

    fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = stateManager.current
        if (currentState.authorizationServiceConfiguration != null) {
            // Replace the state with a fresh `AuthState`
            val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
            if (currentState.lastRegistrationResponse != null) {
                clearedState.update(currentState.lastRegistrationResponse)
            }
            stateManager.replace(clearedState)

            // [LEIP-233] Completely deleting the sharedPreferences/AuthState.xml file does not
            // fix the `Credentials incorrect` error after re-login (app restart required)
            //stateManager.deletePreferencesFile()
        } else {
            Log.w(TAG, "No authorization service configuration to sign out")
        }

        // Invalidate all tokens to ensure they are no longer used
        dispose()
    }

    override fun endSession(activity: FragmentActivity) {
        val currentState = stateManager.current
        val config = currentState.authorizationServiceConfiguration!!
        if (config.endSessionEndpoint != null) {
            val endSessionIntent = authService.getEndSessionRequestIntent(
                EndSessionRequest.Builder(config)
                    .setIdTokenHint(currentState.idToken)
                    .setPostLogoutRedirectUri(configuration.endSessionRedirectUri)
                    .build()
            )
            // This opens a browser window to inform the auth server that the user wants to log out.
            // The window closes after a split second and calls `MainActivity.onActivityResult`
            // where `signOut()` is executed which also removes the account from the account manager.
            activity.startActivityForResult(endSessionIntent, END_SESSION_REQUEST_CODE)
        } else {
            error("Auth server does not provide an end session endpoint")
            //signOut()
        }
    }

    fun createTokenRefreshRequest(): TokenRequest {
        return stateManager.current.createTokenRefreshRequest()
    }


    companion object {
        const val END_SESSION_REQUEST_CODE = 911

        fun oauthConfig(oauthRedirect: String, oauthDiscovery: String): JSONObject {
            return JSONObject()
                .put("client_id", "android-app")
                .put("redirect_uri", oauthRedirect)
                .put("end_session_redirect_uri", oauthRedirect)
                .put("authorization_scope", "openid email profile")
                .put("discovery_uri", oauthDiscovery)
                .put("https_required", true)
        }
    }
}
