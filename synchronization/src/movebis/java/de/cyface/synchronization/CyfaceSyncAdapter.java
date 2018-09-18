package de.cyface.synchronization;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
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
 * @version 1.0.2
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
    private static final String SYNC_PROGRESS_BROADCAST_ACTION = "de.cyface.broadcast.sync.progress";
    /**
     * Key used to identify the current progress value in the bundle associated with the upload progress broadcast
     * message.
     * 
     * @see #SYNC_PROGRESS_BROADCAST_ACTION
     */
    private static final String SYNC_PROGRESS_KEY = "de.cyface.broadcast.sync.progress.key";

    /**
     * Creates a new completely initialized <code>CyfaceSyncAdapter</code>. See the documentation of
     * <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The current context this adapter is running under.
     * @param autoInitialize For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @see AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context, boolean)
     */
    CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize) {
        super(context, autoInitialize);
    }

    /**
     * Creates a new completely initialized <code>CyfaceSyncAdapter</code>. See the documentation of
     * <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The current context this adapter is running under.
     * @param autoInitialize For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @param allowParallelSyncs For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @see AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context, boolean)
     */
    CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize, final boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    @Override
    public void onPerformSync(final @NonNull Account account, final @NonNull Bundle extras,
            final @NonNull String authority, final @NonNull ContentProviderClient provider,
            final @NonNull SyncResult syncResult) {
        Log.d(TAG, "Sync started.");

        final MeasurementSerializer serializer = new MeasurementSerializer();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        Cursor syncableMeasurementsCursor = null;
        final AccountManager accountManager = AccountManager.get(getContext());
        final AccountManagerFuture<Bundle> future = accountManager.getAuthToken(account, Constants.AUTH_TOKEN_TYPE,
                null, false, null, null);
        try {
            final SyncPerformer syncPerformer = new SyncPerformer(getContext());

            final Bundle result = future.getResult(1, TimeUnit.SECONDS);
            final String jwtAuthToken = result.getString(AccountManager.KEY_AUTHTOKEN);
            if (jwtAuthToken == null) {
                throw new IllegalStateException("No valid auth token supplied. Aborting data synchronization!");
            }

            final String endPointUrl = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
            if (endPointUrl == null) {
                throw new IllegalStateException("Unable to read synchronization endpoint from settings!");
            }

            final String deviceIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
            if (deviceIdentifier == null) {
                throw new IllegalStateException("Unable to read device identifier from settings!");
            }

            // Load all Measurements that are finished capturing
            syncableMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider, authority);

            while (syncableMeasurementsCursor.moveToNext()) {
                final long measurementIdentifier = syncableMeasurementsCursor
                        .getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID));
                final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementIdentifier,
                        provider, authority);

                Log.d(TAG, String.format("Measurement with identifier %d is about to be serialized.",
                        measurementIdentifier));
                final InputStream data = serializer.serializeCompressed(loader);
                final int responseStatus = syncPerformer.sendData(endPointUrl, measurementIdentifier, deviceIdentifier, data,
                        new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                Intent syncProgressIntent = new Intent();
                                syncProgressIntent.setAction(SYNC_PROGRESS_BROADCAST_ACTION);
                                syncProgressIntent.putExtra(SYNC_PROGRESS_KEY, percent);
                                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(syncProgressIntent);
                            }
                        }, jwtAuthToken);
                if (responseStatus == 201 || responseStatus == 409) {
                    loader.cleanMeasurement();
                }
            }
        } catch (final RemoteException | OperationCanceledException | AuthenticatorException | IOException e) {
            Log.e(TAG, "Unable to synchronize data!", e);
        } catch (final SynchronisationException e) {
            Log.e(TAG, "Unable to synchronize data because of SynchronizationException!", e);
        } catch (final IllegalStateException e) {
            Log.e(TAG, "Unexpected Exception occurred during synchronization!", e);
        } finally {
            if (syncableMeasurementsCursor != null) {
                syncableMeasurementsCursor.close();
            }
        }
    }
}
