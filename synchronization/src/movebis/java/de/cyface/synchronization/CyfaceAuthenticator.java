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
package de.cyface.synchronization;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.uploader.Authenticator;

/**
 * The CyfaceAuthenticator is called by the {@link AccountManager} to fulfill all account relevant
 * tasks such as getting stored auth-tokens and handling user authentication against the Movebis server.
 * <p>
 * <b>ATTENTION:</b> The {@link #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)} method is only
 * called by the system if no token is cached. As our cyface flavour logic to invalidate token currently is in this
 * method, we call it directly where we need a fresh token.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";

    // The `authenticator` parameter is required in the Cyface flavor of `CyfaceAuthenticator`
    public CyfaceAuthenticator(final @NonNull Context context, @SuppressWarnings("unused") final @NonNull Authenticator authenticator) {
        super(context);
        this.context = context;
    }

    @Override
    public Bundle editProperties(final @NonNull AccountAuthenticatorResponse response,
            final @NonNull String accountType) {
        return null;
    }

    @Override
    public Bundle addAccount(final AccountAuthenticatorResponse response, final String accountType,
            final String authTokenType, final String[] requiredFeatures, final Bundle options) {
        return null;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
        return null;
    }

    /**
     * The getAuthToken handling is different from the Cyface flavour: There we request a new token on each
     * request but on here it's set up once for each account and valid until the end of the campaign.
     * If the Movebis server is offline, the app should silently do nothing.
     * <p>
     * <b>ATTENTION:</b> The {@code #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)} method is only
     * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call
     * it directly where we need a fresh token.
     * <p>
     * For documentation see
     * {@link AbstractAccountAuthenticator#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)}
     */
    @SuppressWarnings({"RedundantThrows", "RedundantSuppression"}) // Cyface flavour throws it, too
    @Override
    @Nullable
    public Bundle getAuthToken(final @Nullable AccountAuthenticatorResponse response, final @NonNull Account account,
            final @NonNull String authTokenType, final Bundle options) throws NetworkErrorException {

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        final AccountManager accountManager = AccountManager.get(context);

        // Check if there is an existing token.
        String authToken = accountManager.peekAuthToken(account, authTokenType);

        // No auth token exists. This should never be the case for movebis users. Ignore request.
        if (TextUtils.isEmpty(authToken)) {
            Log.v(TAG, String.format("Auth Token was empty for account %s! Ignoring request.", account.name));
            return null;
        }

        // Return the auth token
        final Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
        return result;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return "JWT Token";
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
            Bundle options) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
        return null;
    }
}