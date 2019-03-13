package de.cyface.synchronization;

import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;
import static de.cyface.synchronization.Constants.TAG;
import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.AUTHENTICATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.BAD_REQUEST;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATABASE_ERROR;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.4.3
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
        // The network setting may have changed since the initial sync call, avoid unnecessary serialization
        if (isAutoSyncDisallowed(account, authority)) {
            return;
        }

        Log.d(TAG, "Sync started");

        final Context context = getContext();
        final MeasurementSerializer serializer = new MeasurementSerializer(new DefaultFileAccess());
        final PersistenceLayer<DefaultPersistenceBehaviour> persistence = new PersistenceLayer<>(context,
                context.getContentResolver(), authority, new DefaultPersistenceBehaviour());

        try {
            final SyncPerformer syncPerformer = new SyncPerformer(context);

            // Load api url
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            final String endPointUrl = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
            Validate.notNull(endPointUrl,
                    "Sync canceled: Server url not available. Please set the applications server url preference.");

            // Setup required device identifier, if not already existent
            final String deviceId = persistence.restoreOrCreateDeviceId();
            Validate.notNull(deviceId);

            // Ensure user is authorized before starting synchronization
            final CyfaceAuthenticator authenticator = new CyfaceAuthenticator(context);
            String jwtAuthToken;
            try {
                // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
                final Bundle bundle = authenticator.getAuthToken(null, account, AUTH_TOKEN_TYPE, null);
                if (bundle == null) {
                    // Because of Movebis we don't throw an IllegalStateException if there is no auth token
                    throw new AuthenticatorException("No valid auth token supplied. Aborting data synchronization!");
                }
                jwtAuthToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
            } catch (final NetworkErrorException e) {
                // This happened e.g. when Wifi was manually disabled just after synchronization started (Pixel 2 XL).
                Log.w(TAG, "getAuthToken failed, was the connection closed? Aborting sync.");
                return;
            }
            Validate.notNull(jwtAuthToken);
            Log.d(TAG, "Login authToken: **" + jwtAuthToken.substring(jwtAuthToken.length() - 7));

            // Load all Measurements that are finished capturing
            final List<Measurement> syncableMeasurements = persistence.loadMeasurements(MeasurementStatus.FINISHED);

            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncStarted();
            }
            if (syncableMeasurements.size() == 0) {
                return; // nothing to sync
            }

            for (final Measurement measurement : syncableMeasurements) {

                // Load compressed transfer file for measurement
                Log.d(Constants.TAG,
                        String.format("Measurement with identifier %d is about to be loaded for transmission.",
                                measurement.getIdentifier()));
                final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(
                        measurement.getIdentifier(), provider, authority);

                // The network setting may have changed since the initial sync call, avoid unnecessary serialization
                if (isAutoSyncDisallowed(account, authority)) {
                    return;
                }
                final File compressedTransferTempFile = serializer.writeSerializedCompressed(loader,
                        measurement.getIdentifier(), persistence);

                // Try to sync the transfer file - remove it afterwards
                try {

                    // Acquire new auth token before each synchronization (old one could be expired)
                    try {
                        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
                        final Bundle authBundle = authenticator.getAuthToken(null, account, AUTH_TOKEN_TYPE, null);
                        Validate.notNull(authBundle);
                        jwtAuthToken = authBundle.getString(AccountManager.KEY_AUTHTOKEN);
                    } catch (final NetworkErrorException e) {
                        // This happened e.g. when Wifi was manually disabled just after synchronization started (Pixel 2 XL).
                        Log.w(TAG, "getAuthToken failed, was the connection closed? Aborting sync.");
                        return;
                    }
                    Validate.notNull(jwtAuthToken);
                    Log.d(TAG, "Sync authToken: **" + jwtAuthToken.substring(jwtAuthToken.length() - 7));

                    // The network setting may have changed since the initial sync call, avoid using metered network
                    // without permission
                    if (isAutoSyncDisallowed(account, authority)) {
                        return;
                    }

                    // Synchronize measurement
                    Log.d(de.cyface.persistence.Constants.TAG, String.format("Transferring compressed measurement (%s)",
                            DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true)));
                    Validate.notNull(endPointUrl);
                    final boolean transmissionSuccessful = syncPerformer.sendData(http, syncResult, endPointUrl,
                            measurement.getIdentifier(), deviceId, compressedTransferTempFile,
                            new UploadProgressListener() {
                                @Override
                                public void updatedProgress(float percent) {
                                    for (final ConnectionStatusListener listener : progressListener) {
                                        listener.onProgress(percent, measurement.getIdentifier());
                                    }
                                }
                            }, jwtAuthToken);
                    if (!transmissionSuccessful) {
                        break;
                    }

                    // Mark successfully transmitted measurement as synced
                    try {
                        persistence.markAsSynchronized(measurement);
                    } catch (final NoSuchMeasurementException e) {
                        throw new IllegalStateException(e);
                    }
                    Log.d(Constants.TAG, "Measurement marked as synced.");

                } finally {
                    if (compressedTransferTempFile.exists()) {
                        Validate.isTrue(compressedTransferTempFile.delete());
                    }
                }
            }
        } catch (final CursorIsNullException e) {
            Log.w(TAG, "DatabaseException: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode(), e.getMessage());
        } catch (final BadRequestException e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numConflictDetectedExceptions++;
            sendErrorIntent(context, BAD_REQUEST.getCode(), e.getMessage());
        } catch (final AuthenticatorException e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_ERROR.getCode(), e.getMessage());
        } finally {
            Log.d(TAG, String.format("Sync finished. (%s)", syncResult.hasError() ? "ERROR" : "success"));
            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncFinished();
            }
        }
    }

    /**
     * We need to check if the network is still syncable:
     * - this is only possible indirect, we check if the surveyor disabled auto sync for the account
     * - the network settings could have changed between sync initial call and "now"
     * - there is a bug [MOV-616] that we were not yet able to fix that SyncService is created and sync starts
     * when no wifi is connected but mobile data, syncOnUnMeteredNetworkOnly is on, after some time
     *
     * @param account The {@code Account} to check the status for
     * @param authority The authority string for the synchronization to check
     */
    private boolean isAutoSyncDisallowed(@NonNull final Account account, @NonNull final String authority) {

        final boolean isSyncAllowed = ContentResolver.getSyncAutomatically(account, authority);
        if (!isSyncAllowed) {
            Log.w(TAG,
                    "Sync aborted: auto sync is not enabled for this account (the network is probably metered and syncOnUnMeteredNetworkOnly activated).");
        }
        return !isSyncAllowed;
    }

    private void addConnectionListener(final @NonNull ConnectionStatusListener listener) {
        progressListener.add(listener);
    }
}
