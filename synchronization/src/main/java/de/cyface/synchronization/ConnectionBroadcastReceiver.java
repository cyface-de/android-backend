package de.cyface.synchronization;

import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_ERROR_MESSAGE;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_EXCEPTION_TYPE;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_PROGRESS;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_PROGRESS_TOTAL;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_PROGRESS_TRANSMITTED;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_READ_ERROR;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_STARTED;
import static de.cyface.synchronization.CyfaceSyncProgressListener.SYNC_TRANSMIT_ERROR;

import java.util.Collection;
import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import de.cyface.utils.Validate;

/**
 * {@link BroadcastReceiver} for the {@link ConnectionListener} events.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.5.0
 */
public class ConnectionBroadcastReceiver extends BroadcastReceiver {

    static final String TAG = "de.cyface.sync";
    public static final String SYNC_FINISHED = "de.cynav.cyface.sync_finished";
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
        filter.addAction(SYNC_READ_ERROR);
        filter.addAction(SYNC_TRANSMIT_ERROR);
        context.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Validate.notNull(intent.getAction());
        switch (intent.getAction()) {
            case SYNC_STARTED:
                for (final ConnectionListener listener : connectionListener) {
                    listener.onSyncStarted();
                }
                break;
            case SYNC_FINISHED:
                for (final ConnectionListener listener : connectionListener) {
                    listener.onSyncFinished();
                }
                break;
            case SYNC_PROGRESS:
                for (final ConnectionListener listener : connectionListener) {
                    if (listener != null) {
                        listener.onProgressInfo(intent.getLongExtra(SYNC_PROGRESS_TRANSMITTED, 0),
                                intent.getLongExtra(SYNC_PROGRESS_TOTAL, 1));
                    }
                }
                break;
            case SYNC_READ_ERROR:
            case SYNC_TRANSMIT_ERROR:
                for (final ConnectionListener listener : connectionListener) {
                    listener.onDisconnected(intent.getStringExtra(SYNC_EXCEPTION_TYPE),
                            intent.getStringExtra(SYNC_ERROR_MESSAGE));
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