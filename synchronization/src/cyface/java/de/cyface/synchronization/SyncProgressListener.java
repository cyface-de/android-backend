package de.cyface.synchronization;

import org.json.JSONObject;

import android.support.annotation.NonNull;

public interface SyncProgressListener {
    void onSyncStarted(final long countOfPointsToTransmit);

    void onSyncTransmitError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onSyncReadError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onProgress(JSONObject measurementSlice);

    void onSyncFinished();
}
