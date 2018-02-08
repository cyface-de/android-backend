package de.cyface.synchronization;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Created by muthmann on 07.02.18.
 */
public final class AuthenticatorService extends Service {
    private final static String TAG = "de.cyface.sync";
    private StubAuthenticator authenticator;

    @Override
    public void onCreate() {
        Log.d(TAG,"authenticator service on create!");
        authenticator = new StubAuthenticator(this);
    }

    @Override
    public IBinder onBind(final @NonNull Intent intent) {
        Log.d(TAG,"authenticator service on bind");
        return authenticator.getIBinder();
    }
}
