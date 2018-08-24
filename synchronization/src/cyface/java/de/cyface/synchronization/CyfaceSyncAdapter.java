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
            unsyncedMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider, authority);
            final boolean atLeastOneMeasurementExists = unsyncedMeasurementsCursor.moveToNext();
            if (!atLeastOneMeasurementExists) {
                Log.i(TAG, "Unable to sync data: no unsynchronized data");
                return;
            }
            notifySyncStarted(countUnsyncedDataPoints(provider, unsyncedMeasurementsCursor, authority));

            final GeoLocationJsonMapper geoLocationJsonMapper = new GeoLocationJsonMapper();
            final AccelerationJsonMapper accelerationJsonMapper = new AccelerationJsonMapper();
            final RotationJsonMapper rotationJsonMapper = new RotationJsonMapper();
            final DirectionJsonMapper directionJsonMapper = new DirectionJsonMapper();

            // The cursor is reset to initial position (i.e. 0) by countUnsyncedDataPoints
            do {
                // Prepare slice template
                final int identifierColumnIndex = unsyncedMeasurementsCursor.getColumnIndex(BaseColumns._ID);
                final long measurementIdentifier = unsyncedMeasurementsCursor.getLong(identifierColumnIndex);
                JSONObject measurementSlice = prepareMeasurementSliceTemplate(measurementIdentifier,
                        unsyncedMeasurementsCursor, deviceIdentifier);

                // Process slices
                final MeasurementContentProviderClient dataAccessLayer = new MeasurementContentProviderClient(
                        measurementIdentifier, provider, authority);
                for (int g = 0, a = 0, r = 0, d = 0; g < dataAccessLayer.countData(createGeoLocationsUri(authority),
                        GpsPointsTable.COLUMN_MEASUREMENT_FK)
                        || a < dataAccessLayer.countData(createAccelerationsUri(authority),
                                SamplePointTable.COLUMN_MEASUREMENT_FK)
                        || r < dataAccessLayer.countData(createRotationsUri(authority),
                                RotationPointTable.COLUMN_MEASUREMENT_FK)
                        || d < dataAccessLayer.countData(createDirectionsUri(authority),
                                MagneticValuePointTable.COLUMN_MEASUREMENT_FK); g += geoLocationsUploadBatchSize, a += accelerationsUploadBatchSize, r += rotationsUploadBatchSize, d += directionsUploadBatchSize) {

                    measurementSlice = fillMeasurementSlice(measurementSlice, dataAccessLayer, g, a, r, d,
                            geoLocationJsonMapper, accelerationJsonMapper, rotationJsonMapper, directionJsonMapper);
                    postMeasurementSlice(authority, provider, syncResult, context, url, account, measurementSlice,
                            geoLocationJsonMapper, accelerationJsonMapper, rotationJsonMapper, directionJsonMapper);
                }
            } while (unsyncedMeasurementsCursor.moveToNext());

        } catch (final RemoteException e) {
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode());
            notifySyncReadError(context.getString(R.string.error_message_database), e);
        } catch (final RequestParsingException e) {
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

    private JSONObject fillMeasurementSlice(final JSONObject measurementSlice,
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
            measurementSlice.put("gpsPoints", geoLocationsJsonArray);

            accelerationsCursor = dataAccessLayer.load3DPoint(new AccelerationsSerializer(), a,
                    a + accelerationsUploadBatchSize);
            final JSONArray accelerationPointsArray = transformToJsonArray(accelerationsCursor, accelerationJsonMapper);
            measurementSlice.put("accelerationPoints", accelerationPointsArray);

            rotationsCursor = dataAccessLayer.load3DPoint(new RotationsSerializer(), r, r + rotationsUploadBatchSize);
            final JSONArray rotationPointsArray = transformToJsonArray(rotationsCursor, rotationJsonMapper);
            measurementSlice.put("rotationPoints", rotationPointsArray);

            directionsCursor = dataAccessLayer.load3DPoint(new DirectionsSerializer(), d,
                    d + directionsUploadBatchSize);
            final JSONArray magneticValuePointsArray = transformToJsonArray(directionsCursor, directionJsonMapper);
            measurementSlice.put("magneticValuePoints", magneticValuePointsArray);
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
        return measurementSlice;
    }

    private void postMeasurementSlice(final String authority, final ContentProviderClient provider,
            final SyncResult syncResult, final Context context, final String url, final Account account,
            JSONObject measurementSlice, final GeoLocationJsonMapper geoLocationJsonMapper,
            final AccelerationJsonMapper accelerationJsonMapper, final RotationJsonMapper rotationJsonMapper,
            final DirectionJsonMapper directionJsonMapper) throws RequestParsingException, RemoteException {
        try {
            final URL postUrl = new URL(http.returnUrlWithTrailingSlash(url) + "/measurements/");
            final String jwtBearer = AccountManager.get(context).blockingGetAuthToken(account,
                    Constants.AUTH_TOKEN_TYPE, false);
            HttpURLConnection con = null;
            try {
                con = http.openHttpConnection(postUrl, jwtBearer);
                http.post(con, measurementSlice, true);

                ArrayList<ContentProviderOperation> markAsSyncedOperation = new ArrayList<>();
                markAsSyncedOperation
                        .addAll(geoLocationJsonMapper.buildMarkSyncedOperation(measurementSlice, authority));
                markAsSyncedOperation
                        .addAll(accelerationJsonMapper.buildMarkSyncedOperation(measurementSlice, authority));
                markAsSyncedOperation.addAll(rotationJsonMapper.buildMarkSyncedOperation(measurementSlice, authority));
                markAsSyncedOperation.addAll(directionJsonMapper.buildMarkSyncedOperation(measurementSlice, authority));
                provider.applyBatch(markAsSyncedOperation);
            } catch (final OperationApplicationException e) {
                throw new DatabaseException("ApplyBatch operation failed", e);
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
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode());
            notifySyncReadError(context.getString(R.string.error_message_database), e);
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode());
            notifySyncTransmitError(context.getString(R.string.error_message_unauthorized), e);
        }
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

    // TODO: Do we need to differentiate between a "read" and "transmission" error? What for?
    private void notifySyncReadError(final @NonNull String errorMessage, final Throwable errorType) {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncReadError(errorMessage, errorType);
        }
    }

    private void notifySyncTransmitError(final @NonNull String errorMessage, final Throwable errorType) {
        Log.e(TAG, "Unable to sync data.", errorType);
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncTransmitError(errorMessage, errorType);
        }
    }

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
