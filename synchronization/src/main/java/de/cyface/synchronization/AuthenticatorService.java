package de.cyface.synchronization;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * The Android service used to communicate with the {@link StubAuthenticator}. This has been implemented as described in
 * <a href=
 * "https://developer.android.com/training/sync-adapters/creating-authenticator.html#CreateAuthenticatorService">the
 * Android documentation</a>.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class AuthenticatorService extends Service {
    /**
     * Tag used to identify logging messages in Logcat.
     */
    private final static String TAG = "de.cyface.sync";
    /**
     * The <code>Authenticator</code> called from this service.
     */
    private StubAuthenticator authenticator;

    @Override
    public void onCreate() {
        Log.d(TAG, "authenticator service on create!");
        authenticator = new StubAuthenticator(this);
    }

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG, "authenticator service on bind");
        return authenticator.getIBinder();
    }
}
