package de.cyface.synchronization;

import java.io.InputStream;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * This is a no-op implementation of a <code>SyncPerformer</code>. On call it simply ignores all arguments and simulates
 * a successful data transmission without actually calling any network.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class SyncPerformer {

    /**
     * The tag used to identify Logcat messages from objects of this class.
     */
    private static final String TAG = "de.cyface.sync";

    /**
     * Creates a new completely initialized <code>SyncPerformer</code> with the current Android context.
     *
     * @param context The current context of this object. Usually an instance of <code>SyncService</code>.
     */
    public SyncPerformer(final @NonNull Context context) {
        // Nothing to do here.
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
    public int sendData(final @NonNull String endPointUrl, final long measurementIdentifier,
            final @NonNull String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener uploadProgressListener, final @NonNull String jwtAuthToken) {
        Log.i(TAG, "Synchronizing data");
        return 201;
    }
}
