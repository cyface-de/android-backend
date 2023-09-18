/*
 * Copyright 2019-2023 Cyface GmbH
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
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import de.cyface.synchronization.settings.SynchronizationSettings

/**
 * The CyfaceAuthenticator is called by the [AccountManager] to fulfill all account relevant
 * tasks such as getting stored auth-tokens and handling user authentication against the Movebis server.
 *
 * **ATTENTION:** The [.getAuthToken] method is only
 * called by the system if no token is cached. As our cyface flavour logic to invalidate token currently is in this
 * method, we call it directly where we need a fresh token.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.0
 * @since 3.0.0
 */
class CyfaceAuthenticator(private val context: Context) :
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
    ): Bundle? {
        return null
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle
    ): Bundle? {
        return null
    }

    /**
     * The getAuthToken handling is different from the Cyface flavour: There we request a new token on each
     * request but on here it's set up once for each account and valid until the end of the campaign.
     * If the Movebis server is offline, the app should silently do nothing.
     *
     * **ATTENTION:** The `#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)` method is only
     * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call
     * it directly where we need a fresh token.
     *
     * For documentation see
     * [AbstractAccountAuthenticator.getAuthToken]
     */
    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?, account: Account,
        authTokenType: String, options: Bundle
    ): Bundle? {

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        val accountManager = AccountManager.get(context)

        // Check if there is an existing token.
        val authToken = accountManager.peekAuthToken(account, authTokenType)

        // No auth token exists. This should never be the case for movebis users. Ignore request.
        if (TextUtils.isEmpty(authToken)) {
            Log.v(
                TAG,
                String.format(
                    "Auth Token was empty for account %s! Ignoring request.",
                    account.name
                )
            )
            return null
        }

        // Return the auth token
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        result.putString(AccountManager.KEY_AUTHTOKEN, authToken)
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

    companion object {
        private const val TAG = "de.cyface.auth"

        /**
         * Custom settings used by this library.
         */
        lateinit var settings: SynchronizationSettings
    }
}