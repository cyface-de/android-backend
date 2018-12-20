package de.cyface.synchronization;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

import static de.cyface.synchronization.SharedConstants.TAG;

/**
 * Listener for interested parties to subscribe to synchronization status updates.
 * Synchronization errors are broadcasted via the {@link de.cyface.utils.ErrorHandler}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.1.1
 * @since 1.0.0
 */
public final class CyfaceConnectionStatusListener implements ConnectionStatusListener {

    public final static String SYNC_STARTED = TAG + ".started";
    public final static String SYNC_FINISHED = TAG + ".finished";
    public final static String SYNC_PROGRESS = TAG + ".progress";
    public final static String SYNC_PERCENTAGE = TAG + ".percentage";
    public final static String SYNC_MEASUREMENT_ID = TAG + ".measurement_id";
    private final Context context;

    public CyfaceConnectionStatusListener(final @NonNull Context context) {
        this.context = context;
    }

    @Override
    public void onSyncStarted() {
        final Intent intent = new Intent(SYNC_STARTED);
        context.sendBroadcast(intent);
    }

    @Override
    public void onProgress(final float percent, final long measurementId) {
        final Intent intent = new Intent(SYNC_PROGRESS);
        intent.putExtra(SYNC_PERCENTAGE, percent);
        intent.putExtra(SYNC_MEASUREMENT_ID, measurementId);
        context.sendBroadcast(intent);
    }

    @Override
    public void onSyncFinished() {
        final Intent intent = new Intent(SYNC_FINISHED);
        context.sendBroadcast(intent);
    }
}
