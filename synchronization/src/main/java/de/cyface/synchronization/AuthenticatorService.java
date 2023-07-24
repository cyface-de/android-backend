package de.cyface.synchronization;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * The Android service used to communicate with the Stub Authenticator. This has been implemented as described in
 * <a href=
 * "https://developer.android.com/training/sync-adapters/creating-authenticator.html#CreateAuthenticatorService">the
 * Android documentation</a>.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 2.0.0
 */
public final class AuthenticatorService extends Service {

    /**
     * Logging TAG to identify logs associated with the {@link WiFiSurveyor}.
     */
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // we add and move logs often, so keep it
    public static final String TAG = Constants.TAG + ".authSvc";
    /**
     * The <code>Authenticator</code> called from this service.
     */
    private CyfaceAuthenticator authenticator;

    @Override
    public void onCreate() {
        Log.d(TAG, "authenticator service on create!");

        // Load authUrl
        final var preferences = new CustomPreferences(this);
        if (preferences.getOAuthUrl() == null) {
            throw new IllegalStateException(
                    "Server url not available. Please set the applications server url preference.");
        }
        authenticator = new CyfaceAuthenticator(this);
    }

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG, "authenticator service on bind");
        return authenticator.getIBinder();
    }
}
