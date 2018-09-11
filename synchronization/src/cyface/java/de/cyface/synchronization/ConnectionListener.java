package de.cyface.synchronization;

/**
 * Listener interface for interested parties to subscribe to synchronization status updates.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
public interface ConnectionListener {
    /**
     * Is called when the synchronization starts.
     *
     * @param pointsToTransmitted The number of point to be transmitted.
     */
    void onSyncStarted(final long pointsToTransmitted);

    /**
     * This event is called when the sync progress changed.
     *
     * @param transmittedPoints The number of points transmitted so far.
     * @param pointsToTransmit The number of points to transmit.
     * @param measurementId The measurement id of the measurement which is currently transmitted.
     */
    void onProgress(final long transmittedPoints, final long pointsToTransmit, final long measurementId);

    void onSyncFinished();
}
