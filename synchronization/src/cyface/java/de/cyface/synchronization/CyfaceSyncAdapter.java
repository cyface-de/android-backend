package de.cyface.synchronization;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import de.cyface.persistence.GpsPointsTable;
import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasurementTable;
import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.RotationPointTable;
import de.cyface.persistence.SamplePointTable;

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
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at
     *            {@link AbstractThreadedSyncAdapter#AbstractThreadedSyncAdapter(Context,
     *            boolean)}.
     */
    public CyfaceSyncAdapter(Context context, boolean autoInitialize) {
        this(context, autoInitialize, false);
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
     * @param account The user's android sync account which is needed to start a synchronization
     * @param extras not used
     * @param authority not used
     * @param provider a link to the content provider which is needed to access the data layer
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

                        URL postUrl = new URL(Http.returnUrlWithTrailingSlash(url) + "measurements/");
                        final String jwtBearer = AccountManager.get(context).blockingGetAuthToken(account,
                                CyfaceAuthenticator.AUTH_TOKEN_TYPE, false);
                        HttpURLConnection con = null;
                        try {
                            con = Http.openHttpConnection(postUrl, jwtBearer);
                            Http.post(con, measurementSlice, true);

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
     * When there is a login or sync error (both DCS API) this method generates a user friendly message
     * which can be used to inform the user about the problem.
     *
     * @param context The context where the error should be shown, usually a view context
     * @param resultExceptionType The name of the Exception returned by Exception.class.getSimpleName()
     * @param resultErrorMessage The error message returned by the DCS API
     * @return A string which contains a user-friendly error message
     */
    /*
     * public static String identifyTransmissionError(final Context context, final String resultExceptionType,
     * final String resultErrorMessage) {
     * String toastErrorMessage = context.getString(R.string.toast_error_message_login_failed); // Default message
     * // Exception identification
     * if (resultExceptionType.equals(MalformedJsonException.class.getSimpleName())) {
     * toastErrorMessage = context.getString(R.string.toast_error_message_server_unavailable);
     * } else if (resultExceptionType.equals(JSONException.class.getSimpleName())) {
     * toastErrorMessage = context.getString(R.string.toast_error_message_response_unreadable);
     * } else if (resultExceptionType.equals(DataTransmissionException.class.getSimpleName())) {
     * if (resultErrorMessage.contains(ERROR_MESSAGE_BAD_CREDENTIALS)) {
     * toastErrorMessage = context.getString(R.string.toast_error_message_credentials_incorrect);
     * } else if (resultErrorMessage.contains(ERROR_MESSAGE_SERVER_UNAVAILABLE)) {
     * toastErrorMessage = context.getString(R.string.toast_error_message_server_unavailable);
     * }
     * } else if (resultExceptionType.equals(RemoteException.class.getSimpleName())) {
     * toastErrorMessage = context.getString(R.string.toast_error_message_database_unaddressable);
     * }
     * return toastErrorMessage;
     * }
     */

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
