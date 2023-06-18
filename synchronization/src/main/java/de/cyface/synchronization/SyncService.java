package de.cyface.synchronization;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import de.cyface.uploader.DefaultAuthenticator;
import de.cyface.uploader.DefaultUploader;
import de.cyface.utils.Validate;

/**
 * The synchronisation <code>Service</code> used to bind the synchronisation adapter to the Android framework.
 * <p>
 * Further details are described in the <a href=
 * "https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService">Android
 * documentation</a>.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.8
 * @since 2.0.0
 */
public final class SyncService extends Service {

    /**
     * Logging TAG to identify logs associated with the {@link WiFiSurveyor}.
     */
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // we add and move logs often, so keep it
    public static final String TAG = Constants.TAG + ".syncSvc";
    /**
     * The settings key used to identify the settings storing the URL of the server to upload data to.
     */
    public static final String SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint";
    /**
     * The settings key used to identify the settings storing the OAuth configuration JsonObject as String.
     */
    public static final String OAUTH_CONFIG_SETTINGS_KEY = "de.cyface.oauth.config";
    /**
     * The synchronisation adapter this service is supposed to call.
     * <p>
     * Singleton isn't what they call a beauty. Nevertheless this is how it is specified in the documentation. Maybe try
     * to change this after it runs.
     */
    private static SyncAdapter syncAdapter = null;
    /**
     * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
     */
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        synchronized (LOCK) {
            if (syncAdapter == null) {
                final var oauth = new OAuth2(getApplicationContext()); // move to DefaultAuthenticator
                final var authApi = authApi(getApplicationContext());
                final var collectorApi = collectorApi(getApplicationContext());
                syncAdapter = new SyncAdapter(getApplicationContext(), true, new DefaultAuthenticator(authApi),
                        new DefaultUploader(collectorApi), oauth);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return syncAdapter.getSyncAdapterBinder();
    }

    /**
     * Reads the Collector API URL from the preferences.
     *
     * @param context The `Context` required to read the preferences
     * @return The URL as string
     */
    private String collectorApi(@NonNull final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final var apiEndpoint = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        Validate.notNull(
                apiEndpoint,
                "Sync canceled: Server url not available. Please set the applications server url preference.");
        return apiEndpoint;
    }

    /**
     * Reads the Auth URL from the preferences.
     *
     * @param context The `Context` required to read the preferences
     * @return The URL as string
     */
    private String authApi(@NonNull final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final var apiEndpoint = preferences.getString(OAUTH_CONFIG_SETTINGS_KEY, null);
        Validate.notNull(
                apiEndpoint,
                "Sync canceled: Auth url not available. Please set the applications server url preference.");
        return apiEndpoint;
    }
}
