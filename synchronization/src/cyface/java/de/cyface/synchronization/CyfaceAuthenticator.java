package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.ACCOUNT_TYPE;
import static de.cyface.synchronization.Constants.ARG_IS_ADDING_NEW_ACCOUNT;
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

import java.io.IOException;
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
import android.text.TextUtils;
import android.util.Log;

/**
 * The CyfaceAuthenticator is called by the {@link AccountManager} to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";
    private final Http http;
    public static Class<? extends AccountAuthenticatorActivity> LOGIN_ACTIVITY;
    //public final static int ACCOUNT_NOT_ADDED_ERROR_CODE = 1;

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

        Log.d(TAG, "Adding Account");
        final Intent intent = new Intent(context, LOGIN_ACTIVITY);
        intent.putExtra(ACCOUNT_TYPE, accountType);
        intent.putExtra(AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(ARG_IS_ADDING_NEW_ACCOUNT, true);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;

        // FIXME: Is the following code still necessary?
        /*
         * Account account = null;
         * final AccountManager manager = AccountManager.get(context);
         * final Account[] accounts = manager.getAccountsByType(accountType);
         * for (Account existingAccount : accounts) {
         * if (existingAccount.name.equals(Constants.DEFAULT_FREE_USERNAME)) {
         * account = existingAccount;
         * }
         * }
         * boolean accountExists = account == null;
         * if (!accountExists) {
         * account = new Account(Constants.DEFAULT_FREE_USERNAME, accountType);
         * accountExists = manager.addAccountExplicitly(account, Constants.DEFAULT_FREE_PASSWORD, null);
         * }
         * Bundle bundle = new Bundle();
         * if (accountExists) {
         * bundle.putCharSequence(AccountManager.KEY_ACCOUNT_NAME, Constants.DEFAULT_FREE_USERNAME);
         * bundle.putCharSequence(AccountManager.KEY_ACCOUNT_TYPE, accountType);
         * response.onResult(bundle);
         * } else {
         * response.onError(ACCOUNT_NOT_ADDED_ERROR_CODE, "Unable to add or find default account.");
         * }
         * return null;
         */
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
        return null;
    }

    @Override
    public Bundle getAuthToken(final @NonNull AccountAuthenticatorResponse response, final @NonNull Account account,
            final @NonNull String authTokenType, final Bundle options) throws NetworkErrorException {

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        Log.d(TAG, "Getting Auth Token.");
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
                } catch (SynchronisationException e) {
                    throw new NetworkErrorException(e);
                } catch (JSONException e) {
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
        intent.putExtra(Constants.ACCOUNT_TYPE, account.type);
        intent.putExtra(Constants.AUTH_TOKEN_TYPE, authTokenType);
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
     * Initializes the synchronisation by logging in to the server and creating this device if
     * necessary.
     *
     * @param username The username that is used by the application to login to the
     *            server.
     * @param password The password belonging to the account with the {@code username}
     *            logging in to the Cyface server.
     * @return The currently valid auth token to be used by further requests from this application.
     * @throws SynchronisationException If there are any communication failures.
     * @throws JSONException Thrown if the returned JSON message is not parsable.
     */
    private String initSync(final @NonNull String username, final @NonNull String password)
            throws SynchronisationException, JSONException {
        Log.v(TAG, "Init Sync!");
        try {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            final String installationIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
            if (installationIdentifier == null) {
                throw new SynchronisationException(
                        "Sync canceled: No installation identifier for this application set in its preferences.");
            }
            final String url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
            if (url == null) {
                throw new SynchronisationException(
                        "Sync canceled: Server url not available. Please set the applications server url preference.");
            }
            // Don't write password into log!
            Log.d(TAG, "Authenticating at " + url + " as " + username);

            // Login to get JWT token
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
        } catch (IOException e) {
            throw new SynchronisationException(e);
        } catch (DataTransmissionException e) {
            throw new SynchronisationException(e);
        }
    }

    /**
     * Only registered devices can push data to the Cyface Server. This method
     *
     * @param url The URL or the Server API
     * @param installationIdentifier The device id
     * @param authToken The authentication token to prove that the user is who is says he is
     * @throws SynchronisationException
     * @throws DataTransmissionException
     * @throws MalformedURLException If the used server URL is not well formed
     * @throws JSONException If the server response is not well formed
     */
    private void registerDevice(final @NonNull String url, final @NonNull String installationIdentifier,
            final @NonNull String authToken)
            throws SynchronisationException, DataTransmissionException, MalformedURLException, JSONException {

        final HttpURLConnection con = http
                .openHttpConnection(new URL(http.returnUrlWithTrailingSlash(url) + "devices/"), authToken);
        final JSONObject device = new JSONObject();
        device.put("id", installationIdentifier);
        device.put("name", Build.DEVICE);
        final HttpResponse registerDeviceResponse = http.post(con, device, false);
        con.disconnect();

        if (registerDeviceResponse.is2xxSuccessful() && !registerDeviceResponse.getBody().isNull("errorName")
                && registerDeviceResponse.getBody().get("errorName").equals("Duplicate Device")) {
            Log.w(TAG, String.format(context.getString(R.string.error_message_device_exists), installationIdentifier));
        }
    }
}
