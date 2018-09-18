package de.cyface.synchronization;

import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_FINISHED;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_MEASUREMENT_ID;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_POINTS_TO_TRANSMIT;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_POINTS_TRANSMITTED;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_PROGRESS;
import static de.cyface.synchronization.CyfaceConnectionListener.SYNC_STARTED;

import java.util.Collection;
import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import de.cyface.utils.Validate;

/**
 * {@link BroadcastReceiver} for the {@link CyfaceConnectionListener} events.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 2.5.0
 */
public class ConnectionBroadcastReceiver extends BroadcastReceiver {

    static final String TAG = "de.cyface.sync";
    /**
     * The interested parties for synchronization events.
     */
    private Collection<ConnectionListener> connectionListener;

    public ConnectionBroadcastReceiver(final Context context) {
        this.connectionListener = new HashSet<>();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SYNC_FINISHED);
        filter.addAction(SYNC_PROGRESS);
        filter.addAction(SYNC_STARTED);
        LocalBroadcastManager.getInstance(context).registerReceiver(this, filter);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Validate.notNull(intent.getAction());
        switch (intent.getAction()) {
            case SYNC_STARTED:
                long pointsToTransmit = intent.getLongExtra(SYNC_POINTS_TO_TRANSMIT, -1L);
                Validate.isTrue(pointsToTransmit >= 0L);
                for (final ConnectionListener listener : connectionListener) {
                    listener.onSyncStarted(pointsToTransmit);
                }
                break;
            case SYNC_FINISHED:
                for (final ConnectionListener listener : connectionListener) {
                    listener.onSyncFinished();
                }
                break;
            case SYNC_PROGRESS:
                pointsToTransmit = intent.getLongExtra(SYNC_POINTS_TO_TRANSMIT, -1L);
                final long transmittedPoints = intent.getLongExtra(SYNC_POINTS_TRANSMITTED, -1L);
                final long measurementId = intent.getLongExtra(SYNC_MEASUREMENT_ID, -1L);
                Validate.isTrue(pointsToTransmit > 0L);
                Validate.isTrue(transmittedPoints > 0L);
                Validate.isTrue(measurementId > 0L);

                for (final ConnectionListener listener : connectionListener) {
                    listener.onProgress(transmittedPoints, pointsToTransmit, measurementId);
                }
                break;
        }
    }

    public void addListener(final ConnectionListener connectionListener) {
        this.connectionListener.add(connectionListener);
    }

    public void removeListener(final ConnectionListener connectionListener) {
        this.connectionListener.remove(connectionListener);
    }
}