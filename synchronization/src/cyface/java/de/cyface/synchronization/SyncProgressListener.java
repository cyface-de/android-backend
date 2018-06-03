package de.cyface.synchronization;

import android.content.Intent;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface SyncProgressListener {
    void onSyncStarted(final long countOfPointsToTransmit);

    void onSyncTransmitError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onSyncReadError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    void onProgress(JSONObject measurementSlice) throws JSONException;

    void onSyncFinished();
}
