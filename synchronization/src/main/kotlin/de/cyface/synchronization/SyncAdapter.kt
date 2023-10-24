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
import de.cyface.model.RequestMetaData
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.serialization.MeasurementSerializer
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * In the SyncAdapter guide, the `WorkManager` is recommended for background work
 * https://developer.android.com/training/sync-adapters/index.html (postponed for now)
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 4.1.1
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

    private val progressListener: MutableCollection<ConnectionStatusListener>

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
        progressListener = HashSet()
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
                    processMeasurements(syncableMeasurements, syncPerformer, persistence, deviceId, syncResult, fromBackground, account, authority, provider)
                }
            } catch (e: Exception) {
                handleSyncExceptions(e, syncResult, fromBackground)
            } finally {
                finalizeSync(syncResult)
            }
        }
    }

    private fun finalizeSync(syncResult: SyncResult) {
        Log.d(TAG,"Sync finished. (${if (syncResult.hasError()) "ERROR" else "success"})")
        for (listener in progressListener) {
            listener.onSyncFinished()
        }
    }

    private fun handleSyncExceptions(
        e: Exception,
        syncResult: SyncResult,
        fromBackground: Boolean
    ) {
        Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
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
        authority: String,
        provider: ContentProviderClient
    ) {
        val measurementCount = measurements.size
        var error = false

        for (index in 0 until measurementCount) {
            val measurement = measurements[index]
            if (error) return

            Log.d(TAG, "Preparing Measurement (id ${measurement.id}) for upload.",)
            validateMeasurementFormat(measurement)

            val metaData = loadMetaData(measurement, persistence, deviceId, context)

            // Load, try to sync the file to be transferred and clean it up afterwards
            var compressedTransferTempFile: File? = null
            try {
                compressedTransferTempFile = serializeMeasurement(measurement, persistence)

                if (isSyncRequestAborted(account, authority)) return

                error = !syncMeasurement(measurement, metaData, compressedTransferTempFile, syncPerformer, syncResult, fromBackground, index, measurementCount, persistence)

                FIXME: Sync attachments, mark each as SYNCED and then mark measurement as SYNCED
            } finally {
                cleanUpAfterSync(compressedTransferTempFile, provider)
            }
        }
    }

    private fun cleanUpAfterSync(compressedTransferTempFile: File?, provider: ContentProviderClient) {
        if (compressedTransferTempFile != null && compressedTransferTempFile.exists()) {
            Validate.isTrue(compressedTransferTempFile.delete())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            provider.close()
        } else {
            provider.release()
        }
    }

    private fun serializeMeasurement(measurement: Measurement, persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>): File? {
        var compressedTransferTempFile: File?
        runBlocking {
            compressedTransferTempFile = MeasurementSerializer().writeSerializedCompressed(measurement.id, persistence)
        }
        return compressedTransferTempFile
    }

    private suspend fun syncMeasurement(
        measurement: Measurement,
        metaData: RequestMetaData,
        compressedTransferTempFile: File?,
        syncPerformer: SyncPerformer,
        syncResult: SyncResult,
        fromBackground: Boolean,
        index: Int,
        measurementCount: Int,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>
    ): Boolean = coroutineScope {
        val resultDeferred = CompletableDeferred<Boolean>()

        // Multi-measurement progress
        val processListener = object : UploadProgressListener {
            override fun updatedProgress(percent: Float) {
                val progressPerMeasurement =
                    100.0 / measurementCount.toDouble()
                val progressBeforeThis =
                    index.toDouble() * progressPerMeasurement
                val lastMeasurement = index == measurementCount - 1
                val total =
                    if (lastMeasurement && percent.toDouble() == 1.0) 100.0 else progressBeforeThis + percent * progressPerMeasurement
                for (listener in progressListener) {
                    listener.onProgress(total.toFloat(), measurement.id)
                }
            }
        }

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
                val result = syncPerformer.sendData(
                    uploader,
                    syncResult,
                    metaData,
                    compressedTransferTempFile!!,
                    processListener,
                    accessToken!!
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
                            }
                            Result.UPLOAD_SUCCESSFUL -> {
                                // UPLOADING means only the attachments have to be synced
                                persistence.markFinishedAs(
                                    MeasurementStatus.SYNCABLE_ATTACHMENTS,
                                    measurement.id
                                )
                            }
                            else -> {
                                throw IllegalArgumentException(
                                    String.format(
                                        "Unknown result: %s",
                                        result
                                    )
                                )
                            }
                        }
                        Log.d(TAG, "Measurement marked as ${result.toString().lowercase()}")
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

    private fun loadSyncableMeasurements(persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>): List<Measurement> {
        val partiallyUploaded = persistence.loadMeasurements(MeasurementStatus.SYNCABLE_ATTACHMENTS)
        val finishedMeasurements = persistence.loadMeasurements(MeasurementStatus.FINISHED)
        return partiallyUploaded + finishedMeasurements // Returns the partially uploaded measurements first
    }

    private fun notifySyncStarted() {
        for (listener in progressListener) {
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
     * @return The [RequestMetaData] loaded
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Throws(CursorIsNullException::class)
    private fun loadMetaData(
        measurement: Measurement,
        persistence: DefaultPersistenceLayer<DefaultPersistenceBehaviour?>, deviceId: String,
        context: Context
    ): RequestMetaData {

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
        val lastTrack: List<ParcelableGeoLocation?>? =
            if (tracks.isNotEmpty()) tracks[tracks.size - 1].geoLocations else null
        var startLocation: RequestMetaData.GeoLocation? = null
        if (tracks.isNotEmpty()) {
            val l = tracks[0].geoLocations[0]!!
            startLocation = RequestMetaData.GeoLocation(l.timestamp, l.lat, l.lon)
        }
        var endLocation: RequestMetaData.GeoLocation? = null
        if (lastTrack != null) {
            val l = lastTrack[lastTrack.size - 1]
            endLocation = RequestMetaData.GeoLocation(l!!.timestamp, l.lat, l.lon)
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
        return RequestMetaData(
            deviceId,
            measurement.id.toString(),
            osVersion,
            deviceType,
            appVersion,
            measurement.distance,
            locationCount.toLong(),
            startLocation,
            endLocation,
            measurement.modality.databaseIdentifier,
            RequestMetaData.CURRENT_TRANSFER_FILE_FORMAT_VERSION
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
        progressListener.add(listener)
    }

    companion object {
        /**
         * A String to filter log output from [SyncPerformer] logs.
         */
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
    }
}