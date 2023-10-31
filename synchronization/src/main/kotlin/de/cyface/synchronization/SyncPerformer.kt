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
package de.cyface.synchronization

import android.content.Context
import android.content.SyncResult
import android.util.Log
import de.cyface.model.RequestMetaData
import de.cyface.serializer.DataSerializable
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.AccountNotActivated
import de.cyface.uploader.exception.BadRequestException
import de.cyface.uploader.exception.ConflictException
import de.cyface.uploader.exception.EntityNotParsableException
import de.cyface.uploader.exception.ForbiddenException
import de.cyface.uploader.exception.InternalServerErrorException
import de.cyface.uploader.exception.MeasurementTooLarge
import de.cyface.uploader.exception.NetworkUnavailableException
import de.cyface.uploader.exception.ServerUnavailableException
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.uploader.exception.TooManyRequestsException
import de.cyface.uploader.exception.UnauthorizedException
import de.cyface.uploader.exception.UnexpectedResponseCode
import de.cyface.uploader.exception.UploadFailed
import de.cyface.uploader.exception.UploadSessionExpired
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * Performs the actual synchronisation with a provided server, by uploading meta data and a file containing
 * measurements.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 8.0.0
 * @since 2.0.0
 * @property context The Android `Context` to use for setting the correct server certification information.
 * @property fromBackground `true` if the error was caused without user interaction, e.g. to avoid
 *          disturbing the user while he is not using the app.
 */
internal class SyncPerformer(private val context: Context, private val fromBackground: Boolean) {
    /**
     * Triggers the data transmission to a Cyface server API. The `measurementIdentifier` and
     * `deviceIdentifier` need to be globally unique. If they are not the server will probably reject the
     * request.
     *
     * Sync errors are broadcasted to the [ErrorHandler].
     *
     * Since this is a synchronous call it can take from seconds to minutes depending on the size of `data`.
     * Never call this on the UI thread. Your users are going to hate you.
     *
     * @param uploader The uploader to use for transmission
     * @param syncResult The [SyncResult] used to store sync error information.
     * @param metaData The [RequestMetaData] required for the Multipart request.
     * @param file The compressed transfer file containing the `Measurement` data to transmit
     * @param progressListener The [UploadProgressListener] to be informed about the upload progress.
     * @param jwtAuthToken A valid JWT auth token to authenticate the transmission
     * @param fileName Id of the attachment to upload or `null` if this is not an attachment.
     * @param endpoint The endpoint to upload the file to.
     * @return True of the transmission was successful.
     */
    fun sendData(
        uploader: Uploader,
        syncResult: SyncResult,
        metaData: RequestMetaData,
        file: File,
        progressListener: UploadProgressListener,
        jwtAuthToken: String,
        fileName: String,
        endpoint: URL
    ): Result {
        val size = DataSerializable.humanReadableSize(file.length(), true)
        Log.d(TAG, "Transferring attachment or compressed measurement ($size})")

        return runBlocking {
            val deferredResult = CoroutineScope(Dispatchers.IO).async {
                val result = try {
                    Log.i(TAG, "Uploading $fileName to $endpoint")

                    uploader.upload(jwtAuthToken, metaData, file, endpoint, progressListener)
                } catch (e: UploadFailed) {
                    return@async handleUploadFailed(e, syncResult)
                } catch (e: MalformedURLException) {
                    // Catching this temporarily to indicate a hard error to the sync adapter
                    syncResult.stats.numParseExceptions++
                    Log.e(TAG, "MalformedURLException in `HttpConnection.upload`.")
                    throw IllegalArgumentException(e)
                } catch (e: RuntimeException) {
                    // Catching this temporarily to indicate a hard error to the sync adapter
                    syncResult.stats.numParseExceptions++

                    // e.g. when the collector API does respond correctly [DAT-775]
                    Log.e(TAG, "Uncaught Exception in `HttpConnection.upload`.")
                    throw e // TODO ensure this is still thrown
                }

                // Upload was successful, measurement can be marked as synced
                if (result == Result.UPLOAD_SKIPPED) {
                    syncResult.stats.numSkippedEntries++
                } else {
                    syncResult.stats.numUpdates++
                }
                return@async result
            }

            return@runBlocking deferredResult.await()
        }
    }

    private fun handleUploadFailed(e: UploadFailed, syncResult: SyncResult): Result {
        return when (e.cause) {
            is ServerUnavailableException -> {
                // The SyncResults come from Android and help the SyncAdapter to re-schedule the sync
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.SERVER_UNAVAILABLE.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is ForbiddenException -> {
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.FORBIDDEN.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is MalformedURLException -> {
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.MALFORMED_URL.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is SynchronisationException -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.SYNCHRONIZATION_ERROR.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is UnauthorizedException -> {
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.UNAUTHORIZED.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is InternalServerErrorException -> {
                syncResult.stats.numConflictDetectedExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.INTERNAL_SERVER_ERROR.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is EntityNotParsableException -> {
                syncResult.stats.numParseExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.ENTITY_NOT_PARSABLE.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is BadRequestException -> {
                syncResult.stats.numParseExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.BAD_REQUEST.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is NetworkUnavailableException -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.NETWORK_UNAVAILABLE.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is SynchronizationInterruptedException -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.SYNCHRONIZATION_INTERRUPTED.code,
                    e.message,
                    fromBackground
                )
                e.printStackTrace()
                Result.UPLOAD_FAILED
            }

            is TooManyRequestsException -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.TOO_MANY_REQUESTS.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is UploadSessionExpired -> {
                syncResult.stats.numIoExceptions++ // Try again
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.UPLOAD_SESSION_EXPIRED.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is UnexpectedResponseCode -> {
                syncResult.stats.numParseExceptions++ // hard error
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.UNEXPECTED_RESPONSE_CODE.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is AccountNotActivated -> {
                syncResult.stats.numAuthExceptions++ // hard error
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.ACCOUNT_NOT_ACTIVATED.code,
                    e.message,
                    fromBackground
                )
                Result.UPLOAD_FAILED
            }

            is MeasurementTooLarge -> {
                syncResult.stats.numSkippedEntries++
                Log.d(TAG, e.message!!)
                Result.UPLOAD_SKIPPED
            }

            is ConflictException -> {
                syncResult.stats.numSkippedEntries++
                // We consider the upload successful and mark the measurement as synced
                Result.UPLOAD_SUCCESSFUL
            }

            else -> {
                // Unknown sub-type of `UploadFailed`
                throw IllegalArgumentException(e)
            }
        }
    }

    companion object {
        /**
         * A String to filter log output from [SyncPerformer] logs.
         */
        const val TAG = "de.cyface.sync.perf"
    }
}