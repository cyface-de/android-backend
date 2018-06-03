package de.cyface.synchronization;

import java.io.InputStream;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * This is a no-op implementation of a <code>SyncPerformer</code>. On call it simply ignores all arguments and simulates
 * a successful data transmission without actually calling any network. It does nothing and just prints its status to
 * the terminal.
 * Use it in test scenarios where you have no access to a Movebis server.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
class SyncPerformer {

    /**
     * The tag used to print messages to Logcat.
     */
    private static final String TAG = "de.cyface.sync";

    /**
     * Creates a new completely initilized <code>SyncPerformer</code> with an Android context. If you implement an
     * Anroid synchronization adapter, this can be the synchronisation service.
     *
     * @param context The Android context to use.
     * @throws SynchronisationException Never, but required to be in sync with the other implementation in the other
     *             flavor.
     */
    SyncPerformer(final @NonNull Context context) throws SynchronisationException {
        // Nothing to do here. Just mocked
    }

    /**
     * A do nothing implementation of the data transmission method.
     *
     * @param endPointUrl Is ignored!
     * @param measurementIdentifier Is ignored!
     * @param deviceIdentifier Is ignored!
     * @param data Is ignored!
     * @param uploadProgressListener Is ignored!
     * @param jwtAuthToken Is ignored!
     * @return Always <code>201</code> which is the expected status code for a successful transmission.
     */
    int sendData(final @NonNull String endPointUrl, final long measurementIdentifier,
            final @NonNull String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener uploadProgressListener, final @NonNull String jwtAuthToken) {
        Log.i(TAG, "Synchronizing data");
        return 201;
    }
}
