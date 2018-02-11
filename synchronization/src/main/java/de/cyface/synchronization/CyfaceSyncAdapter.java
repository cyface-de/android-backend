package de.cyface.synchronization;

import java.io.InputStream;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

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

    /**
     * Tag used for log messages to logcat.
     */
    private static final String TAG = "de.cyface.sync";

    /**
     * Key for the system broadcast action issued to report about the current upload progress.
     */
    public static final String SYNC_PROGRESS_BROADCAST_ACTION = "de.cyface.broadcast.sync.progress";
    /**
     * Key used to identify the current progress value in the bundle associated with the upload progress broadcast
     * message.
     * 
     * @see #SYNC_PROGRESS_BROADCAST_ACTION
     */
    public static final String SYNC_PROGRESS_KEY = "de.cyface.broadcast.sync.progress.key";
    /**
     * The settings key used to identify the settings storing the URL of the server to upload data to.
     */
    public static final String SYNC_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.sync.endpoint";
    /**
     * The settings key used to identify the settings storing the device or rather installation identifier of the
     * current app. This identifier is used to anonymously group measurements from the same device together.
     */
    public static final String DEVICE_IDENTIFIER_KEY = "de.cyface.identifier.device";

    /**
     * Creates a new completely initialized <code>CyfaceSyncAdapter</code>. See the documentation of <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The current context this adapter is running under.
     * @param autoInitialize For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @see AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context, boolean)
     */
    CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    /**
     * Creates a new completely initialized <code>CyfaceSyncAdapter</code>. See the documentation of <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The current context this adapter is running under.
     * @param autoInitialize For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @param allowParallelSyncs For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @see AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context, boolean)
     */
    CyfaceSyncAdapter(final @NonNull Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
            SyncResult syncResult) {
        Log.d(TAG, "syncing");

        MeasurementSerializer serializer = new MeasurementSerializer();
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

            if(syncableMeasurementsCursor==null) {
                throw new IllegalStateException("Unable to load measurement from content provider!");
            }

            while (syncableMeasurementsCursor.moveToNext()) {

                long measurementIdentifier = syncableMeasurementsCursor
                        .getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID));
                MeasurementLoader loader = new MeasurementLoader(measurementIdentifier,provider);

                InputStream data = serializer.serialize(loader);
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
