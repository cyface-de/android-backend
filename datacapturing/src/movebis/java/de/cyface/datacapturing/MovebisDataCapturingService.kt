/*
 * Copyright 2018-2023 Cyface GmbH
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
package de.cyface.datacapturing

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.ui.Reason
import de.cyface.datacapturing.ui.UIListener
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.strategy.DefaultDistanceCalculation
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE
import de.cyface.synchronization.exception.SynchronisationException
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate

/**
 * In implementation of the [DataCapturingService] as required inside the Movebis project.
 *
 * This implementation provides access to location updates even outside of a running data capturing session. To start
 * these updates use [.startUILocationUpdates]; to stop it use [.stopUILocationUpdates]. It might be
 * necessary to provide a user interface asking the user for location access permissions. You can provide this user
 * interface using [UIListener.onRequirePermission]. This method will be called with
 * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` permission requests.
 *
 * Before you try to measure any data you should provide a valid JWT auth token for data synchronization. You may do
 * this using [.registerJWTAuthToken] with a token for a certain username. To anonymize the user
 * is ok to use some garbage username here. If a user is no longer required, you can deregister it using
 * [.deregisterJWTAuthToken].
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 11.0.2
 * @since 2.0.0
 */
// Used by SDK implementing apps (SR)
class MovebisDataCapturingService internal constructor(
    context: Context, authority: String,
    accountType: String, dataUploadServerAddress: String,
    uiListener: UIListener,
    /**
     * The maximum rate of location updates to receive in milliseconds which are sent to the [UIListener].
     *
     * This only determines the updates sent to the `UIListener`, not the amount of locations captured for
     * [Measurement]s.
     *
     * Set this to `0L` if you would like to be notified as often as possible.
     */
    private val locationUpdateRate: Long,
    eventHandlingStrategy: EventHandlingStrategy,
    capturingListener: DataCapturingListener, sensorFrequency: Int
) : DataCapturingService(
    context, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
    DefaultPersistenceLayer(context, authority, CapturingPersistenceBehaviour()),
    DefaultDistanceCalculation(), DefaultLocationCleaning(), capturingListener,
    sensorFrequency
) {
    /**
     * A `LocationManager` that is used to provide location updates for the UI even if no capturing is
     * running.
     */
    private val preMeasurementLocationManager: LocationManager?

    /**
     * A listener for location updates, which it passes through to the user interface.
     */
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val uiListener: UIListener? = uiListener
            uiListener?.onLocationUpdate(location)
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            // Nothing to do here.
        }

        override fun onProviderEnabled(provider: String) {
            // Nothing to do here.
        }

        override fun onProviderDisabled(provider: String) {
            // Nothing to do here.
        }
    }

    /**
     * A flag set if the locationListener for UI updates is active. This helps us to prevent to register such a listener
     * multiple times.
     */
    private var uiUpdatesActive = false

    /**
     * Creates a new completely initialized [MovebisDataCapturingService].
     *
     * @param context The context (i.e. `Activity`) handling this service.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     * this service. This must be in the format "https://some.url/optional/resource".
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in milliseconds which are sent to the
     * [UIListener]. This only determines the updates sent to the `#getUiListener`, not
     * the amount of locations captured for [Measurement]s. Set this to `0L` if you would like to
     * be notified as often as possible.
     * @param eventHandlingStrategy The [EventHandlingStrategy] used to react to selected events
     * triggered by the [DataCapturingBackgroundService].
     * @param capturingListener A [DataCapturingListener] that is notified of important events during data
     * capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     * fails.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    @Suppress("unused") // Used by SDK implementing apps (SR)
    constructor(
        context: Context, dataUploadServerAddress: String,
        uiListener: UIListener, locationUpdateRate: Long,
        eventHandlingStrategy: EventHandlingStrategy,
        capturingListener: DataCapturingListener, sensorFrequency: Int
    ) : this(
        context,
        "de.cyface.provider",
        "de.cyface",
        dataUploadServerAddress,
        uiListener,
        locationUpdateRate,
        eventHandlingStrategy,
        capturingListener,
        sensorFrequency
    ) {
    }

    /**
     * Creates a new completely initialized [MovebisDataCapturingService].
     * This variant is required to test the ContentProvider.
     *
     * **ATTENTION:** This constructor is only for testing to be able to inject authority and account type. Use
     * []
     * instead.
     *
     * @param context The context (i.e. `Activity`) handling this service.
     * @param authority The `ContentProvider` authority used to identify the content provider used by this
     * `DataCapturingService`. You should use something world wide unique, like your domain, to
     * avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     * this service. This must be in the format "https://some.url/optional/resource".
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in milliseconds which are sent to the
     * [UIListener]. This only determines the updates sent to the `UIListener`, not the amount
     * of locations captured for [Measurement]s. Set this to `0L` if you would like to be
     * notified as often as possible.
     * @param eventHandlingStrategy The [EventHandlingStrategy] used to react to selected events
     * triggered by the [DataCapturingBackgroundService].
     * @param capturingListener A [DataCapturingListener] that is notified of important events during data
     * capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     * fails.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     */
    init {
        preMeasurementLocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setUiListener(uiListener)
    }

    /**
     * Starts the reception of location updates for the user interface. No tracking is started with this method. This is
     * purely intended for display purposes. The received locations are forwarded to the [UIListener] provided to
     * the constructor.
     */
    // Because sdk implementing apps (SR) use this method to receive location updates
    @SuppressLint("MissingPermission") // Because we are checking the permission, but lint does not notice this.
    fun startUILocationUpdates() {
        if (uiUpdatesActive) {
            return
        }
        val fineLocationAccessIsGranted = checkFineLocationAccess(getContext()!!)
        if (fineLocationAccessIsGranted) {
            preMeasurementLocationManager!!.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, locationUpdateRate, 0f,
                locationListener
            )
            uiUpdatesActive = true
        }
        val coarseLocationAccessIsGranted = checkCoarseLocationAccess(getContext()!!)
        if (coarseLocationAccessIsGranted) {
            if (!preMeasurementLocationManager!!.getAllProviders()
                    .contains(LocationManager.NETWORK_PROVIDER)
            ) {
                Log.w(
                    Constants.TAG,
                    "Network provider does not exist, not requesting UI location updates from that provider"
                )
                return
            }
            preMeasurementLocationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                locationUpdateRate,
                0f,
                locationListener
            )
            uiUpdatesActive = true
        }
    }

    /**
     * Stops reception of location updates for the user interface.
     *
     * @see .startUILocationUpdates
     */
    // Because sdk implementing apps (SR) use this method to receive location updates
    fun stopUILocationUpdates() {
        if (!uiUpdatesActive) {
            return
        }
        preMeasurementLocationManager!!.removeUpdates(locationListener)
        uiUpdatesActive = false
    }

    /**
     * Adds a [JWT](https://jwt.io/) authentication token for a specific user to Android's account system.
     *
     * After the token has been added it starts periodic data synchronization if not yet active by calling
     * [WiFiSurveyor.startSurveillance].
     *
     * @param username The username of the user to add an auth token for.
     * @param token The auth token to add.
     * @throws SynchronisationException If no current Android Context is available
     */
    @Throws(SynchronisationException::class)  // Because sdk implementing apps (SR) use this to inject a token
    fun registerJWTAuthToken(username: String, token: String) {
        val accountManager: AccountManager = AccountManager.get(getContext())

        // The workflow here is slightly different from the one in CyfaceDataCapturingService.
        // If problems are reported, ensure they are exactly the same (i.e. reuse existing account).

        // Create a "dummy" account used for auto synchronization. Null password as the token is static
        val synchronizationAccount: Account = wiFiSurveyor.createAccount(username, null)

        // IntelliJ sometimes cannot resolve AUTH_TOKEN_TYPE: Invalidate cache & restart works.
        accountManager.setAuthToken(synchronizationAccount, AUTH_TOKEN_TYPE, token)
        wiFiSurveyor.startSurveillance(synchronizationAccount)
    }

    /**
     * Removes the [JWT](https://jwt.io/) auth token for a specific username from the system.
     *
     * This method calls [WiFiSurveyor.stopSurveillance] before removing the account as the surveillance expects
     * an account to be registered.
     *
     * If that username was not registered with [.registerJWTAuthToken] no account is removed.
     *
     * @param username The username of the user to remove the auth token for.
     * @throws SynchronisationException If no current Android Context is available
     */
    @Throws(SynchronisationException::class)  // Because sdk implementing apps (SR) use this to inject a token
    fun deregisterJWTAuthToken(username: String) {
        wiFiSurveyor.stopSurveillance()
        wiFiSurveyor.deleteAccount(username)
    }

    /**
     * Checks whether the user has granted the `ACCESS_COARSE_LOCATION` permission and notifies the UI to ask
     * for it if not.
     *
     * @param context Current `Activity` context.
     * @return Either `true` if permission was or has been granted; `false` otherwise.
     */
    private fun checkCoarseLocationAccess(context: Context): Boolean {
        val permissionAlreadyGranted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val uiListener = uiListener
        return if (!permissionAlreadyGranted && uiListener != null) {
            uiListener.onRequirePermission(
                Manifest.permission.ACCESS_COARSE_LOCATION, Reason(
                    "this app uses information about WiFi and cellular networks to display your position. Please provide your permission to track the networks you are currently using, to see your position on the map."
                )
            )
        } else {
            permissionAlreadyGranted
        }
    }

    /**
     * Starts the capturing process with a [DataCapturingListener], that is notified of important events occurring
     * while the capturing process is running.
     *
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the [StartUpFinishedHandler] to receive
     * a callback, when the service has been started.
     *
     * This method is thread safe to call.
     *
     * **ATTENTION:** If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     *
     * This wrapper avoids an unrecoverable state after the app crashed with an un[MeasurementStatus.FINISHED]
     * [Measurement]. "Dead" `MeasurementStatus#OPEN` and [MeasurementStatus.PAUSED] measurements are
     * then marked as `FINISHED`.
     *
     * @param modality The [Modality] type in use at this moment. If you have no way to know which kind of
     * `Modality` is in use, just use [Modality.UNKNOWN].
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws CursorIsNullException If [ContentProvider] was inaccessible.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a [UIListener] to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     */
    @Throws(
        DataCapturingException::class,
        MissingPermissionException::class,
        CursorIsNullException::class
    )  // This is called by the SDK implementing app to start a measurement
    override fun start(modality: Modality, finishedHandler: StartUpFinishedHandler) {
        try {
            super.start(modality, finishedHandler)
        } catch (e: CorruptedMeasurementException) {
            val corruptedMeasurements: MutableList<Measurement?> = ArrayList()
            val openMeasurements = persistenceLayer.loadMeasurements(MeasurementStatus.OPEN)
            val pausedMeasurements = persistenceLayer
                .loadMeasurements(MeasurementStatus.PAUSED)
            corruptedMeasurements.addAll(openMeasurements!!)
            corruptedMeasurements.addAll(pausedMeasurements!!)
            for (measurement in corruptedMeasurements) {
                Log.w(
                    Constants.TAG,
                    "Finishing corrupted measurement (mid " + measurement!!.id + ")."
                )
                try {
                    // Because of MOV-790 we disable the validation in setStatus and do this manually below
                    persistenceLayer.setStatus(measurement.id, MeasurementStatus.FINISHED, true)
                } catch (e1: NoSuchMeasurementException) {
                    throw IllegalStateException(e1)
                }
            }
            Validate.isTrue(!persistenceLayer.hasMeasurement(MeasurementStatus.OPEN))
            Validate.isTrue(!persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED))

            // Now try again to start Capturing - now there can't be any corrupted measurements
            try {
                super.start(modality, finishedHandler)
            } catch (e1: CorruptedMeasurementException) {
                throw IllegalStateException(e1)
            }
        }
    }
}