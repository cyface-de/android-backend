package de.cyface.synchronization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
 * @version 1.0.3
 * @since 3.0.0
 */
public final class CyfaceAuthenticator extends AbstractAccountAuthenticator {

    private final Context context;
    private final static String TAG = "de.cyface.auth";

    public CyfaceAuthenticator(final @NonNull Context context) {
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
    @SuppressWarnings("RedundantThrows") // Because the cyface flavour variant throws it, too
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
}