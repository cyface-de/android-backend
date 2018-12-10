package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.DEVICE_IDENTIFIER_KEY;
import static de.cyface.synchronization.SharedConstants.TAG;
import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.AUTHENTICATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.BAD_REQUEST;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATABASE_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.MeasurementPersistence;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.serialization.FileCorruptedException;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.synchronization.exceptions.BadRequestException;
import de.cyface.synchronization.exceptions.RequestParsingException;
import de.cyface.utils.Validate;

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
public final class SyncAdapter extends AbstractThreadedSyncAdapter {

    private final Collection<ConnectionStatusListener> progressListener;
    private final Http http;

    /**
     * Creates a new completely initialized {@code SyncAdapter}. See the documentation of
     * <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean)}.
     */
    SyncAdapter(final @NonNull Context context, final boolean autoInitialize, final @NonNull Http http) {
        this(context, autoInitialize, false, http);
    }

    /**
     * Creates a new completely initialized {@code SyncAdapter}. See the documentation of
     * <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The current context this adapter is running under.
     * @param autoInitialize For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @param allowParallelSyncs For details have a look at <code>AbstractThreadedSyncAdapter</code>.
     * @see AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context, boolean)
     */
    private SyncAdapter(final @NonNull Context context, final boolean autoInitialize, final boolean allowParallelSyncs,
            final @NonNull Http http) {
        super(context, autoInitialize, allowParallelSyncs);

        this.http = http;
        progressListener = new HashSet<>();
        addConnectionListener(new CyfaceConnectionStatusListener(context));
    }

    @Override
    public void onPerformSync(final @NonNull Account account, final @NonNull Bundle extras,
            final @NonNull String authority, final @NonNull ContentProviderClient provider,
            final @NonNull SyncResult syncResult) {
        Log.d(TAG, "Sync started.");

        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        final MeasurementPersistence persistence = new MeasurementPersistence(context, resolver, authority);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final AccountManager accountManager = AccountManager.get(getContext());
        final AccountManagerFuture<Bundle> future = accountManager.getAuthToken(account,
                SharedConstants.AUTH_TOKEN_TYPE, null, false, null, null);

        try {
            final SyncPerformer syncPerformer = new SyncPerformer(context);

            // Load header info
            final Bundle result = future.getResult(1, TimeUnit.SECONDS);
            final String jwtAuthToken = result.getString(AccountManager.KEY_AUTHTOKEN);
            if (jwtAuthToken == null) {
                throw new IllegalStateException("No valid auth token supplied. Aborting data synchronization!");
            }

            final String endPointUrl = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
            Validate.notNull(endPointUrl,
                    "Sync canceled: Server url not available. Please set the applications server url preference.");

            final String deviceId = preferences.getString(DEVICE_IDENTIFIER_KEY, null);
            Validate.notNull(deviceId,
                    "Sync canceled: No installation identifier for this application set in its preferences.");

            // Load all Measurements that are finished capturing
            final List<Measurement> syncableMeasurements = persistence.loadFinishedMeasurements();

            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncStarted();
            }
            if (syncableMeasurements.size() == 0) {
                return; // nothing to sync
            }

            for (Measurement measurement : syncableMeasurements) {

                // Load serialized measurement
                final long measurementId = measurement.getIdentifier();

                Log.d(TAG, String.format("Measurement with identifier %d is about to be loaded for transmission.",
                        measurementId));
                final InputStream data;
                try {
                    data = persistence.loadSerializedCompressed(measurementId);
                } catch (FileCorruptedException e) {
                    throw new IllegalStateException(e);
                }

                // Synchronize measurement
                final boolean transmissionSuccessful = syncPerformer.sendData(http, syncResult, endPointUrl,
                        measurementId, deviceId, data, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                for (final ConnectionStatusListener listener : progressListener) {
                                    listener.onProgress(percent, measurementId);
                                }
                            }
                        }, jwtAuthToken);
                if (transmissionSuccessful) {
                    persistence.markAsSynchronized(measurement);
                    Log.d(TAG, "Measurement marked as synced.");
                } // FIXME: else maybe reset sync progress
            }
        } catch (final RequestParsingException/* | SynchronisationException */ e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
        } catch (final BadRequestException e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numConflictDetectedExceptions++;
            sendErrorIntent(context, BAD_REQUEST.getCode(), e.getMessage());
        } catch (final AuthenticatorException | IOException | OperationCanceledException e) {
            // OperationCanceledException is thrown with error message = null which leads to an NPE
            final String errorMessage = e.getMessage() != null ? e.getMessage()
                    : "onPerformSync threw " + e.getClass().getSimpleName();
            Log.w(TAG, e.getClass().getSimpleName() + ": " + errorMessage);
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_ERROR.getCode(), errorMessage);
        } finally {
            Log.d(TAG, String.format("Sync finished. (error: %b)", syncResult.hasError()));
            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncFinished();
            }
        }
    }

    private void addConnectionListener(final @NonNull ConnectionStatusListener listener) {
        progressListener.add(listener);
    }

    private long countUnsyncedDataPoints(final @NonNull ContentProviderClient provider,
            final @NonNull Cursor syncableMeasurements, final @NonNull String authority) throws RemoteException {
        long ret = 0L;
        int initialPosition = syncableMeasurements.getPosition();
        if (!syncableMeasurements.moveToFirst()) {
            Log.d(TAG, "No syncable measurements exist.");
            return 0L;
        }
        do {
            long measurementIdentifier = syncableMeasurements
                    .getLong(syncableMeasurements.getColumnIndex(BaseColumns._ID));
            /*
             * MeasurementContentProviderClient client = new MeasurementContentProviderClient(measurementIdentifier,
             * provider, authority); FIXME
             */

            /*
             * ret += client.countData(createGeoLocationsUri(authority), GpsPointsTable.COLUMN_MEASUREMENT_FK);
             * ret += client.countData(createAccelerationsUri(authority), AccelerationPointTable.COLUMN_MEASUREMENT_FK);
             * ret += client.countData(createRotationsUri(authority), RotationPointTable.COLUMN_MEASUREMENT_FK);
             * ret += client.countData(createDirectionsUri(authority), DirectionPointTable.COLUMN_MEASUREMENT_FK);
             */ // FIXME
        } while (syncableMeasurements.moveToNext());
        final int offsetToInitialPosition = syncableMeasurements.getPosition() - initialPosition;
        syncableMeasurements.move(-offsetToInitialPosition);
        return ret;
    }
}
