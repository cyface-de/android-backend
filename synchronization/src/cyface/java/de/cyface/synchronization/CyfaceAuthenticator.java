package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.ERROR_CODE_EXTRA;
import static de.cyface.synchronization.Constants.ERROR_INTENT;
import static de.cyface.synchronization.Constants.HTTP_CODE_EXTRA;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * The CyfaceAuthenticator is called by the {@link AccountManager} to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";
    private final Http http;
    /**
     * A reference to the LoginActivity for the Android's AccountManager workflow to start when
     * an authToken is requested but not available.
     */
    public static Class<? extends AccountAuthenticatorActivity> LOGIN_ACTIVITY;

    public CyfaceAuthenticator(final @NonNull Context context) {
        super(context);
        this.context = context;
        this.http = new CyfaceHttpConnection();
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

    @Override
    public Bundle getAuthToken(final @Nullable AccountAuthenticatorResponse response, final @NonNull Account account,
            final @NonNull String authTokenType, final Bundle options) throws NetworkErrorException {

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        final AccountManager am = AccountManager.get(context);

        String authToken = am.peekAuthToken(account, authTokenType);

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(authToken)) {
            Log.v(TAG, String.format("Auth Token was empty for account %s!", account.name));
            final String password = am.getPassword(account);
            Log.v(TAG, String.format("Password: %s", password));
            if (password != null) {
                try {
                    authToken = initSync(account.name, password);
                    Log.v(TAG, String.format("Auth token: %s", authToken));
                } catch (final ServerUnavailableException e) {
                    sendErrorIntent(context, Constants.SERVER_UNAVAILABLE_EC);
                    throw new NetworkErrorException(e);
                } catch (final MalformedURLException e) {
                    sendErrorIntent(context, Constants.MALFORMED_URL_EC);
                    throw new NetworkErrorException(e);
                } catch (final JSONException e) {
                    sendErrorIntent(context, Constants.HTTP_RESPONSE_UNREADABLE_EC);
                    throw new NetworkErrorException(e);
                } catch (final SynchronisationException | RequestParsingException e) {
                    sendErrorIntent(context, Constants.SYNCHRONIZATION_ERROR_EC);
                    throw new NetworkErrorException(e);
                } catch (final DataTransmissionException e) {
                    sendErrorIntent(context, Constants.DATA_TRANSMISSION_ERROR_EC, e.getHttpStatusCode());
                    throw new NetworkErrorException(e);
                } catch (final ResponseParsingException e) {
                    sendErrorIntent(context, Constants.HTTP_RESPONSE_UNREADABLE_EC);
                    throw new NetworkErrorException(e);
                } catch (final UnauthorizedException e) {
                    sendErrorIntent(context, Constants.UNAUTHORIZED_EC);
                    throw new NetworkErrorException(e);
                }
            }
        }

        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            return result;
        }

        if (LOGIN_ACTIVITY == null) {
            Log.w(TAG, "Please set LOGIN_ACTIVITY.");
            return null;
        }

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        // an intent to display our AuthenticatorActivity.
        final Intent intent = new Intent(context, LOGIN_ACTIVITY);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, authTokenType);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    /**
     * Informs listeners, e.g. a SDK implementing app, about errors.
     *
     * @param context the {@link Context}
     * @param errorCode the Cyface error code
     * @param httpCode the HTTP error returned by the server
     */
    public static void sendErrorIntent(final Context context, final int errorCode, final int httpCode) {
        final Intent intent = new Intent(ERROR_INTENT);
        intent.putExtra(HTTP_CODE_EXTRA, httpCode);
        intent.putExtra(ERROR_CODE_EXTRA, errorCode);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Informs listeners, e.g. a SDK implementing app, about errors.
     *
     * @param context the {@link Context}
     * @param errorCode the Cyface error code
     */
    public static void sendErrorIntent(final Context context, final int errorCode) {
        final Intent intent = new Intent(ERROR_INTENT);
        intent.putExtra(ERROR_CODE_EXTRA, errorCode);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
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
     * Initializes the synchronisation by logging in to the server and creating this device if
     * necessary.
     *
     * @param username The username that is used by the application to login to the
     *            server.
     * @param password The password belonging to the account with the {@code username}
     *            logging in to the Cyface server.
     * @return The currently valid auth token to be used by further requests from this application.
     * @throws JSONException Thrown if the returned JSON message is not parsable.
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     * @throws MalformedURLException When the URL is in a wrong format.
     * @throws RequestParsingException When the request could not be generated.
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     */
    private String initSync(final @NonNull String username, final @NonNull String password)
            throws JSONException, ServerUnavailableException, MalformedURLException, RequestParsingException,
            DataTransmissionException, ResponseParsingException, SynchronisationException, UnauthorizedException {
        Log.v(TAG, "Init Sync!");
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String installationIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
        if (installationIdentifier == null) {
            throw new IllegalStateException("No installation identifier for this application set in its preferences.");
        }
        final String url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        if (url == null) {
            throw new IllegalStateException(
                    "Server url not available. Please set the applications server url preference.");
        }

        // Login to get JWT token
        Log.d(TAG, "Authenticating at " + url + " as " + username);
        final JSONObject loginPayload = new JSONObject();
        loginPayload.put("login", username);
        loginPayload.put("password", password);
        final HttpURLConnection connection = http
                .openHttpConnection(new URL(http.returnUrlWithTrailingSlash(url) + "login"));
        final HttpResponse loginResponse = http.post(connection, loginPayload, false);
        connection.disconnect();
        if (loginResponse.is2xxSuccessful() && connection.getHeaderField("Authorization") == null) {
            throw new IllegalStateException("Login successful but response does not contain a token");
        }

        final String jwtBearer = connection.getHeaderField("Authorization");
        registerDevice(url, installationIdentifier, jwtBearer);
        return jwtBearer;
    }

    /**
     * Registers device to the Cyface Server as only registered devices can push data.
     *
     * @param url The URL or the Server API
     * @param installationIdentifier The device id
     * @param authToken The authentication token to prove that the user is who is says he is
     * @throws MalformedURLException If the used server URL is not well formed
     * @throws ServerUnavailableException When there seems to be no server at the URL
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws RequestParsingException When the request could not be generated.
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     */
    private void registerDevice(final @NonNull String url, final @NonNull String installationIdentifier,
            final @NonNull String authToken)
            throws MalformedURLException, ServerUnavailableException, SynchronisationException,
            ResponseParsingException, RequestParsingException, DataTransmissionException, UnauthorizedException {

        final HttpURLConnection con = http
                .openHttpConnection(new URL(http.returnUrlWithTrailingSlash(url) + "devices/"), authToken);
        final JSONObject device = new JSONObject();
        try {
            device.put("id", installationIdentifier);
            device.put("name", Build.DEVICE);
        } catch (final JSONException e) {
            throw new IllegalStateException(e);
        }
        final HttpResponse registerDeviceResponse = http.post(con, device, false);
        con.disconnect();

        try {
            if (registerDeviceResponse.is2xxSuccessful() && !registerDeviceResponse.getBody().isNull("errorName")
                    && registerDeviceResponse.getBody().get("errorName").equals("Duplicate Device")) {
                Log.w(TAG,
                        String.format(context.getString(R.string.error_message_device_exists), installationIdentifier));
            }
        } catch (final JSONException e) {
            throw new ResponseParsingException("Unable to read error from response", e);
        }
    }
}
