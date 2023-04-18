package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.TAG;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.annotation.NonNull;

import de.cyface.uploader.DefaultAuthenticator;

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
     * The <code>Authenticator</code> called from this service.
     */
    private CyfaceAuthenticator authenticator;

    @Override
    public void onCreate() {
        Log.d(TAG, "authenticator service on create!");

        // Load authUrl
        final var preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final var url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        if (url == null) {
            throw new IllegalStateException(
                    "Server url not available. Please set the applications server url preference.");
        }
        authenticator = new CyfaceAuthenticator(this, new DefaultAuthenticator(url));
    }

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG, "authenticator service on bind");
        return authenticator.getIBinder();
    }
}
