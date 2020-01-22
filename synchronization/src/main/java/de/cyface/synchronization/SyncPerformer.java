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

import static de.cyface.synchronization.Constants.TAG;
import static de.cyface.synchronization.CyfaceAuthenticator.loadSslContext;
import static de.cyface.synchronization.ErrorHandler.sendErrorIntent;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.BAD_REQUEST;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.ENTITY_NOT_PARSABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.FORBIDDEN;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.INTERNAL_SERVER_ERROR;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.MALFORMED_URL;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.NETWORK_UNAVAILABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.SERVER_UNAVAILABLE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.SYNCHRONIZATION_ERROR;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.SYNCHRONIZATION_INTERRUPTED;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.TOO_MANY_REQUESTS;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UNAUTHORIZED;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.SSLContext;

import android.content.Context;
import android.content.SyncResult;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.Constants;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Measurement;

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.2
 * @since 2.0.0
 */
class SyncPerformer {

    /**
     * Socket Factory required to communicate with the Cyface Server when using a self signed certificate issued by that
     * server. Further details are available in the
     * <a href="https://developer.android.com/training/articles/security-ssl.html#UnknownCa">Android documentation</a>
     * and for example <a href=
     * "https://stackoverflow.com/questions/24555890/using-a-custom-truststore-in-java-as-well-as-the-default-one">here</a>.
     */
    private SSLContext sslContext;
    private Context context;

    /**
     * Creates a new completely initialized <code>SyncPerformer</code> for a given Android <code>Context</code>.
     *
     * @param context The Android <code>Context</code> to use for setting the correct server certification information.
     */
    SyncPerformer(final @NonNull Context context) {
        this.context = context;

        // Load SSLContext
        try {
            sslContext = loadSslContext(context);
        } catch (final IOException e) {
            throw new IllegalStateException("Trust store file failed while closing", e);
        } catch (final SynchronisationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Triggers the data transmission to a Cyface server API. The <code>measurementIdentifier</code> and
     * <code>deviceIdentifier</code> need to be globally unique. If they are not the server will probably reject the
     * request.
     * <p>
     * Sync errors are broadcasted to the {@link ErrorHandler}.
     * <p>
     * Since this is a synchronous call it can take from seconds to minutes depending on the size of <code>data</code>.
     * Never call this on the UI thread. Your users are going to hate you.
     *
     * @param http The {@link Http} connection to use for transmission
     * @param syncResult The {@link SyncResult} used to store sync error information.
     * @param dataServerUrl The server URL to send the data to.
     * @param metaData The {@link SyncAdapter.MetaData} required for the Multipart request.
     * @param compressedTransferTempFile The {@link Measurement} data to transmit
     * @param compressedEventsTransferTempFile The {@link Event} data of the {@link Measurement} to transmit
     * @param progressListener The {@link UploadProgressListener} to be informed about the upload progress.
     * @param jwtAuthToken A valid JWT auth token to authenticate the transmission
     * @return True of the transmission was successful.
     */
    boolean sendData(@NonNull final Http http, @NonNull final SyncResult syncResult,
            @NonNull final String dataServerUrl, @NonNull final SyncAdapter.MetaData metaData,
            @NonNull final File compressedTransferTempFile, @NonNull final File compressedEventsTransferTempFile,
            @NonNull final UploadProgressListener progressListener,
            @NonNull final String jwtAuthToken) {

        Log.d(Constants.TAG, String.format("Transferring compressed measurement (%s)",
                DefaultFileAccess.humanReadableByteCount(compressedTransferTempFile.length(), true)));
        Log.d(Constants.TAG, String.format("Transferring compressed events (%s)",
                DefaultFileAccess.humanReadableByteCount(compressedEventsTransferTempFile.length(), true)));
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection connection = null;
        final String fileName = String.format(Locale.US, "%s_%d." + Constants.TRANSFER_FILE_EXTENSION,
                metaData.deviceId, metaData.measurementId);
        final String eventsFileName = String.format(Locale.US, "%s_%d." + Constants.EVENTS_TRANSFER_FILE_EXTENSION,
                metaData.deviceId, metaData.measurementId);

        try {
            final URL url = new URL(String.format("%s/measurements", dataServerUrl));
            Log.i(TAG, String.format(Locale.GERMAN, "Uploading %s and %s to %s", fileName, eventsFileName,
                    url.toString()));
            try {
                connection = http.openHttpConnection(url, sslContext, true, jwtAuthToken);
                http.post(connection, compressedTransferTempFile, compressedEventsTransferTempFile, metaData, fileName,
                        eventsFileName, progressListener);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } catch (final ServerUnavailableException e) {
            // The SyncResults come from Android and help the SyncAdapter to re-schedule the sync
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SERVER_UNAVAILABLE.getCode(), e.getMessage());
            return false;
        } catch (final ForbiddenException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, FORBIDDEN.getCode(), e.getMessage());
            return false;
        } catch (final MalformedURLException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode(), e.getMessage());
            return false;
        } catch (final SynchronisationException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
            return false;
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode(), e.getMessage());
            return false;
        } catch (final InternalServerErrorException e) {
            syncResult.stats.numConflictDetectedExceptions++;
            sendErrorIntent(context, INTERNAL_SERVER_ERROR.getCode(), e.getMessage());
            return false;
        } catch (final EntityNotParsableException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, ENTITY_NOT_PARSABLE.getCode(), e.getMessage());
            return false;
        } catch (final BadRequestException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, BAD_REQUEST.getCode(), e.getMessage());
            return false;
        } catch (final NetworkUnavailableException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, NETWORK_UNAVAILABLE.getCode(), e.getMessage());
            return false;
        } catch (final SynchronizationInterruptedException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_INTERRUPTED.getCode(), e.getMessage());
            return false;
        } catch (final TooManyRequestsException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, TOO_MANY_REQUESTS.getCode(), e.getMessage());
            return false;
        } catch (final ConflictException e) {
            syncResult.stats.numSkippedEntries++;
            return true; // We consider the upload successful and mark the measurement as synced
        }

        syncResult.stats.numUpdates++; // Upload was successful, measurement can be marked as synced
        return true;
    }
}
