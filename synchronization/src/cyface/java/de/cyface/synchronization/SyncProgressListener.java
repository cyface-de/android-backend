package de.cyface.synchronization;

import org.json.JSONObject;

import android.support.annotation.NonNull;

/**
 * An interface for listeners who are interested in the sync progress.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SyncProgressListener {
    void onSyncStarted(final long countOfPointsToTransmit);

    void onSyncTransmitError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onSyncReadError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onProgress(JSONObject measurementSlice) throws RequestParsingException;

    void onSyncFinished();
}
