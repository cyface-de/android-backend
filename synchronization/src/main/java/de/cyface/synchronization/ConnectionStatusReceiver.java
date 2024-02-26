package de.cyface.synchronization;

import static de.cyface.synchronization.BundlesExtrasCodes.SYNC_PERCENTAGE_ID;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_FINISHED;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_MEASUREMENT_ID;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_PROGRESS;
import static de.cyface.synchronization.CyfaceConnectionStatusListener.SYNC_STARTED;

import java.util.Collection;
import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import de.cyface.utils.Validate;

/**
 * A {@link BroadcastReceiver} for the {@link CyfaceConnectionStatusListener} events. We use this receiver
 * to populate received broadcasts about synchronization events to registered {@link ConnectionStatusListener}s.
 *
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 2.5.0
 */
public class ConnectionStatusReceiver extends BroadcastReceiver {

    /**
     * The interested parties for synchronization events.
     */
    private Collection<ConnectionStatusListener> connectionStatusListener;

    /**
     * Registers this {@link BroadcastReceiver} to {@link CyfaceConnectionStatusListener} events.
     * Don't forget to call the {@code ConnectionStatusReceiver#shutdown()} method at some point
     * in the future.
     *
     * @param context The {@link Context} to use to register this {@link BroadcastReceiver}.
     */
    public ConnectionStatusReceiver(final Context context) {
        this.connectionStatusListener = new HashSet<>();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SYNC_FINISHED);
        filter.addAction(SYNC_PROGRESS);
        filter.addAction(SYNC_STARTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(this, filter);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Validate.notNull(intent.getAction());
        switch (intent.getAction()) {
            case SYNC_STARTED:
                for (final ConnectionStatusListener listener : connectionStatusListener) {
                    listener.onSyncStarted();
                }
                break;
            case SYNC_FINISHED:
                for (final ConnectionStatusListener listener : connectionStatusListener) {
                    listener.onSyncFinished();
                }
                break;
            case SYNC_PROGRESS:
                final float percent = intent.getFloatExtra(SYNC_PERCENTAGE_ID, -1.0f);
                final long measurementId = intent.getLongExtra(SYNC_MEASUREMENT_ID, -1L);
                Validate.isTrue(percent > 0.0f);
                Validate.isTrue(measurementId > 0L);

                for (final ConnectionStatusListener listener : connectionStatusListener) {
                    listener.onProgress(percent, measurementId);
                }
                break;
        }
    }

    public void addListener(final ConnectionStatusListener connectionStatusListener) {
        this.connectionStatusListener.add(connectionStatusListener);
    }

    public void removeListener(final ConnectionStatusListener connectionStatusListener) {
        this.connectionStatusListener.remove(connectionStatusListener);
    }

    /**
     * Call this to unregister the {@link BroadcastReceiver} from the {@link CyfaceConnectionStatusListener} events.
     */
    public void shutdown(final Context context) {
        context.unregisterReceiver(this);
    }
}