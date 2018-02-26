package de.cyface.synchronization;

import java.io.InputStream;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Created by muthmann on 26.02.18.
 */
public class SyncPerformer {

    private static final String TAG = "de.cyface.sync";

    public SyncPerformer(final @NonNull Context context) {

    }

    public int sendData(final @NonNull String endPointUrl, final long measurementIdentifier,
            final @NonNull String deviceIdentifier, final @NonNull InputStream data,
            final @NonNull UploadProgressListener uploadProgressListener) {
        Log.i(TAG, "Synchronizing data");
        return 201;
    }
}
