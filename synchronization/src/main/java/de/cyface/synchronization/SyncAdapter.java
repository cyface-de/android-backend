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
import java.util.concurrent.TimeUnit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.MeasurementSerializer;
import de.cyface.synchronization.exceptions.BadRequestException;
import de.cyface.synchronization.exceptions.DatabaseException;
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
        final MeasurementSerializer serializer = new MeasurementSerializer();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Cursor syncableMeasurementsCursor = null;
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
            syncableMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider, authority);

            final long unsyncedDataPoints = countUnsyncedDataPoints(provider, syncableMeasurementsCursor, authority);
            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncStarted();
            }
            if (unsyncedDataPoints == 0) {
                return; // nothing to sync
            }

            // The cursor is reset to initial position (i.e. 0) by countUnsyncedDataPoints
            while (syncableMeasurementsCursor.moveToNext()) {

                // Load serialized measurement
                final long measurementId = syncableMeasurementsCursor
                        .getLong(syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID));
                final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(measurementId,
                        provider, authority);
                Log.d(TAG, String.format("Measurement with identifier %d is about to be serialized.", measurementId));
                // FIXME: final InputStream data = serializer.serializeCompressed(loader);

                // Synchronize measurement
                final boolean transmissionSuccessful = syncPerformer.sendData(http, syncResult, endPointUrl,
                        measurementId, deviceId, null, new UploadProgressListener() {
                            @Override
                            public void updatedProgress(float percent) {
                                for (final ConnectionStatusListener listener : progressListener) {
                                    listener.onProgress(percent, measurementId);
                                }
                            }
                        }, jwtAuthToken);
                if (transmissionSuccessful) {
                    try {
                        // TODO: This way of deleting points is probably rather slow when lots of data is
                        // stored on old devices (from experience). We had a faster but uglier workaround
                        // but won't reimplement this here in the SDK. Instead we'll use the Cyface Byte Format
                        // from the Movebis flavor and the file uploader which we'll implement before releasing this.
                        // TODO: We should probably remove the unused isSynced flag from the points after
                        // we implemented the Cyface Byte Format synchronization. #CY-3592

                        // We delete the data of each point type separately to avoid #CY-3859 parcel size error.
                        /*
                         * deletePointsOfType(provider, authority, measurementSlice, geoLocationJsonMapper);
                         * deletePointsOfType(provider, authority, measurementSlice, accelerationJsonMapper);
                         * deletePointsOfType(provider, authority, measurementSlice, rotationJsonMapper);
                         * deletePointsOfType(provider, authority, measurementSlice, directionJsonMapper);
                         */

                        loader.cleanMeasurement();
                        Log.d(TAG, "Measurement marked as synced.");
                    } catch (/* final OperationApplicationException | */RemoteException e) {
                        throw new DatabaseException("Failed to apply the delete operation: " + e.getMessage(), e);
                    }
                } // FIXME: else maybe reset sync progress
            }
        } catch (final DatabaseException | RemoteException e) {
            Log.w(TAG, "DatabaseException: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode(), e.getMessage());
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
            if (syncableMeasurementsCursor != null) {
                syncableMeasurementsCursor.close();
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
            MeasurementContentProviderClient client = new MeasurementContentProviderClient(measurementIdentifier,
                    provider, authority);

            ret += client.countData(createGeoLocationsUri(authority), GpsPointsTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createAccelerationsUri(authority), AccelerationPointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createRotationsUri(authority), RotationPointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createDirectionsUri(authority), DirectionPointTable.COLUMN_MEASUREMENT_FK);
        } while (syncableMeasurements.moveToNext());
        final int offsetToInitialPosition = syncableMeasurements.getPosition() - initialPosition;
        syncableMeasurements.move(-offsetToInitialPosition);
        return ret;
    }

    private static Uri createGeoLocationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH).build();
    }

    private static Uri createAccelerationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(AccelerationPointTable.URI_PATH)
                .build();
    }

    private static Uri createRotationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH).build();
    }

    private static Uri createDirectionsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(DirectionPointTable.URI_PATH)
                .build();
    }
}
