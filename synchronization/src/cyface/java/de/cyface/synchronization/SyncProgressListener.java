package de.cyface.synchronization;

import org.json.JSONObject;

import android.support.annotation.NonNull;

/**
 * An interface for listeners who are interested in the sync progress.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public interface SyncProgressListener {
    /**
     * Is called when the synchronization starts.
     *
     * @param countOfPointsToTransmit The number of point to be transmitted.
     */
    void onSyncStarted(final long countOfPointsToTransmit);

    /**
     * Is called upon transmission errors during synchronization.
     *
     * @param errorMessage The error message.
     * @param errorType The cause of the error.
     */
    void onSyncTransmitError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    /**
     * Is called
     * @param errorMessage
     * @param errorType
     */
    void onSyncReadError(final @NonNull String errorMessage, final @NonNull Throwable errorType);

    /**
     * This event is called when the sync progress changed.
     *
     * @param measurementSlice The measurement slice which was transmitted since the last progress update.
     * @throws RequestParsingException when data could not be parsed from the measurement slice.
     */
    void onProgress(JSONObject measurementSlice) throws RequestParsingException;

    void onSyncFinished();
}
