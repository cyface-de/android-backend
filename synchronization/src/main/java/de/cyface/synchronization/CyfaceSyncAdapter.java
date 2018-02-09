package de.cyface.synchronization;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;

import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

/**
 * The <code>SyncAdapter</code> implementation used by the framework to transmit measured data to a server.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class CyfaceSyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "de.cyface.sync";

    public static final String SYNC_PROGRESS_BROADCAST_ACTION = "de.cyface.broadcast.sync.progress";
    public static final String SYNC_PROGRESS_KEY = "de.cyface.broadcast.sync.progress.key";
    public static final String SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint";
    public static final String DEVICE_IDENTIFIER_KEY = "de.cyface.identifier.device";

    private final ContentResolver resolver;

    public CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        resolver = context.getContentResolver();
    }

    public CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        resolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
            SyncResult syncResult) {
        Log.d(TAG, "syncing");
        MeasurementSerializer serializer = new MeasurementSerializer(provider);
        SyncPerformer syncer = new SyncPerformer();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        String endPointUrl = preferences.getString(SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        if (endPointUrl == null) {
            throw new IllegalStateException("Unable to read synchronization endpoint from settings!");
        }

        String deviceIdentifier = preferences.getString(DEVICE_IDENTIFIER_KEY, null);
        if (deviceIdentifier == null) {
            throw new IllegalStateException("Unable to read device identifier from settings!");
        }

        Cursor syncableMeasurementsCursor = null;
        try {
            syncableMeasurementsCursor = provider.query(MeasuringPointsContentProvider.MEASUREMENT_URI, null,
                    MeasurementTable.COLUMN_FINISHED + "=?", new String[] {Integer.valueOf(1).toString()}, null);
            while (syncableMeasurementsCursor.moveToNext()) {

                long measurementIdentifier = syncableMeasurementsCursor
                        .getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID));

                InputStream data = serializer.serialize(measurementIdentifier);
                syncer.sendData(endPointUrl, measurementIdentifier, deviceIdentifier, data,
                        new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Intent syncProgressIntent = new Intent();
                                syncProgressIntent.setAction(SYNC_PROGRESS_BROADCAST_ACTION);
                                syncProgressIntent.putExtra(SYNC_PROGRESS_KEY, percent);
                                getContext().sendBroadcast(syncProgressIntent);
                            }
                        });
            }
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        } finally {
            if (syncableMeasurementsCursor != null) {
                syncableMeasurementsCursor.close();
            }
        }

        // TODO delete synchronized measurements.
    }
}
