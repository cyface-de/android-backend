package de.cyface.synchronization;

import static de.cyface.utils.ErrorHandler.ErrorCode.BAD_REQUEST;
import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATA_TRANSMISSION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.MALFORMED_URL;
import static de.cyface.utils.ErrorHandler.ErrorCode.SERVER_UNAVAILABLE;
import static de.cyface.utils.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNAUTHORIZED;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNREADABLE_HTTP_RESPONSE;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

/**
 * The CyfaceAuthenticator is called by the {@link AccountManager} to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Cyface server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
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
        this.http = new HttpConnection();
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
        final AccountManager accountManager = AccountManager.get(context);

        // Check if there is an existing token. As they expire after 60 seconds currently it's takes less
        // resources to invalidate old ones and request a new one instead of checking the old one via request.
        String authToken = accountManager.peekAuthToken(account, authTokenType);
        accountManager.invalidateAuthToken(account.type, authToken);
        authToken = ""; // To request a new token

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(authToken)) {
            Log.v(TAG, String.format("Auth Token was empty for account %s!", account.name));
            final String password = accountManager.getPassword(account);
            Log.v(TAG, String.format("Password: %s", password));

            if (password != null) {
                // Load SSLContext
                final SSLContext sslContext;
                try {
                    sslContext = initSslContext(context);
                } catch (final IOException e) {
                    throw new IllegalStateException("Trust store file failed while closing", e);
                } catch (final SynchronisationException e) {
                    throw new IllegalStateException(e);
                }

                // Get Auth token
                try {
                    authToken = initSync(account.name, password, sslContext);
                    Log.v(TAG, String.format("Auth token: %s", authToken));
                } catch (final ServerUnavailableException e) {
                    sendErrorIntent(context, SERVER_UNAVAILABLE.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final MalformedURLException e) {
                    sendErrorIntent(context, MALFORMED_URL.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final JSONException e) {
                    sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final SynchronisationException | RequestParsingException e) {
                    sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final DataTransmissionException e) {
                    sendErrorIntent(context, DATA_TRANSMISSION_ERROR.getCode(), e.getHttpStatusCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final ResponseParsingException e) {
                    sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final UnauthorizedException e) {
                    sendErrorIntent(context, UNAUTHORIZED.getCode(), e.getMessage());
                    throw new NetworkErrorException(e);
                } catch (final BadRequestException e) {
                    sendErrorIntent(context, BAD_REQUEST.getCode(), e.getMessage());
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
     * Loads the SSL certificate from the trust store and returns the {@link SSLContext}. If the trust
     * store file is empty, the default context is used.
     *
     * @param context The {@link Context} to use to load the trust store file.
     * @return the {@link SSLContext} to be used for HTTPS connections.
     * @throws SynchronisationException when the SSLContext could not be loaded
     * @throws IOException if the trustStoreFile failed while closing.
     */
    static SSLContext initSslContext(final Context context) throws SynchronisationException, IOException {
        final SSLContext sslContext;

        InputStream trustStoreFile = null;
        try {
            // If no self-signed certificate is used and an empty trust store is provided:
            trustStoreFile = context.getResources().openRawResource(R.raw.truststore);
            if (trustStoreFile.read() == -1) {
                Log.d(TAG, "Trust store is empty, loading default sslContext ...");
                sslContext = SSLContext.getInstance("TLSv1");
                sslContext.init(null, null, null);
                return sslContext;
            }

            // Add trust store to sslContext
            trustStoreFile.close();
            trustStoreFile = context.getResources().openRawResource(R.raw.truststore);
            final KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(trustStoreFile, "secret".toCharArray());
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(trustStore);

            // Create an SSLContext that uses our TrustManager
            sslContext = SSLContext.getInstance("TLSv1");
            final byte[] seed = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom(seed));

        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException
                | KeyManagementException e) {
            throw new SynchronisationException("Unable to load SSLContext", e);
        } finally {
            if (trustStoreFile != null) {
                trustStoreFile.close();
            }
        }
        return sslContext;
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
     * @param sslContext
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
    private String initSync(final @NonNull String username, final @NonNull String password, SSLContext sslContext)
            throws JSONException, ServerUnavailableException, MalformedURLException, RequestParsingException,
            DataTransmissionException, ResponseParsingException, SynchronisationException, UnauthorizedException,
            BadRequestException {
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
        final URL authUrl = new URL(http.returnUrlWithTrailingSlash(url) + "login");

        // Login to get JWT token
        final JSONObject loginPayload = new JSONObject();
        loginPayload.put("username", username);
        loginPayload.put("password", password);
        Log.d(TAG, "Authenticating at " + authUrl + " with " + loginPayload);
        HttpsURLConnection connection = null;
        try {
            connection = http.openHttpConnection(authUrl, sslContext, false);
            final HttpResponse loginResponse = http.post(connection, loginPayload, false);
            connection.disconnect();
            if (loginResponse.is2xxSuccessful() && connection.getHeaderField("Authorization") == null) {
                throw new IllegalStateException("Login successful but response does not contain a token");
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return connection.getHeaderField("Authorization");
    }
}
