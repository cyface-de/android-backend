package de.cyface.synchronization;

import static de.cyface.utils.ErrorHandler.sendErrorIntent;
import static de.cyface.utils.ErrorHandler.ErrorCode.AUTHENTICATION_CANCELED;
import static de.cyface.utils.ErrorHandler.ErrorCode.AUTHENTICATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATABASE_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.DATA_TRANSMISSION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.MALFORMED_URL;
import static de.cyface.utils.ErrorHandler.ErrorCode.NETWORK_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.SERVER_UNAVAILABLE;
import static de.cyface.utils.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNAUTHORIZED;
import static de.cyface.utils.ErrorHandler.ErrorCode.UNREADABLE_HTTP_RESPONSE;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
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

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;
import de.cyface.utils.Validate;

/**
 * The SyncAdapter implements Android's SyncAdapter which is responsible for the synchronization.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class CyfaceSyncAdapter extends AbstractThreadedSyncAdapter {

    private final static String TAG = "de.cyface.sync";
    private final Collection<SyncProgressListener> progressListener;
    private final Http http;
    private final int geoLocationsUploadBatchSize;
    private final int accelerationsUploadBatchSize;
    private final int rotationsUploadBatchSize;
    private final int directionsUploadBatchSize;

    /**
     * Creates a new completely initialized {@code CyfaceSyncAdapter}.
     *
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean)}.
     * @param geoLocationsUploadBatchSize the number of elements to transmit in one batch
     * @param accelerationsUploadBatchSize the number of elements to transmit in one batch
     * @param rotationsUploadBatchSize the number of elements to transmit in one batch
     * @param directionsUploadBatchSize the number of elements to transmit in one batch
     */
    public CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize, final @NonNull Http http,
            final int geoLocationsUploadBatchSize, final int accelerationsUploadBatchSize,
            final int rotationsUploadBatchSize, final int directionsUploadBatchSize) {
        this(context, autoInitialize, false, http, geoLocationsUploadBatchSize, accelerationsUploadBatchSize,
                rotationsUploadBatchSize, directionsUploadBatchSize);
    }

    /**
     * Creates a new completely initialized {@code CyfaceSyncAdapter}.
     *
     * @param context The context this transmitter is active under.
     * @param autoInitialize More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean)}.
     * @param allowParallelSyncs More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean, boolean)}.
     * @param geoLocationsUploadBatchSize the number of elements to transmit in one batch
     * @param accelerationsUploadBatchSize the number of elements to transmit in one batch
     * @param rotationsUploadBatchSize the number of elements to transmit in one batch
     * @param directionsUploadBatchSize the number of elements to transmit in one batch
     */
    public CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize,
            final boolean allowParallelSyncs, final @NonNull Http http, int geoLocationsUploadBatchSize,
            int accelerationsUploadBatchSize, int rotationsUploadBatchSize, int directionsUploadBatchSize) {
        super(context, autoInitialize, allowParallelSyncs);

        this.http = http;
        this.geoLocationsUploadBatchSize = geoLocationsUploadBatchSize;
        this.accelerationsUploadBatchSize = accelerationsUploadBatchSize;
        this.rotationsUploadBatchSize = rotationsUploadBatchSize;
        this.directionsUploadBatchSize = directionsUploadBatchSize;
        progressListener = new HashSet<>();
        addSyncProgressListener(new CyfaceSyncProgressListener(context));
    }

    /**
     * Starts the sync process if non-synced data and wifi is available. This contains logging into
     * the server and starting the measurement transmission.
     *
     * @param account The user's android sync account which is needed to start a synchronization
     * @param extras not used
     * @param authority The authority used to identify the content provider containing the data to synchronize.
     * @param provider a link to the content provider which is needed to access the data layer
     * @param syncResult used to check if the sync was successful
     */
    @Override
    // FIXME: this method is too long/complex, even for the IDE to analyse
    public void onPerformSync(final @NonNull Account account, Bundle extras, String authority,
            final @NonNull ContentProviderClient provider, final @NonNull SyncResult syncResult) {
        final Context context = getContext();
        Log.d(TAG, "Sync started.");

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String deviceIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
        final String url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        Validate.notNull(deviceIdentifier,
                "Sync canceled: No installation identifier for this application set in its preferences.");
        Validate.notNull(url,
                "Sync canceled: Server url not available. Please set the applications server url preference.");

        Cursor unsyncedMeasurementsCursor = null;
        try {
            try {
                unsyncedMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider,
                        authority);
            } catch (final RemoteException e) {
                throw new DatabaseException("Failed to loadSyncableMeasurements: " + e.getMessage(), e);
            }
            final boolean atLeastOneMeasurementExists = unsyncedMeasurementsCursor.moveToNext();
            if (!atLeastOneMeasurementExists) {
                Log.i(TAG, "Unable to sync data: no unsynchronized data");
                return;
            }
            try {
                notifySyncStarted(countUnsyncedDataPoints(provider, unsyncedMeasurementsCursor, authority));
            } catch (final RemoteException e) {
                throw new DatabaseException("Failed to notifySyncStarted: " + e.getMessage(), e);
            }

            final GeoLocationJsonMapper geoLocationJsonMapper = new GeoLocationJsonMapper();
            final AccelerationJsonMapper accelerationJsonMapper = new AccelerationJsonMapper();
            final RotationJsonMapper rotationJsonMapper = new RotationJsonMapper();
            final DirectionJsonMapper directionJsonMapper = new DirectionJsonMapper();

            // The cursor is reset to initial position (i.e. 0) by countUnsyncedDataPoints
            do {
                // Prepare slice template
                final int identifierColumnIndex = unsyncedMeasurementsCursor.getColumnIndex(BaseColumns._ID);
                final long measurementIdentifier = unsyncedMeasurementsCursor.getLong(identifierColumnIndex);
                final JSONObject measurementSliceTemplate = prepareMeasurementSliceTemplate(measurementIdentifier,
                        unsyncedMeasurementsCursor, deviceIdentifier);

                // Process slices
                final MeasurementContentProviderClient dataAccessLayer = new MeasurementContentProviderClient(
                        measurementIdentifier, provider, authority);

                final long numberOfGeolocations, numberOfAccelerationPoints, numberOfRotationPoints,
                        numberOfMagneticValuePoints;
                try {
                    numberOfGeolocations = dataAccessLayer.countData(createGeoLocationsUri(authority),
                            GpsPointsTable.COLUMN_MEASUREMENT_FK);
                    numberOfAccelerationPoints = dataAccessLayer.countData(createAccelerationsUri(authority),
                            SamplePointTable.COLUMN_MEASUREMENT_FK);
                    numberOfRotationPoints = dataAccessLayer.countData(createRotationsUri(authority),
                            RotationPointTable.COLUMN_MEASUREMENT_FK);
                    numberOfMagneticValuePoints = dataAccessLayer.countData(createDirectionsUri(authority),
                            MagneticValuePointTable.COLUMN_MEASUREMENT_FK);
                } catch (final RemoteException e) {
                    throw new DatabaseException("Failed to countData: " + e.getMessage(), e);
                }
                for (int geoLocationStartIndex = 0, accelerationPointStartIndex = 0, rotationPointStartIndex = 0, directionPointStartIndex = 0; geoLocationStartIndex < numberOfGeolocations
                        || accelerationPointStartIndex < numberOfAccelerationPoints
                        || rotationPointStartIndex < numberOfRotationPoints
                        || directionPointStartIndex < numberOfMagneticValuePoints; geoLocationStartIndex += geoLocationsUploadBatchSize, accelerationPointStartIndex += accelerationsUploadBatchSize, rotationPointStartIndex += rotationsUploadBatchSize, directionPointStartIndex += directionsUploadBatchSize) {

                    final JSONObject measurementSlice;
                    try {
                        measurementSlice = fillMeasurementSlice(measurementSliceTemplate, dataAccessLayer,
                                geoLocationStartIndex, accelerationPointStartIndex, rotationPointStartIndex,
                                directionPointStartIndex, geoLocationJsonMapper, accelerationJsonMapper,
                                rotationJsonMapper, directionJsonMapper);
                    } catch (final RemoteException e) {
                        throw new DatabaseException("Failed to fillMeasurementSlice: " + e.getMessage(), e);
                    }
                    postMeasurementSlice(authority, provider, syncResult, context, url, account, measurementSlice,
                            geoLocationJsonMapper, accelerationJsonMapper, rotationJsonMapper, directionJsonMapper);
                }
            } while (unsyncedMeasurementsCursor.moveToNext());

        } catch (final DatabaseException e) {
            Log.w(TAG, "RemoteException: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode());
            notifySyncReadError(context.getString(R.string.error_message_database), e);
        } catch (final RequestParsingException e) {
            Log.w(TAG, "RequestParsingException: " + e.getMessage());
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_synchronization_error), e);
        } finally {
            Log.d(TAG, String.format("Sync finished. (error: %b)", syncResult.hasError()));
            notifySyncFinished();
            if (unsyncedMeasurementsCursor != null) {
                unsyncedMeasurementsCursor.close();
            }
        }
    }

    JSONObject fillMeasurementSlice(final JSONObject measurementSliceTemplate,
            final MeasurementContentProviderClient dataAccessLayer, final int g, final int a, final int r, final int d,
            final GeoLocationJsonMapper geoLocationJsonMapper, final AccelerationJsonMapper accelerationJsonMapper,
            final RotationJsonMapper rotationJsonMapper, final DirectionJsonMapper directionJsonMapper)
            throws RemoteException, RequestParsingException {
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor rotationsCursor = null;
        Cursor directionsCursor = null;
        try {
            geoLocationsCursor = dataAccessLayer.loadGeoLocations(g, g + geoLocationsUploadBatchSize);
            final JSONArray geoLocationsJsonArray = transformToJsonArray(geoLocationsCursor, geoLocationJsonMapper);
            measurementSliceTemplate.put("gpsPoints", geoLocationsJsonArray);

            accelerationsCursor = dataAccessLayer.load3DPoint(new AccelerationsSerializer(), a,
                    a + accelerationsUploadBatchSize);
            final JSONArray accelerationPointsArray = transformToJsonArray(accelerationsCursor, accelerationJsonMapper);
            measurementSliceTemplate.put("accelerationPoints", accelerationPointsArray);

            rotationsCursor = dataAccessLayer.load3DPoint(new RotationsSerializer(), r, r + rotationsUploadBatchSize);
            final JSONArray rotationPointsArray = transformToJsonArray(rotationsCursor, rotationJsonMapper);
            measurementSliceTemplate.put("rotationPoints", rotationPointsArray);

            directionsCursor = dataAccessLayer.load3DPoint(new DirectionsSerializer(), d,
                    d + directionsUploadBatchSize);
            final JSONArray magneticValuePointsArray = transformToJsonArray(directionsCursor, directionJsonMapper);
            measurementSliceTemplate.put("magneticValuePoints", magneticValuePointsArray);
        } catch (final JSONException e) {
            throw new RequestParsingException("Failed to parse measurement data.", e);
        } finally {
            if (geoLocationsCursor != null) {
                geoLocationsCursor.close();
            }
            if (accelerationsCursor != null) {
                accelerationsCursor.close();
            }
            if (rotationsCursor != null) {
                rotationsCursor.close();
            }
            if (directionsCursor != null) {
                directionsCursor.close();
            }
        }
        return measurementSliceTemplate;
    }

    /**
     * Posts a slice of a measurement to the responsible endpoint sitting behind the provided {@code url}.
     * The posted points are marked as synced afterwards. The sync progress is broadcasted.
     *
     * @param authority The authority used to access the data.
     * @param provider The {@link ContentProviderClient} used to access the data.
     * @param syncResult The {@link SyncResult} used to store sync error information.
     * @param context The {@link Context} to access the {@link AccountManager}.
     * @param url The URL of the Cyface Data Collector API to post the data to.
     * @param account The {@link Account} used to post the data.
     * @param measurementSlice The measurement slice as {@link JSONObject}.
     * @param geoLocationJsonMapper The geolocations to post.
     * @param accelerationJsonMapper The acceleration points to post.
     * @param rotationJsonMapper The rotation points to post.
     * @param directionJsonMapper The direction points to post.
     * @throws RequestParsingException When the post request could not be generated or when data could not be parsed
     *             from the measurement slice.
     */
    private void postMeasurementSlice(final String authority, final ContentProviderClient provider,
            final SyncResult syncResult, final Context context, final String url, final Account account,
            JSONObject measurementSlice, final GeoLocationJsonMapper geoLocationJsonMapper,
            final AccelerationJsonMapper accelerationJsonMapper, final RotationJsonMapper rotationJsonMapper,
            final DirectionJsonMapper directionJsonMapper) throws RequestParsingException {
        try {
            final URL postUrl = new URL(http.returnUrlWithTrailingSlash(url) + "/measurements/");
            final String jwtBearer = AccountManager.get(context).blockingGetAuthToken(account,
                    Constants.AUTH_TOKEN_TYPE, false);
            HttpURLConnection con = null;
            try {
                con = http.openHttpConnection(postUrl, jwtBearer);
                Log.d(TAG, "Posing measurement slice ...");
                http.post(con, measurementSlice, true);

                // We mark the data of each point type separately to avoid #CY-3859 parcel size error.
                markAsSynced(provider, authority, measurementSlice, geoLocationJsonMapper);
                markAsSynced(provider, authority, measurementSlice, accelerationJsonMapper);
                markAsSynced(provider, authority, measurementSlice, rotationJsonMapper);
                markAsSynced(provider, authority, measurementSlice, directionJsonMapper);
            } catch (final OperationApplicationException | RemoteException e) {
                throw new DatabaseException("Failed to applyBatch: " + e.getMessage(), e);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
            notifySyncProgress(measurementSlice);
        }
        // We currently inform two groups of listeners: the error intent listeners (to show the
        // error) and the syncProgress listeners to upgrade the sync progress UI
        catch (final ServerUnavailableException e) {
            syncResult.stats.numAuthExceptions++; // TODO: Do we use those statistics ?
            sendErrorIntent(context, SERVER_UNAVAILABLE.getCode());
            notifySyncTransmitError(e.getMessage(), e);
        } catch (final MalformedURLException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode());
            notifySyncTransmitError(String.format(context.getString(R.string.error_message_url_parsing), url), e);
        } catch (final AuthenticatorException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_ERROR.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_authentication_error), e);
        } catch (final OperationCanceledException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_CANCELED.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_authentication_canceled), e);
        } catch (final ResponseParsingException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_http_response_parsing), e);
        } catch (final DataTransmissionException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, DATA_TRANSMISSION_ERROR.getCode(), e.getHttpStatusCode());
            notifySyncTransmitError(context.getString(R.string.error_message_data_transmission_error_with_code), e);
        } catch (final SynchronisationException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode());
            notifySyncTransmitError(e.getMessage(), e);
        } catch (final IOException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, NETWORK_ERROR.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_network_error), e);
        } catch (final DatabaseException e) {
            Log.w(TAG, "Database Exception: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode());
            notifySyncReadError(context.getString(R.string.error_message_database), e);
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_unauthorized), e);
        }
    }

    /**
     * Marks the points of the type determined by the provided {@code jsonMapper} of the measurement slice as synced.
     *
     * @param provider The provider to access the data.
     * @param authority The authority to access the data.
     * @param measurementSlice The measurement slice containing the data to be mark as synced.
     * @param jsonMapper The mapped to parse data of a specific type from the measurement slice. E.g.
     *            {@link GeoLocationJsonMapper}, {@link AccelerationJsonMapper}
     */
    private void markAsSynced(final ContentProviderClient provider, final String authority, final JSONObject measurementSlice,
                              final JsonMapper jsonMapper)
            throws SynchronisationException, RemoteException, OperationApplicationException {

        ArrayList<ContentProviderOperation> markAsSyncedOperation = new ArrayList<>(
                jsonMapper.buildMarkSyncedOperation(measurementSlice, authority));
        Log.d(TAG, String.format("Marking %d points as synced of type %s", markAsSyncedOperation.size(),
                jsonMapper.getClass().getSimpleName()));
        provider.applyBatch(markAsSyncedOperation);
    }

    private JSONObject prepareMeasurementSliceTemplate(final long measurementIdentifier,
                                                       final Cursor unsyncedMeasurementsCursor, final String deviceIdentifier) throws RequestParsingException {

        final String measurementContext = unsyncedMeasurementsCursor
                .getString(unsyncedMeasurementsCursor.getColumnIndex(MeasurementTable.COLUMN_VEHICLE));

        final JSONObject measurementSlice = new JSONObject();
        try {
            measurementSlice.put("id", measurementIdentifier);
            measurementSlice.put("deviceId", deviceIdentifier);
            measurementSlice.put("vehicle", measurementContext);
        } catch (final JSONException e) {
            throw new RequestParsingException("Failed to parse measurement info.", e);
        }
        return measurementSlice;
    }

    private JSONArray transformToJsonArray(final @NonNull Cursor cursor, final @NonNull JsonMapper mapper)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();
        while (cursor.moveToNext()) {
            JSONObject json = mapper.map(cursor);
            jsonArray.put(json);
        }
        return jsonArray;
    }

    private void addSyncProgressListener(final @NonNull SyncProgressListener listener) {
        progressListener.add(listener);
    }

    private void notifySyncStarted(final long pointsToBeTransmitted) {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncStarted(pointsToBeTransmitted);
        }
    }

    private void notifySyncFinished() {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncFinished();
        }
    }

    /**
     * Notifies about database access errors.
     */
    private void notifySyncReadError(final @NonNull String errorMessage, final Throwable errorType) {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncReadError(errorMessage, errorType);
        }
    }

    /**
     * Notifies about transmission errors.
     */
    private void notifySyncTransmitError(final @NonNull String errorMessage, final Throwable errorType) {
        Log.e(TAG, "Unable to sync data.", errorType);
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncTransmitError(errorMessage, errorType);
        }
    }

    /**
     * Notifies about sync progress.
     * 
     * @throws RequestParsingException when data could not be parsed from the measurement slice.
     */
    private void notifySyncProgress(final @NonNull JSONObject measurementSlice) throws RequestParsingException {
        for (SyncProgressListener listener : progressListener) {
            listener.onProgress(measurementSlice);
        }
    }

    private long countUnsyncedDataPoints(final @NonNull ContentProviderClient provider,
            final @NonNull Cursor syncableMeasurements, final @NonNull String authority) throws RemoteException {
        long ret = 0L;
        int initialPosition = syncableMeasurements.getPosition();
        syncableMeasurements.moveToFirst();
        do {
            long measurementIdentifier = syncableMeasurements
                    .getLong(syncableMeasurements.getColumnIndex(BaseColumns._ID));
            MeasurementContentProviderClient client = new MeasurementContentProviderClient(measurementIdentifier,
                    provider, authority);

            ret += client.countData(createGeoLocationsUri(authority), GpsPointsTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createAccelerationsUri(authority), SamplePointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createRotationsUri(authority), RotationPointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(createDirectionsUri(authority), MagneticValuePointTable.COLUMN_MEASUREMENT_FK);
        } while (syncableMeasurements.moveToNext());
        final int offsetToInitialPosition = syncableMeasurements.getPosition() - initialPosition;
        syncableMeasurements.move(-offsetToInitialPosition);
        return ret;
    }

    private static Uri createGeoLocationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(GpsPointsTable.URI_PATH).build();
    }

    private static Uri createAccelerationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(SamplePointTable.URI_PATH).build();
    }

    private static Uri createRotationsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(RotationPointTable.URI_PATH).build();
    }

    private static Uri createDirectionsUri(final @NonNull String authority) {
        return new Uri.Builder().scheme("content").authority(authority).appendPath(MagneticValuePointTable.URI_PATH)
                .build();
    }
}
