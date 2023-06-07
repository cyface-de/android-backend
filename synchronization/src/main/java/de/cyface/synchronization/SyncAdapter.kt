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
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.NetworkErrorException
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
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
import de.cyface.uploader.Authenticator
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import java.io.File

/**
 * An Android SyncAdapter implementation to transmit data to a Cyface server.
 *
 * In the SyncAdapter guide, the `WorkManager` is recommended for background work
 * https://developer.android.com/training/sync-adapters/index.html (postponed for now)
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 4.0.0
 * @since 2.0.0
 * @property authenticator The authenticator to use for synchronization.
 * @property uploader The uploader to use for synchronization.
 */
class SyncAdapter private constructor(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean,
    private val authenticator: Authenticator,
    private val uploader: Uploader
) : AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

    private val progressListener: MutableCollection<ConnectionStatusListener>

    /**
     * When this is set to true the [.isConnected] method always returns true.
     */
    private var mockIsConnectedToReturnTrue = false

    /**
     * The service used for authorization.
     */
    private var mAuthService: AuthorizationService

    /**
     * The authorization state.
     */
    private var mStateManager: AuthStateManager

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private var mConfiguration: Configuration

    /**
     * Creates a new completely initialized `SyncAdapter`. See the documentation of
     * `AbstractThreadedSyncAdapter` from the Android framework for further information.
     *
     * @param context The context this adapter is active under.
     * @param autoInitialize More details are available at `AbstractThreadedSyncAdapter`.
     * @param authenticator The authenticator to use for synchronization.
     * @param uploader The uploader to use for synchronization.
     */
    internal constructor(
        context: Context,
        autoInitialize: Boolean,
        authenticator: Authenticator,
        uploader: Uploader
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

        // Authorization
        mStateManager = AuthStateManager.getInstance(context)
        //mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(context)
        val config = Configuration.getInstance(context)
        if (config.hasConfigurationChanged()) {
            throw IllegalArgumentException("config changed")
            /*show("Authentifizierung ist abgelaufen")
            Handler().postDelayed({signOut()}, 2000)*/
            //return
        }
        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
    }

    override fun onPerformSync(
        account: Account, extras: Bundle,
        authority: String, provider: ContentProviderClient,
        syncResult: SyncResult
    ) {

        // This allows us to mock the #isConnected() check for unit tests
        mockIsConnectedToReturnTrue = extras.containsKey(MOCK_IS_CONNECTED_TO_RETURN_TRUE)
        if (isSyncRequestAborted(account, authority)) {
            return
        }
        Log.d(TAG, "Sync started")
        val context = context
        val serializer = MeasurementSerializer()
        val persistence =
            DefaultPersistenceLayer<DefaultPersistenceBehaviour?>(
                context,
                DefaultPersistenceBehaviour()
            )
        val authenticator = CyfaceAuthenticator(context, authenticator)
        val syncPerformer = SyncPerformer(context)
        try {
            // Ensure user is authorized before starting synchronization
            // FIXME: getAuthToken currently just refreshes the token async, maybe call `authenticator.isAuthorized` here
            getAuthToken(authenticator, account)
            val deviceId = persistence.restoreOrCreateDeviceId()

            // Inform ConnectionStatusListener
            for (listener in progressListener) {
                listener.onSyncStarted()
            }

            // Load all Measurements ready for synchronization
            val syncableMeasurements = persistence.loadMeasurements(MeasurementStatus.FINISHED)
            if (syncableMeasurements!!.isEmpty()) {
                return  // nothing to sync
            }
            val measurementCount = syncableMeasurements.size
            for (index in 0 until measurementCount) {
                val measurement = syncableMeasurements[index]
                Log.d(
                    Constants.TAG, String.format(
                        "Measurement with identifier %d is about to be loaded for transmission.",
                        measurement!!.id
                    )
                )

                // Ensure the measurement is supported
                val format = measurement.fileFormatVersion
                Validate.isTrue(format == DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION)

                // Load measurement data
                val metaData = loadMetaData(measurement, persistence, deviceId, context)

                // Load, try to sync the file to be transferred and clean it up afterwards
                var compressedTransferTempFile: File? = null
                try {
                    runBlocking {
                        compressedTransferTempFile =
                            serializer.writeSerializedCompressed(measurement.id, persistence)
                    }

                    // Acquire new auth token before each synchronization (old one could be expired)
                    //val jwtAuthToken = getAuthToken(authenticator, account) // FIXME: replace with `performActionWithFreshTokens`

                    // Check whether the network settings changed to avoid using metered network without permission
                    if (isSyncRequestAborted(account, authority)) {
                        return
                    }

                    // Synchronize measurement
                    val processListener = object : UploadProgressListener {
                        override fun updatedProgress(percent: Float) {
                            // Multi-measurement progress
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

                    // FIXME: This is now executed asynchronously!

                    // FIXME !!!!!!!!!!!! SyncAdapter may not be dependent on OAuth (used by SR, too)
                    mStateManager.current.performActionWithFreshTokens(
                        mAuthService
                    ) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                        Log.w(TAG, "TOKEN ::::: FIXME ::: $accessToken")
                        val result = syncPerformer.sendData(
                            uploader,
                            syncResult,
                            metaData,
                            compressedTransferTempFile!!,
                            processListener,
                            accessToken!!
                        )
                        if (result == Result.UPLOAD_FAILED) {
                            throw java.lang.IllegalStateException("upload failed")
                            //FIXME break
                        }

                        // Mark successfully transmitted measurement as synced
                        try {
                            if (result == Result.UPLOAD_SKIPPED) {
                                persistence.markFinishedAs(MeasurementStatus.SKIPPED, measurement.id)
                            } else if (result == Result.UPLOAD_SUCCESSFUL) {
                                persistence.markFinishedAs(MeasurementStatus.SYNCED, measurement.id)
                            } else {
                                throw IllegalArgumentException(
                                    String.format(
                                        "Unknown result: %s",
                                        result
                                    )
                                )
                            }
                            Log.d(
                                Constants.TAG, String.format(
                                    "Measurement marked as %s.",
                                    result.toString().lowercase()
                                )
                            )
                        } catch (e: NoSuchMeasurementException) {
                            throw IllegalStateException(e)
                        }
                    }
                } finally {
                    if (compressedTransferTempFile != null && compressedTransferTempFile!!.exists()) {
                        Validate.isTrue(compressedTransferTempFile!!.delete())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        provider.close()
                    } else {
                        provider.release()
                    }
                }
            }
        } catch (e: CursorIsNullException) {
            Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
            syncResult.databaseError = true
            ErrorHandler.sendErrorIntent(context, ErrorCode.DATABASE_ERROR.code, e.message)
        } catch (e: AuthenticatorException) {
            Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
            syncResult.stats.numAuthExceptions++
            ErrorHandler.sendErrorIntent(context, ErrorCode.AUTHENTICATION_ERROR.code, e.message)
        } catch (e: SynchronizationInterruptedException) {
            Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
            syncResult.stats.numIoExceptions++
            ErrorHandler.sendErrorIntent(
                context,
                ErrorCode.SYNCHRONIZATION_INTERRUPTED.code,
                e.message
            )
        } catch (e: NetworkErrorException) {
            Log.w(TAG, e.javaClass.simpleName + ": " + e.message)
            syncResult.stats.numIoExceptions++
            // No need to sendErrorIntent() as CyfaceAuthenticator already throws more specific error
        } finally {
            Log.d(
                TAG,
                String.format(
                    "Sync finished. (%s)",
                    if (syncResult.hasError()) "ERROR" else "success"
                )
            )
            for (listener in progressListener) {
                listener.onSyncFinished()
            }
        }
    }

    /**
     * Gets the authentication token from the [CyfaceAuthenticator].
     *
     * @param authenticator The `CyfaceAuthenticator` to be used
     * @param account The `Account` to get the token for
     * @return The token as string
     * @throws AuthenticatorException If no token was supplied which must be supported for implementing apps (SR)
     * @throws NetworkErrorException If the network authentication request failed for any reasons
     * @throws SynchronizationInterruptedException If the synchronization was [Thread.interrupted].
     */
    @Throws(
        AuthenticatorException::class,
        NetworkErrorException::class,
        SynchronizationInterruptedException::class
    )
    private fun getAuthToken(authenticator: CyfaceAuthenticator, account: Account): String {
        val jwtAuthToken: String?
        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        val bundle: Bundle? = try {
            authenticator.getAuthToken(null, account, Constants.AUTH_TOKEN_TYPE, null)
        } catch (e: NetworkErrorException) {
            // This happened e.g. when Wifi was manually disabled just after synchronization started (Pixel 2 XL).
            Log.w(TAG, "getAuthToken failed, was the connection closed? Aborting sync.")
            throw e
        }
        if (bundle == null) {
            // Because of Movebis we don't throw an IllegalStateException if there is no auth token
            throw AuthenticatorException("No valid auth token supplied. Aborting data synchronization!")
        }
        /*jwtAuthToken = bundle.getString(AccountManager.KEY_AUTHTOKEN)
        // When WifiSurveyor.deleteAccount() was called in the meantime the jwt token is empty, thus:
        if (jwtAuthToken == null) {
            Validate.isTrue(Thread.interrupted())
            throw SynchronizationInterruptedException("Sync interrupted, aborting sync.")
        }
        Log.d(TAG, "Login authToken: **" + jwtAuthToken.substring(jwtAuthToken.length - 7))*/
        return "FIXME123" //jwtAuthToken
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