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
package de.cyface.synchronization;

import static de.cyface.synchronization.ErrorHandler.sendErrorIntent;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.ACCOUNT_NOT_ACTIVATED;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.HOST_UNRESOLVABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.MALFORMED_URL;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.NETWORK_UNAVAILABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.SERVER_UNAVAILABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.TOO_MANY_REQUESTS;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UNAUTHORIZED;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UNEXPECTED_RESPONSE_CODE;

import java.net.MalformedURLException;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.uploader.Authenticator;
import de.cyface.uploader.exception.AccountNotActivated;
import de.cyface.uploader.exception.ForbiddenException;
import de.cyface.uploader.exception.HostUnresolvable;
import de.cyface.uploader.exception.LoginFailed;
import de.cyface.uploader.exception.NetworkUnavailableException;
import de.cyface.uploader.exception.ServerUnavailableException;
import de.cyface.uploader.exception.SynchronisationException;
import de.cyface.uploader.exception.TooManyRequestsException;
import de.cyface.uploader.exception.UnauthorizedException;
import de.cyface.uploader.exception.UnexpectedResponseCode;

/**
 * The CyfaceAuthenticator is called by the {@link AccountManager} to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 * <p>
 * <b>ATTENTION:</b> The {@link #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)} method is only
 * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call it
 * directly where we need a fresh token.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";
    private final Authenticator authenticator;
    /**
     * A reference to the implementation of the {@link AccountAuthenticatorActivity} which is called by Android and its
     * {@link AccountManager}. This happens e.g. when a token is requested while none is cached, using
     * {@link #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)}.
     */
    public static Class<? extends AccountAuthenticatorActivity> LOGIN_ACTIVITY;

    public CyfaceAuthenticator(final @NonNull Context context, final @NonNull Authenticator authenticator) {
        super(context);
        this.context = context;
        this.authenticator = authenticator;
    }

    @Override
    public Bundle editProperties(final @NonNull AccountAuthenticatorResponse response,
            final @NonNull String accountType) {
        return null;
    }

    @Override
    public Bundle addAccount(final AccountAuthenticatorResponse response, final String accountType,
            final String authTokenType, final String[] requiredFeatures, final Bundle options) {
        final Intent intent = new Intent(context, LOGIN_ACTIVITY);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
        return null;
    }

    /**
     * <b>ATTENTION:</b> The {@code #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)} method is only
     * called by the system if no token is cached. As our logic to invalidate token currently is in this method, we call
     * it directly where we need a fresh token.
     * <p>
     * For documentation see
     * {@link AbstractAccountAuthenticator#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)}
     */
    @Override
    public Bundle getAuthToken(final @Nullable AccountAuthenticatorResponse response, final @NonNull Account account,
            final @NonNull String authTokenType, final Bundle options) throws NetworkErrorException {

        // Invalidate existing token. They expire after 60 seconds, so it's more resourceful to
        // invalidate request a new token for each request.
        final AccountManager accountManager = AccountManager.get(context);
        accountManager.invalidateAuthToken(account.type, accountManager.peekAuthToken(account, authTokenType));

        // Request login if no password is stored to get new authToken
        final String freshAuthToken;
        final String password = accountManager.getPassword(account);
        if (password == null) {
            return getLoginActivityIntent(response, account, authTokenType);
        }

        // Login to get a new authToken
        // Due to the interface we can only throw NetworkErrorException
        // Thus, we report the specific error type via sendErrorIntent()
        try {
            freshAuthToken = login(account.name, password);
        } catch (final LoginFailed e) {
            var cause = e.getCause();
            if (cause instanceof ServerUnavailableException || cause instanceof ForbiddenException) {
                sendErrorIntent(context, SERVER_UNAVAILABLE.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof MalformedURLException) {
                sendErrorIntent(context, MALFORMED_URL.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof SynchronisationException) {
                sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof UnauthorizedException) {
                sendErrorIntent(context, UNAUTHORIZED.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof NetworkUnavailableException) {
                sendErrorIntent(context, NETWORK_UNAVAILABLE.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof TooManyRequestsException) {
                sendErrorIntent(context, TOO_MANY_REQUESTS.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof HostUnresolvable) {
                sendErrorIntent(context, HOST_UNRESOLVABLE.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof UnexpectedResponseCode) {
                sendErrorIntent(context, UNEXPECTED_RESPONSE_CODE.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else if (cause instanceof AccountNotActivated) {
                sendErrorIntent(context, ACCOUNT_NOT_ACTIVATED.getCode(), e.getMessage());
                throw new NetworkErrorException(e);
            } else {
                // Unknown sub-type of `UploadFailed`
                throw new IllegalArgumentException(e);
            }
        } catch (final MalformedURLException e){
            throw new IllegalArgumentException(e);
        }

        // Return a bundle containing the token
        Log.v(TAG, "Fresh authToken: **" + freshAuthToken.substring(freshAuthToken.length() - 7));
        final Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putString(AccountManager.KEY_AUTHTOKEN, freshAuthToken);
        return result;
    }

    /**
     * Returns an {@link Intent} which displays the {@link AccountAuthenticatorActivity} to re-prompt for their
     * credentials.
     *
     * @param response the {@link AccountAuthenticatorResponse} requested by
     *            {@link #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)}
     * @param account the {@link Account} for whom an authToken was requested
     * @param authTokenType the {@link AccountManager#KEY_AUTHTOKEN} type requested
     * @return the {@link Bundle} containing the requesting {@code Intent}
     */
    @Nullable
    private Bundle getLoginActivityIntent(@Nullable final AccountAuthenticatorResponse response,
            @NonNull final Account account, @NonNull final String authTokenType) {
        if (LOGIN_ACTIVITY == null) {
            Log.w(TAG, "Please set LOGIN_ACTIVITY.");
            return null;
        }

        Log.v(TAG, "Spawn LoginActivity as no password exists.");
        final Intent intent = new Intent(context, LOGIN_ACTIVITY);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, authTokenType);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
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

    /**
     * Logs into the server to get a valid authToken.
     *
     * @param username The username that is used by the application to login to the
     *            server.
     * @param password The password belonging to the account with the {@code username}
     *            logging in to the Cyface server.
     * @return The currently valid auth token to be used by further requests from this application.
     * @throws LoginFailed when an expected error occurred, so that the UI can handle this.
     * @throws MalformedURLException if the endpoint address provided is malformed.
     */
    private String login(final @NonNull String username, final @NonNull String password) throws LoginFailed, MalformedURLException {

        // Login to get JWT token
        Log.d(TAG, "Authenticating at " + authenticator.loginEndpoint() + " with " + username + " / " + password);
        return authenticator.authenticate(username, password);
    }
}
