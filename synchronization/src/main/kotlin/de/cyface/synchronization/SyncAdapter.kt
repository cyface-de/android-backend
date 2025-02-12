/*
 * Copyright 2017-2025 Cyface GmbH
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

import android.accounts.Account
import android.accounts.AuthenticatorException
import android.accounts.NetworkErrorException
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentResolver.SYNC_EXTRAS_MANUAL
import android.content.Context
import android.content.SyncResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.AttachmentStatus
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.protos.model.File.FileType
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.uploader.Result
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.uploader.model.Attachment
import de.cyface.uploader.model.AttachmentIdentifier
import de.cyface.uploader.model.MeasurementIdentifier
import de.cyface.uploader.model.Uploadable
import de.cyface.uploader.model.metadata.ApplicationMetaData
import de.cyface.uploader.model.metadata.ApplicationMetaData.Companion.CURRENT_TRANSFER_FILE_FORMAT_VERSION
import de.cyface.uploader.model.metadata.AttachmentMetaData
import de.cyface.uploader.model.metadata.DeviceMetaData
import de.cyface.uploader.model.metadata.GeoLocation
import de.cyface.uploader.model.metadata.MeasurementMetaData
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.util.Locale
import java.util.UUID

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * In the SyncAdapter guide, the `WorkManager` is recommended for background work
 * https://developer.android.com/training/sync-adapters/index.html (postponed for now)
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 4.2.0
 * @since 2.0.0
 * @property authenticator The authenticator to use for synchronization.
 * @property uploader The uploader to use for synchronization.
 */
class SyncAdapter private constructor(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean,
    private val authenticator: Auth,
    private val uploader: Uploader
) : AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

    private val progressListeners: MutableCollection<ConnectionStatusListener> = HashSet()

    /**
     * When this is set to true the [.isConnected] method always returns true.
     */
    private var mockIsConnectedToReturnTrue = false

    /**
     * Creates a new completely initialized `SyncAdapter`. See the documentation of
     * `AbstractThreadedSyncAdapter` from the Android framework for further information.
     *
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at `AbstractThreadedSyncAdapter`.
     * @param authenticator The authenticator to use for synchronization.
     * @param uploader The uploader to use for synchronization.
     */
    constructor(
        context: Context,
        autoInitialize: Boolean,
        authenticator: Auth,
        uploader: Uploader,
    ) : this(
        context,
        autoInitialize,
        false,
        authenticator,
        uploader
    )

    init {
        addConnectionListener(CyfaceConnectionStatusListener(context))
    }

    override fun onPerformSync(
        account: Account, extras: Bundle,
        authority: String, provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        if (shouldAbortSyncRequest(account, authority, extras)) return

        Log.d(TAG, "Sync started")
        val context = context

        // Ensure sync errors are shown to the user when triggering sync manually
        val fromBackground = !extras.getBoolean(SYNC_EXTRAS_MANUAL)
        val syncPerformer = SyncPerformer(context, fromBackground)
        val persistence = initPersistenceLayer()

        // Ensure user is authorized before starting synchronization
        authenticator.performActionWithFreshTokens { _, _, ex ->
            if (ex != null) {
                handleAuthenticationError(ex, syncResult, fromBackground)
                return@performActionWithFreshTokens
            }

            try {
                val deviceId = persistence.restoreOrCreateDeviceId()
                notifySyncStarted()

                val syncableMeasurements = loadSyncableMeasurements(persistence)
                if (syncableMeasurements.isEmpty()) return@performActionWithFreshTokens

                runBlocking {
                    processMeasurements(
                        syncableMeasurements,
                        syncPerformer,
                        persistence,
                        deviceId,
                        syncResult,
                        fromBackground,
                        account,
                        authority
                    )
                }
            } catch (e: Exception) {
                handleSyncExceptions(e, syncResult, fromBackground)
            } finally {
                finalizeSync(syncResult, provider)
            }
        }
    }

    private fun finalizeSync(syncResult: SyncResult, provider: ContentProviderClient) {
        Log.d(TAG,"Sync finished. (${if (syncResult.hasError()) "ERROR" else "success"})")
        for (listener in progressListeners) {
            listener.onSyncFinished()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            provider.close()
        } else {
            provider.release()
        }
    }

    private fun handleSyncExceptions(
        e: Exception,
        syncResult: SyncResult,
        fromBackground: Boolean
    ) {
        Log.w(TAG, e.javaClass.simpleName + ": " + e.message, e)
        when (e) {
            is CursorIsNullException -> {
                syncResult.databaseError = true
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.DATABASE_ERROR.code,
                    e.message,
                    fromBackground
                )
            }

            is AuthenticatorException -> {
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.AUTHENTICATION_ERROR.code,
                    e.message,
                    fromBackground
                )
            }

            is SynchronizationInterruptedException -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.SYNCHRONIZATION_INTERRUPTED.code,
                    e.message,
                    fromBackground
                )
            }

            is NetworkErrorException -> {
                syncResult.stats.numIoExceptions++
                // No need to sendErrorIntent() as CyfaceAuthenticator already throws more specific error
            }

            // This was newly added, so this might need some additional testing
            else -> {
                syncResult.stats.numIoExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.UNKNOWN.code,
                    e.message,
                    fromBackground
                )
            }
        }
    }

    private suspend fun processMeasurements(
        measurements: List<Measurement>,
        syncPerformer: SyncPerformer,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>,
        deviceId: String,
        syncResult: SyncResult,
        fromBackground: Boolean,
        account: Account,
        authority: String
    ) {
        val measurementCount = measurements.size
        var error: Boolean

        for (index in 0 until measurementCount) {
            val measurement = measurements[index]

            validateMeasurementFormat(measurement)
            val measurementMeta = loadMeasurementMeta(measurement, persistence, deviceId, context)
            // Attention: the attachmentCount might be too little, if images are still being saved
            // just after the measurement stopped, if the upload is instantly triggered. Thus,
            // the imageCount in the metadata might be smaller than the number of images uploaded.
            val attachmentCount = measurementMeta.attachmentMetaData.logCount +
                    measurementMeta.attachmentMetaData.imageCount +
                    measurementMeta.attachmentMetaData.videoCount

            Log.d(TAG, "Preparing to upload Measurement (id ${measurement.id}) with $attachmentCount attachments: ${measurementMeta.attachmentMetaData}")

            // Upload measurement binary first
            if (measurement.status === MeasurementStatus.FINISHED) {
                var compressedTransferTempFile: File? = null
                try {
                    compressedTransferTempFile = serializeMeasurement(measurement, persistence)

                    if (isSyncRequestAborted(account, authority)) return

                    val indexWithinMeasurement = 0 // the core file is index 0
                    val progressListener = DefaultUploadProgressListener(
                        measurementCount,
                        index,
                        measurement.id,
                        attachmentCount,
                        indexWithinMeasurement,
                        progressListeners
                    )
                    error = !syncMeasurement(
                        measurement,
                        measurementMeta,
                        compressedTransferTempFile,
                        syncPerformer,
                        syncResult,
                        fromBackground,
                        persistence,
                        progressListener
                    )
                    if (error) return
                } finally {
                    delete(compressedTransferTempFile)
                }
            }

            // Upload attachments of measurement
            // The status in the database could have changed due to upload, reload it
            val currentStatus = persistence.measurementRepository!!.loadById(measurement.id)!!.status
            if (currentStatus === MeasurementStatus.SYNCABLE_ATTACHMENTS) {
                val syncableAttachments =
                    persistence.attachmentDao!!.loadAllByMeasurementIdAndStatus(
                        measurement.id,
                        AttachmentStatus.SAVED
                    )
                val totalAttachments = persistence.attachmentDao!!.countByMeasurementId(measurement.id)
                val syncedAttachments = totalAttachments - syncableAttachments.size

                for (attachmentIndex in syncableAttachments.indices) {
                    val attachment = syncableAttachments[attachmentIndex]

                    val localFileName = attachment.path.fileName
                    Log.d(TAG, "Preparing to upload attachment (id ${attachment.id}: ${localFileName}).")
                    validateFileFormat(attachment)

                    var transferTempFile: File? = null
                    try {
                        transferTempFile = serializeAttachment(attachment, persistence)

                        if (isSyncRequestAborted(account, authority)) return

                        @Suppress("SpellCheckingInspection")
                        val indexWithinMeasurement = 1 + syncedAttachments + attachmentIndex // ccyf is index 0
                        val progressListener = DefaultUploadProgressListener(
                            measurementCount,
                            index,
                            measurement.id,
                            attachmentCount,
                            indexWithinMeasurement,
                            progressListeners
                        )
                        val attachmentMeta = attachmentMeta(measurementMeta, attachment.id)
                        error = !syncAttachment(
                            attachmentMeta,
                            localFileName,
                            syncPerformer,
                            transferTempFile,
                            syncResult,
                            fromBackground,
                            persistence,
                            progressListener
                        )
                        if (error) return
                    } finally {
                        delete(transferTempFile)
                    }
                }

                persistence.markSyncableAttachmentsAs(MeasurementStatus.SYNCED, measurement.id)
                Log.d(TAG, "Measurement marked as ${MeasurementStatus.SYNCED.name.lowercase()}")
            }
        }
    }

    private fun delete(file: File?) {
        if (file != null && file.exists()) {
            Validate.isTrue(file.delete())
        }
    }

    private fun serializeMeasurement(
        measurement: Measurement,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>
    ): File? {
        var compressedTransferTempFile: File?
        runBlocking {
            compressedTransferTempFile = MeasurementSerializer().writeSerializedCompressed(measurement.id, persistence)
        }
        return compressedTransferTempFile
    }

    private fun serializeAttachment(
        attachment: de.cyface.persistence.model.Attachment,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>
    ): File? {
        var transferTempFile: File?
        runBlocking {
            transferTempFile = MeasurementSerializer().writeSerializedAttachment(attachment, persistence)
        }
        return transferTempFile
    }

    private suspend fun syncMeasurement(
        measurement: Measurement,
        uploadable: de.cyface.uploader.model.Measurement,
        compressedTransferTempFile: File?,
        syncPerformer: SyncPerformer,
        syncResult: SyncResult,
        fromBackground: Boolean,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>,
        progressListener: DefaultUploadProgressListener
    ): Boolean = coroutineScope {
        val resultDeferred = CompletableDeferred<Boolean>()

        authenticator.performActionWithFreshTokens { accessToken, _, e ->
            if (e != null) {
                Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.AUTHENTICATION_ERROR.code,
                    e.message,
                    fromBackground
                )
                resultDeferred.complete(false)
            } else {
                val fileName =
                    "${uploadable.identifier.deviceIdentifier}_" +
                        "${uploadable.identifier.measurementIdentifier}" +
                        ".${COMPRESSED_TRANSFER_FILE_EXTENSION}"
                val result = syncPerformer.sendData(
                    uploader,
                    syncResult,
                    uploadable,
                    compressedTransferTempFile!!,
                    progressListener,
                    accessToken!!,
                    fileName,
                    UploadType.MEASUREMENT
                )
                if (result == Result.UPLOAD_FAILED) {
                    resultDeferred.complete(false)
                } else {
                    // Update sync status of measurement
                    try {
                        when (result) {
                            Result.UPLOAD_SKIPPED -> {
                                persistence.markFinishedAs(
                                    MeasurementStatus.SKIPPED,
                                    measurement.id
                                )
                                Log.d(TAG, "Measurement marked as ${MeasurementStatus.SKIPPED.name.lowercase()}")
                            }
                            Result.UPLOAD_SUCCESSFUL -> {
                                // UPLOADING means only the attachments have to be synced
                                persistence.markFinishedAs(
                                    MeasurementStatus.SYNCABLE_ATTACHMENTS,
                                    measurement.id
                                )
                                Log.d(
                                    TAG,
                                    "Measurement marked as ${MeasurementStatus.SYNCABLE_ATTACHMENTS.name.lowercase()}"
                                )
                            }
                            else -> {
                                throw IllegalArgumentException(
                                    String.format(
                                        Locale.getDefault(),
                                        "Unknown result: %s",
                                        result
                                    )
                                )
                            }
                        }
                        resultDeferred.complete(true)
                    } catch (e: NoSuchMeasurementException) {
                        throw IllegalStateException(e)
                    }
                }
            }
        }

        return@coroutineScope resultDeferred.await()
    }

    private suspend fun syncAttachment(
        attachment: Attachment,
        localFileName: Path,
        syncPerformer: SyncPerformer,
        transferFile: File?,
        syncResult: SyncResult,
        fromBackground: Boolean,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>,
        progressListener: DefaultUploadProgressListener,
    ): Boolean = coroutineScope {
        val resultDeferred = CompletableDeferred<Boolean>()

        authenticator.performActionWithFreshTokens { accessToken, _, e ->
            if (e != null) {
                Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
                syncResult.stats.numAuthExceptions++
                ErrorHandler.sendErrorIntent(
                    context,
                    ErrorCode.AUTHENTICATION_ERROR.code,
                    e.message,
                    fromBackground
                )
                resultDeferred.complete(false)
            } else {
                val attachmentId = attachment.identifier.attachmentIdentifier
                // This helps to identify the content of the log files, e.g.
                // "*_image_metrics.csv" or "*_annotations.json`
                // but also ensures we can generate a `rectId` (image index) from by comparing the
                // `file_name` in `annotations.json#images` and the `id` used there. [LEIP-272]
                // Attention: if you change the format, keep in sync with `WebdavUploader`.
                val prefix = fileNamePrefix(
                    attachment.identifier.deviceIdentifier.toString(),
                    attachment.identifier.measurementIdentifier
                )
                val fileName = prefix + "${attachmentId}_" + "$localFileName"
                val result = syncPerformer.sendData(
                    uploader,
                    syncResult,
                    attachment,
                    transferFile!!,
                    progressListener,
                    accessToken!!,
                    fileName,
                    UploadType.ATTACHMENT
                )
                if (result == Result.UPLOAD_FAILED) {
                    resultDeferred.complete(false)
                } else {
                    // Update sync status of attachment
                    try {
                        when (result) {
                            Result.UPLOAD_SKIPPED -> {
                                persistence.markSavedAs(AttachmentStatus.SKIPPED, attachmentId)
                                Log.d(TAG, "Attachment marked as ${AttachmentStatus.SKIPPED.name.lowercase()}")
                            }

                            Result.UPLOAD_SUCCESSFUL -> {
                                persistence.markSavedAs(AttachmentStatus.SYNCED, attachmentId)
                                Log.d(TAG, "Attachment marked as ${AttachmentStatus.SYNCED.name.lowercase()}")
                            }

                            else -> {
                                throw IllegalArgumentException(
                                    String.format(
                                        Locale.getDefault(),
                                        "Unknown result: %s",
                                        result
                                    )
                                )
                            }
                        }
                        resultDeferred.complete(true)
                    } catch (e: NoSuchMeasurementException) {
                        throw IllegalStateException(e)
                    }
                }
            }
        }

        return@coroutineScope resultDeferred.await()
    }

    private fun validateMeasurementFormat(measurement: Measurement) {
        val format = measurement.fileFormatVersion
        Validate.isTrue(format == DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION)
    }

    private fun validateFileFormat(attachment: de.cyface.persistence.model.Attachment) {
        val format = attachment.fileFormatVersion
        when (attachment.type) {
            FileType.CSV -> {
                Validate.isTrue(format == 1.toShort())
            }
            FileType.JSON -> {
                Validate.isTrue(format == 1.toShort())
            }
            FileType.JPG -> {
                Validate.isTrue(format == 1.toShort())
            }
            else -> {
                throw IllegalArgumentException("Unsupported format ($format) and type (${attachment.type} ")
            }
        }
    }

    private fun loadSyncableMeasurements(
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>
    ): List<Measurement> {
        val partiallyUploaded = persistence.loadMeasurements(MeasurementStatus.SYNCABLE_ATTACHMENTS)
        val finishedMeasurements = persistence.loadMeasurements(MeasurementStatus.FINISHED)
        return partiallyUploaded + finishedMeasurements // Returns the partially uploaded measurements first
    }

    private fun notifySyncStarted() {
        for (listener in progressListeners) {
            listener.onSyncStarted()
        }
    }

    private fun handleAuthenticationError(
        e: Exception,
        syncResult: SyncResult,
        fromBackground: Boolean
    ) {
        Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
        syncResult.stats.numAuthExceptions++
        ErrorHandler.sendErrorIntent(
            context,
            ErrorCode.AUTHENTICATION_ERROR.code,
            e.message,
            fromBackground
        )
    }

    private fun initPersistenceLayer(): DefaultPersistenceLayer<DefaultPersistenceBehaviour?> {
        return DefaultPersistenceLayer(
            context,
            DefaultPersistenceBehaviour()
        )
    }

    /**
     * This allows us to mock the #isConnected() check for unit tests
     */
    private fun shouldAbortSyncRequest(account: Account, authority: String, extras: Bundle): Boolean {
        mockIsConnectedToReturnTrue = extras.containsKey(MOCK_IS_CONNECTED_TO_RETURN_TRUE)
        return isSyncRequestAborted(account, authority)
    }

    /**
     * Checks whether the network was disconnected or the synchronization was interrupted.
     *
     * @return `True` if the synchronization shall be canceled.
     * @param account The `Account` which is used for synchronization
     * @param authority The authority which is used for synchronization
     */
    private fun isSyncRequestAborted(account: Account, authority: String): Boolean {
        if (Thread.interrupted()) {
            Log.w(TAG, "Sync interrupted, aborting sync.")
            return true
        }
        if (!isConnected(account, authority)) {
            Log.w(TAG, "Sync aborted: syncable connection not available anymore")
            return true
        }
        return false
    }

    /**
     * Loads meta data required in the Multipart header to transfer files to the API.
     *
     * @param measurement The [Measurement] to load the meta data for
     * @param persistence The [DefaultPersistenceLayer] to load track data required
     * @param deviceId The device identifier generated for this device
     * @param context The `Context` to load the version name of this SDK
     * @return The [Uploadable] loaded
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Throws(CursorIsNullException::class)
    private fun loadMeasurementMeta(
        measurement: Measurement,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>,
        deviceId: String,
        context: Context
    ): de.cyface.uploader.model.Measurement {

        // If there is only one location captured, start and end locations are identical
        val tracks = persistence.loadTracks(measurement.id)
        var locationCount = 0
        for (track in tracks) {
            locationCount += track.geoLocations.size
        }
        Validate.isTrue(
            tracks.isEmpty() || (tracks[0].geoLocations.size > 0
                    && tracks[tracks.size - 1].geoLocations.size > 0)
        )
        val lastTrack = if (tracks.isNotEmpty()) tracks[tracks.size - 1].geoLocations else null
        var startLocation: GeoLocation? = null
        if (tracks.isNotEmpty()) {
            val l = tracks[0].geoLocations[0]!!
            startLocation = GeoLocation(l.timestamp, l.lat, l.lon)
        }
        var endLocation: GeoLocation? = null
        if (lastTrack != null) {
            val l = lastTrack[lastTrack.size - 1]
            endLocation = GeoLocation(l!!.timestamp, l.lat, l.lon)
        }

        return runBlocking {
            // Attachments
            val csvCount = persistence.attachmentDao!!.countByMeasurementIdAndType(measurement.id, FileType.CSV)
            val jsonCount = persistence.attachmentDao!!.countByMeasurementIdAndType(measurement.id, FileType.JSON)
            val logCount = csvCount + jsonCount
            val imageCount = persistence.attachmentDao!!.countByMeasurementIdAndType(measurement.id, FileType.JPG)
            val allAttachments = persistence.attachmentDao!!.countByMeasurementId(measurement.id)
            val unsupportedAttachments = allAttachments - logCount - imageCount
            require(unsupportedAttachments == 0) {
                "Number of unsupported attachments: $unsupportedAttachments"
            }


            // Non location meta data
            val deviceType = Build.MODEL
            val osVersion = "Android " + Build.VERSION.RELEASE
            val appVersion: String
            val packageManager = context.packageManager
            appVersion = try {
                packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                throw IllegalStateException(e)
            }
            return@runBlocking de.cyface.uploader.model.Measurement(
                MeasurementIdentifier(UUID.fromString(deviceId), measurement.id),
                DeviceMetaData(osVersion, deviceType),
                ApplicationMetaData(appVersion, CURRENT_TRANSFER_FILE_FORMAT_VERSION),
                MeasurementMetaData(
                    measurement.distance,
                    locationCount.toLong(),
                    startLocation,
                    endLocation,
                    measurement.modality.databaseIdentifier,
                ),
                AttachmentMetaData(logCount, imageCount, 0, measurement.filesSize),
            )
        }
    }

    private fun attachmentMeta(measurement: de.cyface.uploader.model.Measurement, attachmentId: Long): Attachment {
        return Attachment(
            AttachmentIdentifier(
                measurement.identifier.deviceIdentifier,
                measurement.identifier.measurementIdentifier,
                attachmentId,
            ),
            measurement.deviceMetaData,
            measurement.applicationMetaData,
            measurement.measurementMetaData,
            measurement.attachmentMetaData,
        )
    }

    /**
     * We need to check if the syncable network is still syncable:
     * - this is only possible indirectly: we check if the surveyor disabled auto sync for the account
     * - the network settings could have changed between sync initial call and "now"
     *
     * The implementation of this method must be identical to [WiFiSurveyor.isConnected].
     *
     * @param account The `Account` to check the status for
     * @param authority The authority string for the synchronization to check
     */
    private fun isConnected(account: Account, authority: String): Boolean {
        if (mockIsConnectedToReturnTrue) {
            Log.w(TAG, "mockIsConnectedToReturnTrue triggered")
            return true
        }

        // We cannot instantly check addPeriodicSync as this seems to be async. For this reason we have a test to ensure
        // it's set to the same state as syncAutomatically: WifiSurveyorTest.testSetConnected()
        return ContentResolver.getSyncAutomatically(account, authority)
    }

    private fun addConnectionListener(listener: ConnectionStatusListener) {
        progressListeners.add(listener)
    }

    companion object {
        /**
         * A String to filter log output from [SyncPerformer] logs.
         */
        @Suppress("SpellCheckingInspection")
        const val TAG = "de.cyface.sync.adaptr"

        /**
         * This bundle flag allows our unit tests to mock [.isConnected].
         *
         *
         * We cannot use `ContentResolver` as we do in the production code as this is an Unit test.
         * When this `Bundle` extra is set (no matter to which String) the [.isConnected]
         * method returns true;
         */
        const val MOCK_IS_CONNECTED_TO_RETURN_TRUE = "mocked_periodic_sync_check_false"

        /**
         * The file extension of the measurement file which is transmitted on synchronization.
         */
        @Suppress("SpellCheckingInspection", "RedundantSuppression")
        const val COMPRESSED_TRANSFER_FILE_EXTENSION = "ccyf"

        fun fileNamePrefix(deviceId: String, measurementId: Long): String {
            return "${deviceId}_" + "${measurementId}_"
        }
    }
}