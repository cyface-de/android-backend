package de.cyface.synchronization;

/**
 * Listener interface for interested parties to subscribe to synchronization status updates.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.4
 * @since 1.0.0
 */
public interface ConnectionStatusListener {
    /**
     * Is called when the synchronization starts.
     */
    void onSyncStarted();

    /**
     * This event is called when the sync progress changed.
     *
     * @param percent How much of the currently uploading measurement is transmitted
     * @param measurementId The measurement id of the measurement which is currently transmitted.
     */
    void onProgress(final float percent, final long measurementId);

    /**
     * Is called when the synchronization ended.
     */
    void onSyncFinished();
}
