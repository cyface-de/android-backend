package de.cyface.synchronization;

import static de.cyface.synchronization.HttpConnection.TAG;

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
 * @version 1.0.4
 * @since 2.0.0
 */
public final class CyfaceAuthenticatorService extends Service {
    /**
     * The <code>Authenticator</code> called from this service.
     */
    private CyfaceAuthenticator authenticator;

    @Override
    public void onCreate() {
        Log.d(TAG, "authenticator service on create!");
        authenticator = new CyfaceAuthenticator(this);
    }

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG, "authenticator service on bind");
        return authenticator.getIBinder();
    }
}
