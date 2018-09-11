package de.cyface.synchronization;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * Listener for interested parties to subscribe to synchronization status updates.
 * TODO: make the sync progress listeners listen to the error handler for errors
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 1.0.0
 */
public final class CyfaceConnectionListener implements ConnectionListener {

    public final static String SYNC_FINISHED = "de.cynav.cyface.sync_finished";
    public final static String SYNC_PROGRESS = "de.cynav.cyface.sync_progress";
    public final static String SYNC_POINTS_TRANSMITTED = "de.cynav.cyface.sync.points_transmitted";
    public final static String SYNC_POINTS_TO_TRANSMIT = "de.cynav.cyface.sync.points_to_transmit";
    public final static String SYNC_MEASUREMENT_ID = "de.cynav.cyface.sync.measurement_id";
    public final static String SYNC_STARTED = "de.cynav.cyface.sync_started";
    private final Context context;

    public CyfaceConnectionListener(final @NonNull Context context) {
        this.context = context;
    }

    @Override
    public void onSyncStarted(final long pointsToTransmitted) {
        final Intent intent = new Intent(SYNC_STARTED);
        intent.putExtra(SYNC_POINTS_TO_TRANSMIT, pointsToTransmitted);
        context.sendBroadcast(intent);
    }

    @Override
    public void onProgress(final long transmittedPoints, final long pointsToTransmit, final long measurementId) {
        final Intent intent = new Intent(SYNC_PROGRESS);
        intent.putExtra(SYNC_POINTS_TRANSMITTED, transmittedPoints);
        intent.putExtra(SYNC_POINTS_TO_TRANSMIT, pointsToTransmit);
        intent.putExtra(SYNC_MEASUREMENT_ID, measurementId);
        context.sendBroadcast(intent);
    }

    @Override
    public void onSyncFinished() {
        final Intent intent = new Intent(SYNC_FINISHED);
        context.sendBroadcast(intent);
    }
}
