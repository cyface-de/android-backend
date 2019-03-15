package de.cyface.synchronization;

import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.BAD_REQUEST;
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.cyface.utils.Validate;

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
 * @version 1.5.1
 * @since 2.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";
    private final Http http;
    /**
     * A reference to the implementation of the {@link AccountAuthenticatorActivity} which is called by Android and its
     * {@link AccountManager}. This happens e.g. when a token is requested while none is cached, using
     * {@link #getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)}.
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
            Validate.notNull(response);
            return getLoginActivityIntent(response, account, authTokenType);
        }

        // Login to get a new authToken
        final SSLContext sslContext;
        try {
            sslContext = loadSslContext(context);
        } catch (final IOException e) {
            throw new IllegalStateException("Trust store file failed while closing", e);
        } catch (final SynchronisationException e) {
            throw new IllegalStateException(e);
        }
        try {
            freshAuthToken = login(account.name, password, sslContext);
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
    private Bundle getLoginActivityIntent(@NonNull final AccountAuthenticatorResponse response,
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

    /**
     * Loads the SSL certificate from the trust store and returns the {@link SSLContext}. If the trust
     * store file is empty, the default context is used.
     *
     * @param context The {@link Context} to use to load the trust store file.
     * @return the {@link SSLContext} to be used for HTTPS connections.
     * @throws SynchronisationException when the SSLContext could not be loaded
     * @throws IOException if the trustStoreFile failed while closing.
     */
    static SSLContext loadSslContext(final Context context) throws SynchronisationException, IOException {
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
     * Logs into the server to get a valid authToken.
     *
     * @param username The username that is used by the application to login to the
     *            server.
     * @param password The password belonging to the account with the {@code username}
     *            logging in to the Cyface server.
     * @param sslContext To open a SSL connection
     * @return The currently valid auth token to be used by further requests from this application.
     * @throws JSONException Thrown if the returned JSON message is not parsable.
     * @throws ServerUnavailableException When there seems to be no server at the given URL.
     * @throws MalformedURLException When the URL is in a wrong format.
     * @throws RequestParsingException When the request could not be generated.
     * @throws DataTransmissionException When the server returned a non-successful status code.
     * @throws ResponseParsingException When the http response could not be parsed.
     * @throws SynchronisationException When the new data output for the http connection failed to be created.
     * @throws UnauthorizedException If the credentials for the cyface server are wrong.
     * @throws NetworkErrorException when the connection's input or error stream was null (e.g. connection disabled)
     */
    private String login(final @NonNull String username, final @NonNull String password, SSLContext sslContext)
            throws JSONException, ServerUnavailableException, MalformedURLException, RequestParsingException,
            DataTransmissionException, ResponseParsingException, SynchronisationException, UnauthorizedException,
            BadRequestException, NetworkErrorException {
        Log.v(TAG, "Logging in to get new authToken");

        // Load authUrl
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
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
            if (loginResponse.is2xxSuccessful() && connection.getHeaderField("Authorization") == null) {
                throw new IllegalStateException("Login successful but response does not contain a token");
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        Validate.notNull(connection);
        return connection.getHeaderField("Authorization");
    }
}
