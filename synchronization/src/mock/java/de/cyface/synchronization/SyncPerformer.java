package de.cyface.synchronization;

import java.io.InputStream;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * A sync performer that does absolutely nothing and just prints its status to the terminal.
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
     */
    SyncPerformer(final @NonNull Context context) {
        // Nothing to do here. Just mocked
    }

    /**
     * Mock implementation of the method to upload data to a Movebis-Server. This implementation just logs a message and
     * does nothing.
     * 
     * @param endPointUrl The URL of the server running the Movebis API to synchronize with.
     * @param measurementIdentifier Usually the identifier of the measurement ot synchronize. In this mock
     *            implementation it is ignored.
     * @param deviceIdentifier Usually the installation identifier of the application. In this mock implementation it is
     *            ignored.
     * @param data Usually the data to upload. Since this is a mock implemenation it will be ignored.
     * @param uploadProgressListener Since there is no progress to report, this will be ignored. You may just provide an
     *            empty implementation.
     * @return
     */
    int sendData(final @NonNull String endPointUrl, final long measurementIdentifier,
            final @NonNull String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener uploadProgressListener) {
        Log.i(TAG, "Synchronizing data");
        return 201;
    }
}
