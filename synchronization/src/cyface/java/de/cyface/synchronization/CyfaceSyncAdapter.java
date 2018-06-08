package de.cyface.synchronization;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.MalformedJsonException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

import static android.content.Context.NOTIFICATION_SERVICE;

public final class CyfaceSyncAdapter extends AbstractThreadedSyncAdapter {

    public final static String NOTIFICATION_CHANNEL_ID_WARNING = "cyface_warnings";
    public final static String NOTIFICATION_CHANNEL_ID_RUNNING = "cyface_running"; // with cancel button
    public final static String NOTIFICATION_CHANNEL_ID_INFO = "cyface_info";
    public final static String SYNC_ERROR_MESSAGE_NO_UN_SYNCED_DATA = "no un-synced data available";
    private final static String TAG = "de.cyface.sync";
    private final static String ERROR_MESSAGE_BAD_CREDENTIALS = "Bad credentials";
    private static final String ERROR_MESSAGE_SERVER_UNAVAILABLE = "Error failed to connect to";
    /**
     * In order to reduce the amount of points loaded into the memory we load "slices" of
     * measurements which load a maximum of 10k points of each kind into memory (due to hardcoded
     * query limit of 10k). However, as the transmission of that amount of points takes a few
     * seconds, we split those slices into smaller "batches" so that the UI transmission progress is
     * updated more often, and thus, fluently.
     */
    private final static int TRANSMISSION_BATCH_SIZE = 7500;
    private final ExecutorService threadPool;
    private final Collection<SyncProgressListener> progressListener;

    /**
     * Creates a new completely initialized {@code CyfaceSyncAdapter}.
     *
     * @param context        The context this adapter is active under.
     * @param autoInitialize More details are available at
     *                       {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *                       boolean)}.
     */
    public CyfaceSyncAdapter(Context context, boolean autoInitialize) {
        this(context, autoInitialize, false);
    }

    /**
     * Creates a new completely initialized {@code CyfaceSyncAdapter}.
     *
     * @param context            The context this transmitter is active under.
     * @param autoInitialize     More details are available at
     *                           {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *                           boolean)}.
     * @param allowParallelSyncs More details are available at
     *                           {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *                           boolean, boolean)}.
     */
    public CyfaceSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);

        int availableCores = Runtime.getRuntime().availableProcessors() + 1;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(10);
        threadPool = new ThreadPoolExecutor(availableCores, availableCores, 1, TimeUnit.SECONDS, workQueue);
        progressListener = new HashSet<>();
        addSyncProgressListener(new CyfaceSyncProgressListener(context));
    }

    /**
     * Starts the sync process if non-synced data and wifi is available. This contains logging into
     * the server and starting the measurement transmission.
     *
     * @param account    The user's android sync account which is needed to start a synchronization
     * @param extras     not used
     * @param authority  not used
     * @param provider   a link to the content provider which is needed to access the data layer
     * @param syncResult used to check if the sync was successful
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
                              SyncResult syncResult) {
        final Context context = getContext();

        Log.d(TAG, "Sync started.");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceIdentifier = preferences.getString(SyncService.DEVICE_IDENTIFIER_KEY, null);
        if (deviceIdentifier == null) {
            Log.e(TAG, "Sync canceled: No installation identifier for this application set in its preferences.");
            return;
        }
        String url = preferences.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null);
        if (url == null) {
            Log.e(TAG, "Sync canceled: Server url not available. Please set the applications server url preference.");
            return;
        }

        try {
            Cursor unsyncedMeasurementsCursor = MeasurementContentProviderClient.loadSyncableMeasurements(provider);
            if (!unsyncedMeasurementsCursor.moveToFirst()) {
                Log.i(TAG, "Unable to sync data: " + SYNC_ERROR_MESSAGE_NO_UN_SYNCED_DATA);
                return;
            }
            notifySyncStarted(countUnsyncedDataPoints(provider, unsyncedMeasurementsCursor));

            GeoLocationJsonMapper geoLocationJsonMapper = new GeoLocationJsonMapper();
            AccelerationJsonMapper accelerationJsonMapper = new AccelerationJsonMapper();
            RotationJsonMapper rotationJsonMapper = new RotationJsonMapper();
            DirectionJsonMapper directionJsonMapper = new DirectionJsonMapper();

            do {
                long measurementIdentifier = unsyncedMeasurementsCursor
                        .getLong(unsyncedMeasurementsCursor.getColumnIndex(BaseColumns._ID));

                String measurementContext = unsyncedMeasurementsCursor
                        .getString(unsyncedMeasurementsCursor.getColumnIndex(MeasurementTable.COLUMN_VEHICLE));
                MeasurementContentProviderClient dataAccessLayer = new MeasurementContentProviderClient(
                        measurementIdentifier, provider);
                JSONObject measurementSlice = new JSONObject();
                measurementSlice.put("id", measurementIdentifier);
                measurementSlice.put("deviceId", deviceIdentifier);
                measurementSlice.put("vehicle", measurementContext);
                for (int g = 0, a = 0, r = 0, d = 0; g < dataAccessLayer
                        .countData(MeasuringPointsContentProvider.GPS_POINTS_URI, GpsPointsTable.COLUMN_MEASUREMENT_FK)
                        || a < dataAccessLayer.countData(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                        SamplePointTable.COLUMN_MEASUREMENT_FK)
                        || r < dataAccessLayer.countData(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                        RotationPointTable.COLUMN_MEASUREMENT_FK)
                        || d < dataAccessLayer.countData(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                        MagneticValuePointTable.COLUMN_MEASUREMENT_FK); g += Constants.GEO_LOCATIONS_UPLOAD_BATCH_SIZE, a += Constants.ACCELERATIONS_UPLOAD_BATCH_SIZE, r += Constants.ROTATIONS_UPLOAD_BATCH_SIZE, d += Constants.DIRECTIONS_UPLOAD_BATCH_SIZE) {
                    Cursor geoLocationsCursor = null;
                    Cursor accelerationsCursor = null;
                    Cursor rotationsCursor = null;
                    Cursor directionsCursor = null;
                    try {
                        geoLocationsCursor = dataAccessLayer.loadGeoLocations(g,
                                g + Constants.GEO_LOCATIONS_UPLOAD_BATCH_SIZE);
                        JSONArray geoLocationsJsonArray = transformToJsonArray(geoLocationsCursor,
                                geoLocationJsonMapper);
                        measurementSlice.put("gpsPoints", geoLocationsJsonArray);

                        accelerationsCursor = dataAccessLayer.load3DPoint(new AccelerationsSerializer(), a,
                                a + Constants.ACCELERATIONS_UPLOAD_BATCH_SIZE);
                        JSONArray accelerationPointsArray = transformToJsonArray(accelerationsCursor,
                                accelerationJsonMapper);
                        measurementSlice.put("accelerationPoints", accelerationPointsArray);

                        rotationsCursor = dataAccessLayer.load3DPoint(new RotationsSerializer(), r,
                                r + Constants.ROTATIONS_UPLOAD_BATCH_SIZE);
                        JSONArray rotationPointsArray = transformToJsonArray(rotationsCursor, rotationJsonMapper);
                        measurementSlice.put("rotationPoints", rotationPointsArray);

                        directionsCursor = dataAccessLayer.load3DPoint(new DirectionsSerializer(), d,
                                d + Constants.DIRECTIONS_UPLOAD_BATCH_SIZE);
                        JSONArray magneticValuePointsArray = transformToJsonArray(directionsCursor,
                                directionJsonMapper);
                        measurementSlice.put("magneticValuePoints", magneticValuePointsArray);

                        URL postUrl = new URL(returnUrlWithTrailingSlash(url) + "measurements/");
                        final String jwtBearer = AccountManager.get(context).blockingGetAuthToken(account,
                                CyfaceAuthenticator.AUTH_TOKEN_TYPE, false);
                        HttpURLConnection con = null;
                        try {
                            con = openHttpConnection(postUrl, jwtBearer);
                            post(con, measurementSlice, true);

                            ArrayList<ContentProviderOperation> markAsSyncedOperation = new ArrayList<>();
                            markAsSyncedOperation
                                    .addAll(geoLocationJsonMapper.buildMarkSyncedOperation(measurementSlice));
                            markAsSyncedOperation
                                    .addAll(accelerationJsonMapper.buildMarkSyncedOperation(measurementSlice));
                            markAsSyncedOperation.addAll(rotationJsonMapper.buildMarkSyncedOperation(measurementSlice));
                            markAsSyncedOperation
                                    .addAll(directionJsonMapper.buildMarkSyncedOperation(measurementSlice));
                            provider.applyBatch(markAsSyncedOperation);
                        } catch (OperationApplicationException e) {
                            Log.e(TAG, "Unable to sync data.", e);
                            notifySyncTransmitError(e.getMessage(), e);
                        } finally {
                            if (con != null) {
                                con.disconnect();
                            }
                        }

                        notifySyncProgress(measurementSlice);
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
                }
            } while (unsyncedMeasurementsCursor.moveToNext());

            // TODO: Move this to the authenticator.
            // initSync(username, password, installationIdentifier, url, getContext());

        } catch (MalformedURLException e) {
            syncResult.stats.numParseExceptions++;
            Log.e(TAG, "Unable to sync data.", e);
            notifySyncTransmitError(String.format(context.getString(R.string.error_message_url_parsing), url), e);
        } catch (OperationCanceledException | AuthenticatorException | SynchronisationException
                | DataTransmissionException | IllegalStateException e) {
            syncResult.stats.numAuthExceptions++;
            Log.e(TAG, "Unable to sync data.", e);
            notifySyncTransmitError(e.getMessage(), e);
        } catch (JSONException | IOException e) {
            syncResult.stats.numParseExceptions++;
            Log.e(TAG, "Unable to sync data.", e);
            notifySyncTransmitError(context.getString(R.string.error_message_json_parsing), e);
        } catch (RemoteException e) {
            syncResult.databaseError = true;
            Log.e(TAG, "Unable to sync data.", e);
            notifySyncReadError(context.getString(R.string.error_message_database), e);
        } finally {
            Log.d(TAG, String.format("Sync finished. (error: %b)", syncResult.hasError()));
            notifySyncFinished();
        }
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

    private void notifySyncReadError(final @NonNull String errorMessage, final Throwable errorType) {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncReadError(errorMessage, errorType);
        }
    }

    private void notifySyncTransmitError(final @NonNull String errorMessage, final Throwable errorType) {
        for (SyncProgressListener listener : progressListener) {
            listener.onSyncTransmitError(errorMessage, errorType);
        }
    }

    private void notifySyncProgress(final @NonNull JSONObject measurementSlice) throws JSONException {
        for (SyncProgressListener listener : progressListener) {
            listener.onProgress(measurementSlice);
        }
    }

    private long countUnsyncedDataPoints(final @NonNull ContentProviderClient provider,
                                         final @NonNull Cursor syncableMeasurements) throws RemoteException {
        long ret = 0L;
        while (syncableMeasurements.moveToNext()) {
            long measurementIdentifier = syncableMeasurements
                    .getLong(syncableMeasurements.getColumnIndex(BaseColumns._ID));
            MeasurementContentProviderClient client = new MeasurementContentProviderClient(measurementIdentifier,
                    provider);

            ret += client.countData(MeasuringPointsContentProvider.GPS_POINTS_URI,
                    GpsPointsTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(MeasuringPointsContentProvider.SAMPLE_POINTS_URI,
                    SamplePointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(MeasuringPointsContentProvider.ROTATION_POINTS_URI,
                    RotationPointTable.COLUMN_MEASUREMENT_FK);
            ret += client.countData(MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI,
                    MagneticValuePointTable.COLUMN_MEASUREMENT_FK);
        }
        return ret;
    }

    /**
     * Initializes the synchronisation by logging in to the server and creating this device if
     * necessary.
     *
     * @param username               The username that is used by the application to login to the
     *                               server.
     * @param password               The password belonging to the account with the {@code username}
     *                               logging in to the Cyface server.
     * @param installationIdentifier The world wide unique identifier of this application
     *                               installation. This is required to save the context the
     *                               application runs with, such as smartphone type, vehicle type or
     *                               type of mount.
     * @param url                    The Cyface server URL to initialize the synchronisation with.
     * @throws SynchronisationException If there are any communication failures.
     * @throws MalformedURLException    If the used server URL is not well formed.
     * @throws JSONException            Thrown if the returned JSON message is not parsable.
     */
    public static void initSync(final @NonNull String username, final @NonNull String password,
                                final @NonNull String installationIdentifier, final @NonNull String url, final @NonNull Context context)
            throws SynchronisationException, MalformedURLException, JSONException {
        try {
            final Device device = new Device(installationIdentifier, Build.DEVICE);
            // Don't write password into log!
            Log.d(TAG, "Authenticating at " + url + " as " + username);

            // Login to get JWT token
            JSONObject loginPayload = new JSONObject();
            loginPayload.put("login", username);
            loginPayload.put("password", password);
            final HttpURLConnection connection = openHttpConnection(new URL(returnUrlWithTrailingSlash(url) + "login"),
                    null, false);
            final HttpResponse loginResponse = post(connection, loginPayload, false);
            connection.disconnect();
            if (loginResponse.is2xxSuccessful() && connection.getHeaderField("Authorization") == null) {
                throw new IllegalStateException("Login successful but response does not contain a token");
            }
            final String jwtBearer = connection.getHeaderField("Authorization");
            final AppPreferences appPreferences = new AppPreferences(context);
            appPreferences.put(context.getString(R.string.jwt_bearer_key), jwtBearer);

            // Register device
            final HttpURLConnection con = openHttpConnection(new URL(returnUrlWithTrailingSlash(url) + "devices/"),
                    jwtBearer, true);
            final HttpResponse registerDeviceResponse = post(con, device.toJson(), false);
            con.disconnect();

            if (registerDeviceResponse.is2xxSuccessful() && !registerDeviceResponse.getBody().isNull("errorName")
                    && registerDeviceResponse.getBody().get("errorName").equals("Duplicate Device")) {
                Log.w(TAG,
                        String.format(context.getString(R.string.error_message_device_exists), installationIdentifier));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BufferedOutputStream initOutputStream(HttpURLConnection con, boolean compress)
            throws SynchronisationException {
        if (compress) {
            con.setRequestProperty("Content-Encoding", "gzip");
        }
        con.setChunkedStreamingMode(0);
        con.setDoOutput(true);
        try {
            return new BufferedOutputStream(con.getOutputStream());
        } catch (IOException e) {
            throw new SynchronisationException(String.format(
                    "OutputStream failed: Error %s. Unable to create new data output for the http connection.",
                    e.getMessage()), e);
        }
    }

    /**
     * Parses the JSON response from a connection and includes error handling for non 2XX status
     * codes.
     *
     * @param con The connection that received the response.
     * @return A parsed {@link HttpResponse} object.
     * @throws DataTransmissionException If the response is no successful HTTP response (i.e. no 2XX
     *                                   status code).
     * @throws SynchronisationException  If the system fails in handling the HTTP response.
     */
    private static HttpResponse readResponse(final @NonNull HttpURLConnection con)
            throws DataTransmissionException, SynchronisationException {

        StringBuilder responseString = new StringBuilder();
        HttpResponse response;
        try {
            // We need to read the status code first, as a response with an error might not contain
            // a response body but an error response body. This caused "response not readable" on Xpedia Z5 6.0.1
            // int status = con.getResponseCode();
            try { // if (status >= 400 && status <= 600) {
                BufferedReader er = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String errorLine;
                while ((errorLine = er.readLine()) != null) {
                    responseString.append(errorLine);
                }
                er.close();
            } catch (NullPointerException e) { // } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseString.append(inputLine);
                }
                in.close();
            }
            response = new HttpResponse(con.getResponseCode(), responseString.toString());
            if (response.is2xxSuccessful()) {
                return response;
            } else {
                if (response.getBody().has("errorName")) {
                    throw new DataTransmissionException(response.getResponseCode(),
                            response.getBody().getString("errorName"), response.getBody().getString("errorMessage"));
                } else if (response.getBody().has("exception") && response.getBody().has("error")
                        && response.getBody().has("message")) {
                    throw new DataTransmissionException(response.getResponseCode(),
                            response.getBody().getString("exception"),
                            response.getBody().getString("error") + ": " + response.getBody().getString("message"));
                } else {
                    throw new DataTransmissionException(response.getResponseCode(), "unknown response attributes",
                            response.getBody().toString());
                }
            }
        } catch (IOException e) {
            throw new SynchronisationException(String.format(
                    "Invalid http response: Error: '%s'. Unable to read the http response.", e.getMessage()), e);
        } catch (JSONException e) {
            throw new SynchronisationException(
                    String.format("Json Parsing failed: Error: '%s'. Unable to parse http response to json: %s",
                            e.getMessage(), responseString),
                    e);
        }
    }

    /**
     * A HTTPConnection must be opened with the right header before you can communicate with the Cyface REST API
     *
     * @param url       The URL of the cyface backend's REST API.
     * @param jwtBearer A String in the format "Bearer TOKEN".
     * @return the HTTPURLConnection
     * @throws DataTransmissionException when no server is at that URL
     */
    private static HttpURLConnection openHttpConnection(final @NonNull URL url, final @NonNull String jwtBearer)
            throws DataTransmissionException {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("Authorization", jwtBearer);
            con.setConnectTimeout(5000);
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", System.getProperty("http.agent"));
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            return con;
        } catch (IOException e) {
            throw new DataTransmissionException(0, "No valid server",
                    String.format("Error %s. There seems to be no server at %s.", e.getMessage(), url.toString()), e);
        }
    }

    /**
     * The compressed post request which transmits a measurement batch through an existing http
     * connection
     *
     * @param payload The measurement batch in json format
     * @param <T>     Json string
     * @throws DataTransmissionException When the server is not reachable or the connection was
     *                                   interrupted.
     * @throws SynchronisationException  If the system is unable to handle the HTTP response.
     */
    private static <T> HttpResponse post(final HttpURLConnection con, final T payload, boolean compress)
            throws DataTransmissionException, SynchronisationException {

        BufferedOutputStream os = initOutputStream(con, compress);
        try {
            Log.d(TAG, "Transmitting with compression " + compress + ".");
            if (compress) {
                os.write(gzip(payload.toString().getBytes("UTF-8")));
            } else {
                os.write(payload.toString().getBytes("UTF-8"));
            }
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new DataTransmissionException(0, "Parsing failed",
                    String.format("Error %s. Unable to parse http request or response.", e.getMessage()), e);
        }
        return readResponse(con);
    }

    private static byte[] gzip(byte[] input) {
        GZIPOutputStream gzipOutputStream = null;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gzipOutputStream.write(input);
            gzipOutputStream.flush();
            gzipOutputStream.close();
            gzipOutputStream = null;
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (gzipOutputStream != null) {
                try {
                    gzipOutputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Adds a trailing slash to the server URL or leaves an existing trailing slash untouched.
     *
     * @param url The url to format.
     * @return The server URL with a trailing slash.
     */
    private static String returnUrlWithTrailingSlash(final String url) {
        if (url.endsWith("/")) {
            return url;
        } else {
            return url + "/";
        }
    }

    /**
     * When there is a login or sync error (both DCS API) this method generates a user friendly message
     * which can be used to inform the user about the problem.
     *
     * @param context             The context where the error should be shown, usually a view context
     * @param resultExceptionType The name of the Exception returned by Exception.class.getSimpleName()
     * @param resultErrorMessage  The error message returned by the DCS API
     * @return A string which contains a user-friendly error message
     */
    public static String identifyTransmissionError(final Context context, final String resultExceptionType,
                                                   final String resultErrorMessage) {
        String toastErrorMessage = context.getString(R.string.toast_error_message_login_failed); // Default message

        // Exception identification
        if (resultExceptionType.equals(MalformedJsonException.class.getSimpleName())) {
            toastErrorMessage = context.getString(R.string.toast_error_message_server_unavailable);
        } else if (resultExceptionType.equals(JSONException.class.getSimpleName())) {
            toastErrorMessage = context.getString(R.string.toast_error_message_response_unreadable);
        } else if (resultExceptionType.equals(DataTransmissionException.class.getSimpleName())) {
            if (resultErrorMessage.contains(ERROR_MESSAGE_BAD_CREDENTIALS)) {
                toastErrorMessage = context.getString(R.string.toast_error_message_credentials_incorrect);
            } else if (resultErrorMessage.contains(ERROR_MESSAGE_SERVER_UNAVAILABLE)) {
                toastErrorMessage = context.getString(R.string.toast_error_message_server_unavailable);
            }
        } else if (resultExceptionType.equals(RemoteException.class.getSimpleName())) {
            toastErrorMessage = context.getString(R.string.toast_error_message_database_unaddressable);
        }
        return toastErrorMessage;
    }

    /*
     * private boolean synchronizationDisabled(AppPreferences appPreferences) {
     * return !appPreferences.getBoolean(getContext().getString(R.string.setting_synchronization_key), true);
     * }
     */

    // TODO: Check where to decide whether to clean data or to delete in Movebis code.
    /**
     * When no unsynced data is available: Delete measurements which can be left over when sync
     * completed without transmissionResultInfo (e.g. finished sync with err)
     */
    /*
     * private void cleanUpMeasurementEntries(DataAccessLayer dataAccessLayer) throws RemoteException {
     * try {
     * long largestMeasurementId = dataAccessLayer.getLargestMeasurementId();
     * SyncedMeasurementsDeleter deleter = new SyncedMeasurementsDeleter(dataAccessLayer,
     * new TransmitNextUnSyncedMeasurementResult(0, largestMeasurementId));
     * threadPool.execute(deleter);
     * } catch (IllegalStateException e) {
     * if (!e.getMessage().equals(NO_UNSYCHRONIZED_DATA_AVAILABLE_MESSAGE)) {
     * throw new IllegalStateException(e);
     * } // else: No fully synced measurements available to delete - nothing to do here.
     * }
     * }
     */

    /**
     * Transmits all unSynced measurements to the Cyface server when Wi-Fi network is available.
     * Synced measurements are deleted after synchronization.
     *
     * @param username The username that is used by the application to login to the server.
     * @param password The password belonging to the account with the {@code username} logging in to
     *            the Cyface server.
     * @param url The Cyface server URL to initialize the synchronisation with.
     * @throws DataTransmissionException If there are any communication failures.
     * @throws RemoteException If there are problems accessing the underlying {@link
     *             DataAccessLayer}.
     * @throws MalformedURLException If the used server URL is not well formed.
     */
    /*
     * public void transmitMeasurements(final String username, final String password, final String url)
     * throws RemoteException, MalformedURLException, DataTransmissionException {
     * TransmitNextUnSyncedMeasurementResult transmitResult = null;
     * final AppPreferences appPreferences = new AppPreferences(getContext());
     * long transmittedPoints = appPreferences.getLong(getContext().getString(R.string.transmitted_points), 0L);
     * long pointsToBeTransmitted = appPreferences.getLong(getContext().getString(R.string.point_to_be_transmitted),
     * 1L);
     * long unSyncedPoints = dataAccessLayer.countUnSyncedPoints();
     * if (pointsToBeTransmitted < unSyncedPoints) {
     * pointsToBeTransmitted = unSyncedPoints;
     * new SyncProgressHelper(getContext()).updatePointsToBeTransmitted(pointsToBeTransmitted, transmittedPoints);
     * }
     * try {
     * while (isConnectedToWifi() && dataAccessLayer.hasUnSyncedData()) {
     * transmitResult = transmitNextUnSyncedMeasurement(url, transmittedPoints, pointsToBeTransmitted);
     * if (transmitResult != null) {
     * transmittedPoints = transmitResult.transmittedPoints;
     * }
     * if (transmitResult == null || transmitResult.transmittedPoints == 0) {
     * break;
     * }
     * }
     * // By checking wifi connection we avoid another time consuming hasUnSyncedData() execution when the wifi
     * // connection was interrupted
     * if (isConnectedToWifi() && !dataAccessLayer.hasUnSyncedData()) {
     * new SyncProgressHelper(getContext()).resetProgress();
     * }
     * } catch (IllegalStateException e) {
     * if (!e.getMessage().equals(NO_UNSYCHRONIZED_DATA_AVAILABLE_MESSAGE)) {
     * throw new IllegalStateException(e);
     * } else {
     * Log.d(TAG, "transmitNextUnSyncedMeasurement canceled: no next measurement available to sync");
     * }
     * } catch (IOException e) {
     * throw new DataTransmissionException(0, "DataOutputStream failed",
     * String.format("Error %s. Unable to close DataOutputStream for http connection.", e.getMessage()),
     * e);
     * } finally {
     * SyncedMeasurementsDeleter deleter = new SyncedMeasurementsDeleter(dataAccessLayer, transmitResult);
     * threadPool.execute(deleter);
     * if (!dataAccessLayer.hasUnSyncedData()) {
     * cleanUpMeasurementEntries(dataAccessLayer);
     * createUploadSuccessfulNotification();
     * }
     * }
     * }
     */

    /**
     * <p>
     * Transmits the next unSynced measurement to the Cyface server for storage.
     * </p>
     *
     * @param transmittedPoints The number of so-far-transmitted points which is needed to send
     *            the SYNC_PROGRESS intend.
     * @param pointsToBeTransmitted The number of unSynced points at the beginning of the sync
     *            process which is needed to send the SYNC_PROGRESS intend.
     * @throws DataTransmissionException If there are any communication failures.
     * @throws MalformedURLException If the used server URL is not well formed.
     * @throws RemoteException If there are problems accessing the underlying {@link
     *             DataAccessLayer}.
     */
    /*
     * private TransmitNextUnSyncedMeasurementResult transmitNextUnSyncedMeasurement(final String url,
     * long transmittedPoints, long pointsToBeTransmitted)
     * throws DataTransmissionException, MalformedURLException, RemoteException, IllegalStateException {
     * Intent syncInProgressIntent;
     * long measurementIdentifier;
     * // Transmit measurement slices (due to hardcoded query limit) as long as this measurement is considered
     * // unSynced (i.e. has unSynced points)
     * measurementIdentifier = dataAccessLayer.getIdOfNextUnSyncedMeasurement();
     * while (dataAccessLayer.getIdOfNextUnSyncedMeasurement() == measurementIdentifier && isConnectedToWifi()) {
     * Log.d(TAG, "Preparing to transmit up to ~30k of " + pointsToBeTransmitted
     * + " unSynced points of measurement " + measurementIdentifier);
     * Measurement measurement_slice = dataAccessLayer.loadNextUnSyncedMeasurementSlice(measurementIdentifier);
     * List<GpsPoint> gpsPoints = new LinkedList<>(measurement_slice.getGpsPoints());
     * List<Point3D> samplePoints = new LinkedList<>(measurement_slice.getSamplePoints());
     * List<Point3D> rotationPoints = new LinkedList<>(measurement_slice.getRotationPoints());
     * List<Point3D> magneticValuePoints = new LinkedList<>(measurement_slice.getMagneticValuePoints());
     * // Transmission of loaded measurement slice in TRANSMISSION_BATCH_SIZE large parcels (for faster UI
     * // progress updates)
     * Measurement measurement_batch;
     * for (measurement_batch = loadNextMeasurementBatch(TRANSMISSION_BATCH_SIZE, measurement_slice.getId(),
     * measurement_slice.getVehicle(), gpsPoints, samplePoints, rotationPoints,
     * magneticValuePoints); measurement_batch != null; measurement_batch = loadNextMeasurementBatch(
     * TRANSMISSION_BATCH_SIZE, measurement_slice.getId(), measurement_slice.getVehicle(),
     * gpsPoints, samplePoints, rotationPoints, magneticValuePoints)) {
     * if (!isConnectedToWifi())
     * break;
     * Log.d(TAG,
     * "Posting batch (" + measurement_batch.getGpsPoints().size() + "/"
     * + measurement_batch.getSamplePoints().size() + "/"
     * + measurement_batch.getRotationPoints().size() + "/"
     * + measurement_batch.getMagneticValuePoints().size() + ")");
     * URL postUrl = new URL(returnUrlWithTrailingSlash(url) + "measurements/");
     * final AppPreferences appPreferences = new AppPreferences(getContext());
     * final String jwtBearer = appPreferences.getString(getContext().getString(R.string.jwt_bearer_key),
     * null);
     * HttpURLConnection con = openHttpConnection(postUrl, jwtBearer, true);
     * try {
     * post(con, measurement_batch.toJson(), true);
     * } finally {
     * // Making sure that no points are transmitted twice if the programs stops for unexpected reasons
     * dataAccessLayer.markSynced(measurement_batch);
     * con.disconnect();
     * }
     * transmittedPoints += measurement_batch.getGpsPoints().size()
     * + measurement_batch.getMagneticValuePoints().size()
     * + measurement_batch.getRotationPoints().size() + measurement_batch.getSamplePoints().size();
     * syncInProgressIntent = new Intent(SYNC_PROGRESS);
     * syncInProgressIntent.putExtra(SYNC_PROGRESS_TRANSMITTED, transmittedPoints);
     * syncInProgressIntent.putExtra(SYNC_PROGRESS_TOTAL, pointsToBeTransmitted);
     * getContext().sendBroadcast(syncInProgressIntent);
     * new SyncProgressHelper(getContext()).updateProgress(transmittedPoints);
     * }
     * if (transmittedPoints >= pointsToBeTransmitted)
     * break;
     * }
     * return new TransmitNextUnSyncedMeasurementResult(transmittedPoints, measurementIdentifier);
     * }
     */

    /**
     * <p>
     * Builds a measurement which contains a batch of the points of the original measurement.
     * This way we can transmit measurements in smaller parts.
     * </p>
     *
     * @param batchSize The maximal number of points the batch may contain
     * @param mId the id of the original measurement
     * @param gpsPoints the currently remaining gps points of the original measurement
     *            which still have to be transmitted
     * @param samplePoints the currently remaining acceleration points of the original
     *            measurement which still have to be transmitted
     * @param rotationPoints the currently remaining rotation points of the original
     *            measurement which still have to be transmitted
     * @param magneticValuePoints the currently remaining magnetic value points of the original
     *            measurement which still have to be transmitted
     * @return the measurement batch
     */
    /*
     * private Measurement loadNextMeasurementBatch(final int batchSize, long mId, Vehicle vehicle,
     * List<GpsPoint> gpsPoints, List<Point3D> samplePoints, List<Point3D> rotationPoints,
     * List<Point3D> magneticValuePoints) {
     * if (gpsPoints.isEmpty() && samplePoints.isEmpty() && magneticValuePoints.isEmpty()
     * && rotationPoints.isEmpty()) {
     * return null;
     * }
     * // Build a new measurement.
     * final AppPreferences appPreferences = new AppPreferences(getContext());
     * final String installationIdentifier = appPreferences
     * .getString(getContext().getString(de.cynav.capturing.R.string.installation_identifier_key), null);
     * Measurement measurement_batch = new Measurement(installationIdentifier, mId, vehicle);
     * int already_filled = 0;
     * // Fill one batch with points
     * GpsPoint gpsP;
     * Point3D mp, rp, sp;
     * while (!gpsPoints.isEmpty() && already_filled < batchSize) {
     * gpsP = gpsPoints.listIterator().next();
     * measurement_batch.addGpsPoint(gpsP);
     * gpsPoints.remove(gpsP);
     * already_filled++;
     * }
     * while (!magneticValuePoints.isEmpty() && already_filled < batchSize) {
     * mp = magneticValuePoints.listIterator().next();
     * measurement_batch.addMagneticValuePoint(mp);
     * magneticValuePoints.remove(mp);
     * already_filled++;
     * }
     * while (!rotationPoints.isEmpty() && already_filled < batchSize) {
     * rp = rotationPoints.listIterator().next();
     * measurement_batch.addRotationPoint(rp);
     * rotationPoints.remove(rp);
     * already_filled++;
     * }
     * while (!samplePoints.isEmpty() && already_filled < batchSize) {
     * sp = samplePoints.listIterator().next();
     * measurement_batch.addSamplePoint(sp);
     * samplePoints.remove(sp);
     * already_filled++;
     * }
     * return measurement_batch;
     * }
     */

    // TODO: When should this happen? Check with old code!
    /*
     * private void createUploadSuccessfulNotification() {
     * // Open Activity when the notification is clicked
     * Intent onClickIntent = new Intent();
     * onClickIntent
     * .setComponent(new ComponentName("de.cyface.planer.client", "de.cyface.planer.client.ui.MainActivity"));
     * PendingIntent onClickPendingIntent = PendingIntent.getActivity(getContext(), 0, onClickIntent,
     * PendingIntent.FLAG_UPDATE_CURRENT);
     * Notification notification = new NotificationCompat.Builder(getContext()).setContentIntent(onClickPendingIntent)
     * .setSmallIcon(de.cynav.capturing.R.drawable.ic_logo_only_c)
     * .setContentTitle(getContext().getString(R.string.upload_successful))
     * .setChannelId(NOTIFICATION_CHANNEL_ID_INFO).setOngoing(false).setWhen(System.currentTimeMillis())
     * .setAutoCancel(true).build();
     * NotificationManager notificationManager = (NotificationManager)getContext()
     * .getSystemService(NOTIFICATION_SERVICE);
     * notificationManager.notify(CAPTURING_ONGOING_NOTIFICATION_ID, notification);
     * }
     */

    /**
     * <p>
     * Internal value object class for the attributes of an HTTP response. It wrappers the HTTP
     * status code as well as a JSON body object.
     * </p>
     */
    private static class HttpResponse {
        private int responseCode;
        private JSONObject body;

        HttpResponse(int responseCode, String responseBodyAsString) throws JSONException {
            this.responseCode = responseCode;
            try {
                this.body = new JSONObject(responseBodyAsString);
            } catch (JSONException e) {
                if (is2xxSuccessful()) {
                    this.body = null; // this is expected, continue.
                } else {
                    throw new JSONException("Empty response body for unsuccessful response (code " + responseCode
                            + "): " + e.getMessage());
                }
            }
        }

        JSONObject getBody() {
            return body;
        }

        int getResponseCode() {
            return responseCode;
        }

        boolean is2xxSuccessful() {
            return (responseCode >= 200 && responseCode <= 300);
        }
    }

    /*
     * private static class SyncedMeasurementsDeleter implements Runnable {
     * private final DataAccessLayer dataAccessLayer;
     * private final TransmitNextUnSyncedMeasurementResult transmissionResultInfo;
     * SyncedMeasurementsDeleter(final DataAccessLayer dataAccessLayer,
     * final TransmitNextUnSyncedMeasurementResult transmissionResultInfo) {
     * this.dataAccessLayer = dataAccessLayer;
     * this.transmissionResultInfo = transmissionResultInfo;
     * }
     * @Override
     * public void run() {
     * try {
     * long deleted_p = dataAccessLayer.deleteSyncedMeasurementPoints();
     * long deleted_m = 0;
     * if (transmissionResultInfo != null) {
     * deleted_m = dataAccessLayer.deleteSyncedMeasurements(transmissionResultInfo.measurementIdentifier);
     * } else {
     * Log.v(TAG, "transmissionResultInfo was null, thus, no fully synced measurements are deleted");
     * }
     * if (deleted_p > 0 || deleted_m > 0) {
     * Log.d(TAG, "deleted " + deleted_p + " synced points"
     * + (deleted_m > 0 ? " and " + deleted_m + " fully synced measurements" : ""));
     * }
     * } catch (RemoteException e) {
     * Log.w(TAG, "Unable to delete synced points.", e);
     * }
     * }
     * }
     */

    // TODO: Do we still need this?
    /*
     * private class TransmitNextUnSyncedMeasurementResult {
     * private long transmittedPoints;
     * private long measurementIdentifier;
     * TransmitNextUnSyncedMeasurementResult(long transmittedPoints, long measurementIdentifier) {
     * this.transmittedPoints = transmittedPoints;
     * this.measurementIdentifier = measurementIdentifier;
     * }
     * }
     */
}
