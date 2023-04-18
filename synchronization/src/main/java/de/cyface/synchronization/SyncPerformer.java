/*
 * Copyright 2017-2023 Cyface GmbH
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

import static de.cyface.serializer.DataSerializable.humanReadableSize;
import static de.cyface.synchronization.ErrorHandler.sendErrorIntent;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.ACCOUNT_NOT_ACTIVATED;
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
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UNEXPECTED_RESPONSE_CODE;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UPLOAD_SESSION_EXPIRED;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Locale;

import android.content.Context;
import android.content.SyncResult;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.model.RequestMetaData;
import de.cyface.persistence.Constants;
import de.cyface.persistence.model.Measurement;
import de.cyface.uploader.Result;
import de.cyface.uploader.UploadProgressListener;
import de.cyface.uploader.Uploader;
import de.cyface.uploader.exception.AccountNotActivated;
import de.cyface.uploader.exception.BadRequestException;
import de.cyface.uploader.exception.ConflictException;
import de.cyface.uploader.exception.EntityNotParsableException;
import de.cyface.uploader.exception.ForbiddenException;
import de.cyface.uploader.exception.InternalServerErrorException;
import de.cyface.uploader.exception.MeasurementTooLarge;
import de.cyface.uploader.exception.NetworkUnavailableException;
import de.cyface.uploader.exception.ServerUnavailableException;
import de.cyface.uploader.exception.SynchronisationException;
import de.cyface.uploader.exception.SynchronizationInterruptedException;
import de.cyface.uploader.exception.TooManyRequestsException;
import de.cyface.uploader.exception.UnauthorizedException;
import de.cyface.uploader.exception.UnexpectedResponseCode;
import de.cyface.uploader.exception.UploadSessionExpired;

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 6.1.1
 * @since 2.0.0
 */
class SyncPerformer {

    /**
     * A String to filter log output from {@link SyncPerformer} logs.
     */
    final static String TAG = "de.cyface.sync.perf";
    /**
     * The file extension of the measurement file which is transmitted on synchronization.
     */
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TRANSFER_FILE_EXTENSION = "ccyf";

    private final Context context;

    /**
     * Creates a new completely initialized <code>SyncPerformer</code> for a given Android <code>Context</code>.
     *
     * @param context The Android <code>Context</code> to use for setting the correct server certification information.
     */
    SyncPerformer(final @NonNull Context context) {
        this.context = context;
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
     * @param uploader The uploader to use for transmission
     * @param syncResult The {@link SyncResult} used to store sync error information.
     * @param metaData The {@link RequestMetaData} required for the Multipart request.
     * @param file The compressed transfer file containing the {@link Measurement} data to transmit
     * @param progressListener The {@link UploadProgressListener} to be informed about the upload progress.
     * @param jwtAuthToken A valid JWT auth token to authenticate the transmission
     * @return True of the transmission was successful.
     */
    Result sendData(@NonNull final Uploader uploader, @NonNull final SyncResult syncResult,
            @NonNull final RequestMetaData metaData,
            @NonNull final File file, @NonNull final UploadProgressListener progressListener,
            @NonNull final String jwtAuthToken) {

        Log.d(Constants.TAG, String.format("Transferring compressed measurement (%s)",
                humanReadableSize(file.length(), true)));

        final Result result;
        try {
            final var fileName = String.format(Locale.US, "%s_%s." + TRANSFER_FILE_EXTENSION,
                    metaData.getDeviceIdentifier(), metaData.getMeasurementIdentifier());
            Log.i(TAG, String.format(Locale.GERMAN, "Uploading %s to %s", fileName, uploader.endpoint()));
            result = uploader.upload(jwtAuthToken, metaData, file, progressListener);
        } catch (final ServerUnavailableException e) {
            // The SyncResults come from Android and help the SyncAdapter to re-schedule the sync
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SERVER_UNAVAILABLE.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final ForbiddenException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, FORBIDDEN.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final MalformedURLException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, MALFORMED_URL.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final SynchronisationException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_ERROR.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final UnauthorizedException e) {
            syncResult.stats.numAuthExceptions++;
            sendErrorIntent(context, UNAUTHORIZED.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final InternalServerErrorException e) {
            syncResult.stats.numConflictDetectedExceptions++;
            sendErrorIntent(context, INTERNAL_SERVER_ERROR.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final EntityNotParsableException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, ENTITY_NOT_PARSABLE.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final BadRequestException e) {
            syncResult.stats.numParseExceptions++;
            sendErrorIntent(context, BAD_REQUEST.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final NetworkUnavailableException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, NETWORK_UNAVAILABLE.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final SynchronizationInterruptedException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, SYNCHRONIZATION_INTERRUPTED.getCode(), e.getMessage());
            e.printStackTrace();
            return Result.UPLOAD_FAILED;
        } catch (final TooManyRequestsException e) {
            syncResult.stats.numIoExceptions++;
            sendErrorIntent(context, TOO_MANY_REQUESTS.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final UploadSessionExpired e) {
            syncResult.stats.numIoExceptions++; // Try again
            sendErrorIntent(context, UPLOAD_SESSION_EXPIRED.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final UnexpectedResponseCode e) {
            syncResult.stats.numParseExceptions++; // hard error
            sendErrorIntent(context, UNEXPECTED_RESPONSE_CODE.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final AccountNotActivated e) {
            syncResult.stats.numAuthExceptions++; // hard error
            sendErrorIntent(context, ACCOUNT_NOT_ACTIVATED.getCode(), e.getMessage());
            return Result.UPLOAD_FAILED;
        } catch (final MeasurementTooLarge e) {
            syncResult.stats.numSkippedEntries++;
            Log.d(TAG, e.getMessage());
            return Result.UPLOAD_SKIPPED;
        } catch (final ConflictException e) {
            syncResult.stats.numSkippedEntries++;
            // We consider the upload successful and mark the measurement as synced
            return Result.UPLOAD_SUCCESSFUL;
        } catch (RuntimeException e) {
            // Catching this temporarily to indicate a hard error to the sync adapter
            syncResult.stats.numParseExceptions++;

            // e.g. when the collector API does respond correctly [DAT-775]
            Log.e(TAG, "Uncaught Exception in `HttpConnection.upload`.");
            // Throw hard or else this error won't be recognized, fixed & device runs out of storage
            throw e;
        }

        // Upload was successful, measurement can be marked as synced
        if (result.equals(Result.UPLOAD_SKIPPED)) {
            syncResult.stats.numSkippedEntries++;
        } else {
            syncResult.stats.numUpdates++;
        }
        return result;
    }
}