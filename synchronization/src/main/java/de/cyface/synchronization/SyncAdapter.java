/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.synchronization;

import static de.cyface.persistence.serialization.MeasurementSerializer.COMPRESSED_TRANSFER_FILE_PREFIX;
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;
import static de.cyface.synchronization.Constants.TAG;
import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.AUTHENTICATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATABASE_ERROR;

import java.io.File;
import java.io.FilenameFilter;
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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.MeasurementContentProviderClient;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Track;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.6.5
 * @since 2.0.0
 */
public final class SyncAdapter extends AbstractThreadedSyncAdapter {

    /**
     * This bundle flag allows our unit tests to mock {@link #isConnected(Account, String)}.
     * <p>
     * We cannot use {@code ContentResolver} as we do in the production code as this is an Unit test.
     * When this {@code Bundle} extra is set (no matter to which String) the {@link #isConnected(Account, String)}
     * method returns true;
     */
    static final String MOCK_IS_CONNECTED_TO_RETURN_TRUE = "mocked_periodic_sync_check_false";
    private final Collection<ConnectionStatusListener> progressListener;
    private final Http http;
    /**
     * When this is set to true the {@link #isConnected(Account, String)} method always returns true.
     */
    private boolean mockIsConnectedToReturnTrue;

    /**
     * Creates a new completely initialized {@code SyncAdapter}. See the documentation of
     * <code>AbstractThreadedSyncAdapter</code> from the Android framework for further information.
     *
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean)}.
     */
    SyncAdapter(@NonNull final Context context, final boolean autoInitialize, @NonNull final Http http) {
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
    private SyncAdapter(@NonNull final Context context, final boolean autoInitialize, final boolean allowParallelSyncs,
            @NonNull final Http http) {
        super(context, autoInitialize, allowParallelSyncs);

        this.http = http;
        progressListener = new HashSet<>();
        addConnectionListener(new CyfaceConnectionStatusListener(context));
    }

    @Override
    public void onPerformSync(@NonNull final Account account, @NonNull final Bundle extras,
            @NonNull final String authority, @NonNull final ContentProviderClient provider,
            @NonNull final SyncResult syncResult) {
        // This allows us to mock the #isConnected() check for unit tests
        mockIsConnectedToReturnTrue = extras.containsKey(MOCK_IS_CONNECTED_TO_RETURN_TRUE);

        // The network setting may have changed since the initial sync call, avoid unnecessary serialization
        if (!isConnected(account, authority)) {
            Log.w(TAG, "Sync aborted: syncable connection not available anymore");
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

                // Load measurement with metadata
                Log.d(Constants.TAG,
                        String.format("Measurement with identifier %d is about to be loaded for transmission.",
                                measurement.getIdentifier()));
                final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(
                        measurement.getIdentifier(), provider, authority);
                final MetaData metaData = loadMetaData(measurement, persistence, deviceId, context);

                // The network setting may have changed since the initial sync call, avoid unnecessary serialization
                if (!isConnected(account, authority)) {
                    Log.w(TAG, "Sync aborted: syncable connection not available anymore");
                    return;
                }
                // Load compressed transfer file for measurement
                File compressedTransferTempFile = null;

                // Try to sync the transfer file - remove it afterwards
                try {
                    compressedTransferTempFile = serializer.writeSerializedCompressed(loader,
                            measurement.getIdentifier(), persistence);

                    // Acquire new auth token before each synchronization (old one could be expired)
                    try {
                        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
                        final Bundle authBundle = authenticator.getAuthToken(null, account, AUTH_TOKEN_TYPE, null);
                        Validate.notNull(authBundle);
                        jwtAuthToken = authBundle.getString(AccountManager.KEY_AUTHTOKEN);
                    } catch (final NetworkErrorException e) {
                        // This happened e.g. when Wifi was manually disabled just after synchronization started (Pixel
                        // 2 XL).
                        Log.w(TAG, "getAuthToken failed, was the connection closed? Aborting sync.");
                        return;
                    }
                    Validate.notNull(jwtAuthToken);
                    Log.d(TAG, "Sync authToken: **" + jwtAuthToken.substring(jwtAuthToken.length() - 7));

                    // The network setting may have changed since the initial sync call, avoid using metered network
                    // without permission

                    if (!isConnected(account, authority)) {
                        Log.w(TAG, "Sync aborted: syncable connection not available anymore");
                        return;
                    }

                    // Synchronize measurement
                    Log.d(de.cyface.persistence.Constants.TAG, String.format("Transferring compressed measurement (%s)",
                            DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true)));
                    Validate.notNull(endPointUrl);
                    final boolean transmissionSuccessful = syncPerformer.sendData(http, syncResult, endPointUrl,
                            metaData, compressedTransferTempFile, new UploadProgressListener() {
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
                    if (compressedTransferTempFile != null && compressedTransferTempFile.exists()) {
                        Validate.isTrue(compressedTransferTempFile.delete());
                    }
                }
            }
        } catch (final CursorIsNullException e) {
            Log.w(TAG, "DatabaseException: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode(), e.getMessage());
        } catch (final AuthenticatorException e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_ERROR.getCode(), e.getMessage());
        } finally {
            // In MOV-719 space used grows when capturing is stopped. This should ensure this cannot happen
            File[] tmpFiles = persistence.getCacheDir().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.contains(COMPRESSED_TRANSFER_FILE_PREFIX);
                }
            });
            for (File file : tmpFiles) {
                Log.w(TAG, "Cleaning forgotten compressedTransferFile: " + file.getName());
                Validate.isTrue(file.delete());
            }

            Log.d(TAG, String.format("Sync finished. (%s)", syncResult.hasError() ? "ERROR" : "success"));
            for (final ConnectionStatusListener listener : progressListener) {
                listener.onSyncFinished();
            }
        }
    }

    /**
     * Loads meta data required in the Multipart header to transfer files to the API.
     *
     * @param measurement The {@link Measurement} to load the meta data for
     * @param persistence The {@link PersistenceLayer} to load track data required
     * @param deviceId The device identifier generated for this device
     * @param context The {@code Context} to load the version name of this SDK
     * @return The {@link MetaData} loaded
     * @throws CursorIsNullException when accessing the {@code ContentProvider} failed
     */
    private MetaData loadMetaData(@NonNull final Measurement measurement,
            PersistenceLayer<DefaultPersistenceBehaviour> persistence, @NonNull final String deviceId,
            @NonNull final Context context) throws CursorIsNullException {

        // If there is only one location captured, start and end locations are identical
        final List<Track> tracks = persistence.loadTracks(measurement.getIdentifier());
        int locationCount = 0;
        for (final Track track : tracks) {
            locationCount += track.getGeoLocations().size();
        }
        Validate.isTrue(tracks.size() == 0 || (tracks.get(0).getGeoLocations().size() > 0
                && tracks.get(tracks.size() - 1).getGeoLocations().size() > 0));
        final List<GeoLocation> lastTrack = tracks.size() > 0 ? tracks.get(tracks.size() - 1).getGeoLocations() : null;
        @Nullable
        final GeoLocation startLocation = tracks.size() > 0 ? tracks.get(0).getGeoLocations().get(0) : null;
        @Nullable
        final GeoLocation endLocation = lastTrack != null ? lastTrack.get(lastTrack.size() - 1) : null;

        // Non location meta data
        final String deviceType = android.os.Build.MODEL;
        final String osVersion = "Android " + Build.VERSION.RELEASE;
        final String appVersion;
        final PackageManager packageManager = context.getPackageManager();
        try {
            appVersion = packageManager.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }

        return new MetaData(startLocation, endLocation, deviceId, measurement.getIdentifier(), deviceType, osVersion,
                appVersion, measurement.getDistance(), locationCount);
    }

    /**
     * We need to check if the syncable network is still syncable:
     * - this is only possible indirectly: we check if the surveyor disabled auto sync for the account
     * - the network settings could have changed between sync initial call and "now"
     * <p>
     * The implementation of this method must be identical to {@link WiFiSurveyor#isConnected()}.
     *
     * @param account The {@code Account} to check the status for
     * @param authority The authority string for the synchronization to check
     */
    private boolean isConnected(@NonNull final Account account, @NonNull final String authority) {
        if (mockIsConnectedToReturnTrue) {
            Log.w(TAG, "mockIsConnectedToReturnTrue triggered");
            return true;
        }

        // We cannot instantly check addPeriodicSync as this seems to be async. For this reason we have a test to ensure
        // it's set to the same state as syncAutomatically: WifiSurveyorTest.testSetConnected()

        return ContentResolver.getSyncAutomatically(account, authority);
    }

    private void addConnectionListener(@NonNull final ConnectionStatusListener listener) {
        progressListener.add(listener);
    }

    /**
     * Meta data which is required in the Multipart header to transfer files to the API.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 4.0.0
     */
    static class MetaData {
        final GeoLocation startLocation;
        final GeoLocation endLocation;
        final String deviceId;
        final long measurementId;
        final String deviceType;
        final String osVersion;
        final String appVersion;
        final double length;
        final int locationCount;

        MetaData(@Nullable final GeoLocation startLocation, @Nullable final GeoLocation endLocation,
                @NonNull final String deviceId, final long measurementId, @NonNull final String deviceType,
                @NonNull final String osVersion, @NonNull final String appVersion, final double length,
                final int locationCount) {
            this.startLocation = startLocation;
            this.endLocation = endLocation;
            this.deviceId = deviceId;
            this.measurementId = measurementId;
            this.deviceType = deviceType;
            this.osVersion = osVersion;
            this.appVersion = appVersion;
            this.length = length;
            this.locationCount = locationCount;
        }
    }
}
