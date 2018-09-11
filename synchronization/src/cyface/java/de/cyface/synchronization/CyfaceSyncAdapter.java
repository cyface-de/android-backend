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

import de.cyface.persistence.AccelerationPointTable;
import de.cyface.persistence.DirectionPointTable;
import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.RotationPointTable;
import de.cyface.utils.Validate;

/**
 * The SyncAdapter implements Android's SyncAdapter which is responsible for the synchronization.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 2.0.0
 */
public final class CyfaceSyncAdapter extends AbstractThreadedSyncAdapter {

    private final static String TAG = "de.cyface.sync";
    private final Collection<ConnectionListener> progressListener;
    private final Http http;
    private final int geoLocationsUploadBatchSize;
    private final int accelerationsUploadBatchSize;
    private final int rotationsUploadBatchSize;
    private final int directionsUploadBatchSize;
    private long pointsToTransmit;
    private long transmittedPoints;

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
    CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize, final @NonNull Http http,
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
    private CyfaceSyncAdapter(final @NonNull Context context, final boolean autoInitialize,
            final boolean allowParallelSyncs, final @NonNull Http http, int geoLocationsUploadBatchSize,
            int accelerationsUploadBatchSize, int rotationsUploadBatchSize, int directionsUploadBatchSize) {
        super(context, autoInitialize, allowParallelSyncs);

        this.http = http;
        this.geoLocationsUploadBatchSize = geoLocationsUploadBatchSize;
        this.accelerationsUploadBatchSize = accelerationsUploadBatchSize;
        this.rotationsUploadBatchSize = rotationsUploadBatchSize;
        this.directionsUploadBatchSize = directionsUploadBatchSize;
        progressListener = new HashSet<>();
        addSyncProgressListener(new CyfaceConnectionListener(context));
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
        Log.d(TAG, "Sync started.");
        final Context context = getContext();

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        final String deviceIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
        final String url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        Validate.notNull(deviceIdentifier,
                "Sync canceled: No installation identifier for this application set in its preferences.");
        Validate.notNull(url,
                "Sync canceled: Server url not available. Please set the applications server url preference.");

        Cursor syncableMeasurementsCursor = null;
        try {
            // Load all Measurements that are finished capturing
            syncableMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider, authority);
            notifySyncStarted(countUnsyncedDataPoints(provider, syncableMeasurementsCursor, authority));

            // The cursor is reset to initial position (i.e. 0) by countUnsyncedDataPoints
            while (syncableMeasurementsCursor.moveToNext()) {
                final int identifierColumnIndex = syncableMeasurementsCursor.getColumnIndex(BaseColumns._ID);
                final long measurementIdentifier = syncableMeasurementsCursor.getLong(identifierColumnIndex);
                final boolean measurementTransmitted = syncMeasurement(authority, provider, syncableMeasurementsCursor,
                        deviceIdentifier, syncResult, context, url, account, measurementIdentifier);
                if (measurementTransmitted) {
                    final MeasurementContentProviderClient loader = new MeasurementContentProviderClient(
                            measurementIdentifier, provider, authority);
                    loader.cleanMeasurement();
                    Log.d(TAG, "Measurement marked as synced.");
                }
            }
        } catch (final DatabaseException | RemoteException e) {
            Log.w(TAG, "DatabaseException: " + e.getMessage());
            syncResult.databaseError = true;
            sendErrorIntent(context, DATABASE_ERROR.getCode());
        } catch (final RequestParsingException | SynchronisationException e) {
            Log.w(TAG, e.getClass().getSimpleName() + ": " + e.getMessage());
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode());
        } finally {
            Log.d(TAG, String.format("Sync finished. (error: %b)", syncResult.hasError()));
            notifySyncFinished();
            if (syncableMeasurementsCursor != null) {
                syncableMeasurementsCursor.close();
            }
        }
    }

    /**
     * Transmits the selected measurement to the Cyface server.
     *
     * @param authority The authority to access the data
     * @param provider The provider to access the data
     * @param syncableMeasurementsCursor The cursor to access the measurement
     * @param deviceIdentifier The device id
     * @param syncResult The {@link SyncResult} to store sync error details
     * @param context The context to access the {@link AccountManager}
     * @param url The url to the Cyface API
     * @param account The {@link Account} to use for synchronization
     * @param measurementIdentifier The id of the measurement to transmit
     * @return True if the transmission was successful
     * @throws RequestParsingException When data could not be inserted or loaded into/from the JSON representation
     * @throws DatabaseException When the data could not be loaded from the persistence layer or the delete operation
     *             failed
     * @throws SynchronisationException When the data could not be mapped
     */
    private boolean syncMeasurement(final String authority, final ContentProviderClient provider,
            final Cursor syncableMeasurementsCursor, final String deviceIdentifier, final SyncResult syncResult,
            final Context context, final String url, final Account account, final long measurementIdentifier)
            throws RequestParsingException, DatabaseException, SynchronisationException {
        final JSONObject measurementSliceTemplate = prepareMeasurementSliceTemplate(measurementIdentifier,
                syncableMeasurementsCursor, deviceIdentifier);
        final MeasurementContentProviderClient dataAccessLayer = new MeasurementContentProviderClient(
                measurementIdentifier, provider, authority);
        final long numberOfGeolocations;
        final long numberOfAccelerationPoints;
        final long numberOfRotationPoints;
        final long numberOfDirectionPoints;
        try {
            numberOfGeolocations = dataAccessLayer.countData(createGeoLocationsUri(authority),
                    GpsPointsTable.COLUMN_MEASUREMENT_FK);
            numberOfAccelerationPoints = dataAccessLayer.countData(createAccelerationsUri(authority),
                    AccelerationPointTable.COLUMN_MEASUREMENT_FK);
            numberOfRotationPoints = dataAccessLayer.countData(createRotationsUri(authority),
                    RotationPointTable.COLUMN_MEASUREMENT_FK);
            numberOfDirectionPoints = dataAccessLayer.countData(createDirectionsUri(authority),
                    DirectionPointTable.COLUMN_MEASUREMENT_FK);
        } catch (final RemoteException e) {
            throw new DatabaseException("Failed to sync measurement slices. " + e.getMessage(), e);
        }
        final GeoLocationJsonMapper geoLocationJsonMapper = new GeoLocationJsonMapper();
        final AccelerationJsonMapper accelerationJsonMapper = new AccelerationJsonMapper();
        final RotationJsonMapper rotationJsonMapper = new RotationJsonMapper();
        final DirectionJsonMapper directionJsonMapper = new DirectionJsonMapper();

        // Sync all slices
        for (int geoLocationsCounter = 0, accelerationsCounter = 0, rotationsCounter = 0, directionsCounter = 0; geoLocationsCounter < numberOfGeolocations
                || accelerationsCounter < numberOfAccelerationPoints || rotationsCounter < numberOfRotationPoints
                || directionsCounter < numberOfDirectionPoints; geoLocationsCounter += geoLocationsUploadBatchSize, accelerationsCounter += accelerationsUploadBatchSize, rotationsCounter += rotationsUploadBatchSize, directionsCounter += directionsUploadBatchSize) {

            // We delete the points in each iteration so we load always from cursor position 0
            final JSONObject measurementSlice = fillMeasurementSlice(measurementSliceTemplate, dataAccessLayer, 0, 0, 0,
                    0, geoLocationJsonMapper, accelerationJsonMapper, rotationJsonMapper, directionJsonMapper);
            final boolean transmissionSuccessful = postMeasurementSlice(syncResult, context, url, account,
                    measurementSlice);
            if (!transmissionSuccessful) {
                return false;
            }
            try {
                // TODO: This way of deleting points is probably rather slow when lots of data is
                // stored on old devices (from experience). We had a faster but uglier workaround
                // but won't reimplement this here in the SDK. Instead we'll use the Cyface Byte Format
                // from the Movebis flavor and the file uploader which we'll implement before releasing this.
                // TODO: We should probably remove the unused isSynced flag from the points after
                // we implemented the Cyface Byte Format synchronization. #CY-3592

                // We delete the data of each point type separately to avoid #CY-3859 parcel size error.
                deletePointsOfType(provider, authority, measurementSlice, geoLocationJsonMapper);
                deletePointsOfType(provider, authority, measurementSlice, accelerationJsonMapper);
                deletePointsOfType(provider, authority, measurementSlice, rotationJsonMapper);
                deletePointsOfType(provider, authority, measurementSlice, directionJsonMapper);
            } catch (final OperationApplicationException | RemoteException e) {
                throw new DatabaseException("Failed to apply the delete operation: " + e.getMessage(), e);
            }
        }
        return true;
    }

    /**
     * Inserts a batch of points into a measurement slice template for transmission.
     *
     * @param measurementSliceTemplate The measurement slice template with the context details
     * @param dataAccessLayer The layer to access the data
     * @param geoLocationStartIndex The index from which points should be inserted from
     * @param accelerationPointStartIndex The index from which points should be inserted from
     * @param rotationPointStartIndex The index from which points should be inserted from
     * @param directionPointStartIndex The index from which points should be inserted from
     * @param geoLocationJsonMapper The {@link JsonMapper} used to parse the points
     * @param accelerationJsonMapper The {@link JsonMapper} used to parse the points
     * @param rotationJsonMapper The {@link JsonMapper} used to parse the points
     * @param directionJsonMapper The {@link JsonMapper} used to parse the points
     * @return the {@link JSONObject} containing a filled measurement slice
     * @throws RequestParsingException When the data could not be inserted into the template.
     * @throws DatabaseException When the data could not be loaded from the persistence layer.
     */
    private JSONObject fillMeasurementSlice(final JSONObject measurementSliceTemplate,
            final MeasurementContentProviderClient dataAccessLayer, final int geoLocationStartIndex,
            final int accelerationPointStartIndex, final int rotationPointStartIndex,
            final int directionPointStartIndex, final GeoLocationJsonMapper geoLocationJsonMapper,
            final AccelerationJsonMapper accelerationJsonMapper, final RotationJsonMapper rotationJsonMapper,
            final DirectionJsonMapper directionJsonMapper) throws RequestParsingException, DatabaseException {
        Cursor geoLocationsCursor = null;
        Cursor accelerationsCursor = null;
        Cursor rotationsCursor = null;
        Cursor directionsCursor = null;
        try {
            geoLocationsCursor = dataAccessLayer.loadGeoLocations(geoLocationStartIndex,
                    geoLocationStartIndex + geoLocationsUploadBatchSize);
            final JSONArray geoLocationsJsonArray = transformToJsonArray(geoLocationsCursor, geoLocationJsonMapper);
            measurementSliceTemplate.put("gpsPoints", geoLocationsJsonArray);

            accelerationsCursor = dataAccessLayer.load3DPoint(new AccelerationsSerializer(),
                    accelerationPointStartIndex, accelerationPointStartIndex + accelerationsUploadBatchSize);
            final JSONArray accelerationPointsArray = transformToJsonArray(accelerationsCursor, accelerationJsonMapper);
            measurementSliceTemplate.put("accelerationPoints", accelerationPointsArray);

            rotationsCursor = dataAccessLayer.load3DPoint(new RotationsSerializer(), rotationPointStartIndex,
                    rotationPointStartIndex + rotationsUploadBatchSize);
            final JSONArray rotationPointsArray = transformToJsonArray(rotationsCursor, rotationJsonMapper);
            measurementSliceTemplate.put("rotationPoints", rotationPointsArray);

            directionsCursor = dataAccessLayer.load3DPoint(new DirectionsSerializer(), directionPointStartIndex,
                    directionPointStartIndex + directionsUploadBatchSize);
            final JSONArray directionPointsArray = transformToJsonArray(directionsCursor, directionJsonMapper);
            measurementSliceTemplate.put("directionPoints", directionPointsArray);
        } catch (final JSONException e) {
            throw new RequestParsingException("Failed to parse measurement data.", e);
        } catch (final RemoteException e) {
            throw new DatabaseException("Failed to fillMeasurementSlice: " + e.getMessage(), e);
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
     * Sync errors are broadcasted to the {@link de.cyface.utils.ErrorHandler}.
     *
     * @param syncResult The {@link SyncResult} used to store sync error information.
     * @param context The {@link Context} to access the {@link AccountManager}.
     * @param url The URL of the Cyface Data Collector API to post the data to.
     * @param account The {@link Account} used to post the data.
     * @param measurementSlice The measurement slice as {@link JSONObject}.
     * @throws RequestParsingException When the post request could not be generated or when data could not be parsed
     *             from the measurement slice.
     * @return True of the transmission was successful.
     */
    private boolean postMeasurementSlice(final SyncResult syncResult, final Context context, final String url,
            final Account account, JSONObject measurementSlice) throws RequestParsingException {
        try {
            final URL postUrl = new URL(http.returnUrlWithTrailingSlash(url) + "/measurements/");
            final String jwtBearer = AccountManager.get(context).blockingGetAuthToken(account,
                    Constants.AUTH_TOKEN_TYPE, false);
            HttpURLConnection con = null;
            try {
                con = http.openHttpConnection(postUrl, jwtBearer);
                Log.d(TAG, "Posing measurement slice ...");
                http.post(con, measurementSlice, true);
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
            return false;
        } catch (final MalformedURLException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode());
            return false;
        } catch (final AuthenticatorException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_ERROR.getCode());
            return false;
        } catch (final OperationCanceledException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, AUTHENTICATION_CANCELED.getCode());
            return false;
        } catch (final ResponseParsingException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, UNREADABLE_HTTP_RESPONSE.getCode());
            return false;
        } catch (final DataTransmissionException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, DATA_TRANSMISSION_ERROR.getCode(), e.getHttpStatusCode());
            return false;
        } catch (final SynchronisationException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode());
            return false;
        } catch (final IOException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, NETWORK_ERROR.getCode());
            return false;
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode());
            return false;
        }
        return true;
    }

    /**
     * Deletes the points of the type determined by the provided {@code jsonMapper} of the measurement slice.
     *
     * @param provider The provider to access the data.
     * @param authority The authority to access the data.
     * @param measurementSlice The measurement slice containing the data to be mark as synced.
     * @param jsonMapper The mapped to parse data of a specific type from the measurement slice. E.g.
     *            {@link GeoLocationJsonMapper}, {@link AccelerationJsonMapper}
     * @throws SynchronisationException When the data could not be mapped
     * @throws RemoteException When the delete operation failed
     * @throws OperationApplicationException When the delete operation failed
     */
    private void deletePointsOfType(final ContentProviderClient provider, final String authority,
            final JSONObject measurementSlice, final JsonMapper jsonMapper)
            throws SynchronisationException, RemoteException, OperationApplicationException {

        final ArrayList<ContentProviderOperation> deleteOperation = jsonMapper
                .buildDeleteDataPointsOperation(measurementSlice, authority);
        Log.d(TAG, String.format("Deleting %d points of type %s", deleteOperation.size(),
                jsonMapper.getClass().getSimpleName()));
        provider.applyBatch(deleteOperation);
    }

    /**
     * Prepares a measurement slice with the measurement context and details.
     *
     * @param measurementIdentifier The id of the measurement to prepare a slice of
     * @param unsyncedMeasurementsCursor The cursor to access the measurement
     * @param deviceIdentifier The device id
     * @return The prepared measurement slice template
     * @throws RequestParsingException When the measurement info could not be inserted into the template
     */
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

    private void addSyncProgressListener(final @NonNull ConnectionListener listener) {
        progressListener.add(listener);
    }

    private void notifySyncStarted(final long pointsToTransmit) {
        this.pointsToTransmit = pointsToTransmit;
        for (final ConnectionListener listener : progressListener) {
            listener.onSyncStarted(pointsToTransmit);
        }
    }

    private void notifySyncFinished() {
        this.pointsToTransmit = -1L;
        this.transmittedPoints = -1L;
        for (final ConnectionListener listener : progressListener) {
            listener.onSyncFinished();
        }
    }

    /**
     * Notifies about the sync progress.
     *
     * @param measurementSlice The {@link JSONObject} of the measurement slice transmitted.
     * @throws RequestParsingException when data could not be parsed from the measurement slice.
     */
    private void notifySyncProgress(final @NonNull JSONObject measurementSlice) throws RequestParsingException {
        final long measurementId;
        try {
            this.transmittedPoints += measurementSlice.getJSONArray("gpsPoints").length()
                    + measurementSlice.getJSONArray("directionPoints").length()
                    + measurementSlice.getJSONArray("rotationPoints").length()
                    + measurementSlice.getJSONArray("accelerationPoints").length();
            measurementId = measurementSlice.getLong("id");
        } catch (final JSONException e) {
            throw new RequestParsingException("Unable to parse measurement data", e);
        }

        for (final ConnectionListener listener : progressListener) {
            listener.onProgress(transmittedPoints, pointsToTransmit, measurementId);
        }
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
