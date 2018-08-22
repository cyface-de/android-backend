package de.cyface.synchronization;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 1.0.0
 */
public final class CyfaceSyncProgressListener implements SyncProgressListener {

    public final static String SYNC_FINISHED = "de.cynav.cyface.sync_finished";
    public final static String SYNC_PROGRESS = "de.cynav.cyface.sync_progress";
    public final static String SYNC_PROGRESS_TRANSMITTED = "de.cynav.cyface.sync.progress_transmitted";
    public final static String SYNC_PROGRESS_TOTAL = "de.cynav.cyface.sync.progress_total";
    public final static String SYNC_STARTED = "de.cynav.cyface.sync_started";
    public final static String SYNC_ERROR_MESSAGE = "de.cynav.cyface.sync.error_message";
    public final static String SYNC_EXCEPTION_TYPE = "de.cynav.cyface.sync.exception_type";
    public final static String SYNC_READ_ERROR = "de.cynav.cyface.sync.read_error";
    public final static String SYNC_TRANSMIT_ERROR = "de.cynav.cyface.sync.transmit_error";

    private final Context context;
    private long countOfTransmittedPoints = 0L;
    private long countOfPointsToTransmit = 0L;

    public CyfaceSyncProgressListener(final @NonNull Context context) {
        this.context = context;
    }

    @Override
    public void onSyncStarted(final long countOfPointsToTransmit) {
        Intent syncStartedIntent = new Intent(SYNC_STARTED);
        countOfTransmittedPoints = 0L;
        this.countOfPointsToTransmit = countOfPointsToTransmit;
        context.sendBroadcast(syncStartedIntent);
    }

    @Override
    public void onSyncTransmitError(final @NonNull String errorMessage, final @NonNull Throwable errorType) {
        onError(SYNC_TRANSMIT_ERROR, errorMessage, errorType);
    }

    @Override
    public void onSyncReadError(final @NonNull String errorMessage, final @NonNull Throwable errorType) {
        onError(SYNC_READ_ERROR, errorMessage, errorType);
    }

    private void onError(final @NonNull String errorKind, final @NonNull String errorMessage,
            final @NonNull Throwable errorType) {
        Intent syncFinishedIntent = new Intent(errorKind);
        syncFinishedIntent.putExtra(SYNC_ERROR_MESSAGE, errorMessage);
        syncFinishedIntent.putExtra(SYNC_EXCEPTION_TYPE, errorType.getClass().getSimpleName());
        context.sendBroadcast(syncFinishedIntent);
    }

    @Override
    public void onProgress(JSONObject measurementSlice) throws RequestParsingException {
        try {
            countOfTransmittedPoints += measurementSlice.getJSONArray("gpsPoints").length()
                    + measurementSlice.getJSONArray("magneticValuePoints").length()
                    + measurementSlice.getJSONArray("rotationPoints").length()
                    + measurementSlice.getJSONArray("accelerationPoints").length();
        } catch (final JSONException e) {
            throw new RequestParsingException("Unable to parse measurement data", e);
        }
        Intent syncInProgressIntent = new Intent(SYNC_PROGRESS);
        syncInProgressIntent.putExtra(SYNC_PROGRESS_TRANSMITTED, countOfTransmittedPoints);
        syncInProgressIntent.putExtra(SYNC_PROGRESS_TOTAL, countOfPointsToTransmit);
        context.sendBroadcast(syncInProgressIntent);
    }

    @Override
    public void onSyncFinished() {
        Intent syncInProgressIntent = new Intent(SYNC_FINISHED);
        context.sendBroadcast(syncInProgressIntent);
    }
}
