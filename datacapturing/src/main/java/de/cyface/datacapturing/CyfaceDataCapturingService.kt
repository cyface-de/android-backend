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
package de.cyface.datacapturing

import android.content.Context
import android.util.Log
import de.cyface.datacapturing.backend.SensorCapture
import de.cyface.datacapturing.backend.SensorCaptureEnabled
import de.cyface.datacapturing.exception.CorruptedMeasurementException
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.SetupException
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.strategy.DefaultDistanceCalculation
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.persistence.strategy.DistanceCalculationStrategy
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.synchronization.LoginActivityProvider
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.Validate

/**
 * An implementation of a `DataCapturingService` using a dummy Cyface account for data synchronization.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 15.0.0
 * @since 2.0.0
 * @param context The context (i.e. `Activity`) handling this service.
 * @param authority The `ContentProvider` authority used to identify the content provider used by this
 * `DataCapturingService`. You should use something world wide unique, like your domain, to
 * avoid collisions between different apps using the Cyface SDK.
 * @param accountType The type of the account to use to synchronize data.
 * @param eventHandlingStrategy The [EventHandlingStrategy] used to react to selected events
 * triggered by the [DataCapturingBackgroundService].
 * @param distanceCalculationStrategy The [DistanceCalculationStrategy] used to calculate the
 * [Measurement.getDistance]
 * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
 * [ParcelableGeoLocation]s
 * @param capturingListener A [DataCapturingListener] that is notified of important events during data
 * capturing.
 * @param sensorCapture The [SensorCapture] implementation which decides if sensor data should
 * be captured.
 */
@Suppress("unused") // Used by SDK implementing apps (CY)
class CyfaceDataCapturingService private constructor(
    context: Context,
    authority: String,
    accountType: String,
    eventHandlingStrategy: EventHandlingStrategy,
    distanceCalculationStrategy: DistanceCalculationStrategy,
    locationCleaningStrategy: LocationCleaningStrategy,
    capturingListener: DataCapturingListener,
    sensorCapture: SensorCapture,
    loginActivityProvider: LoginActivityProvider,
) : DataCapturingService(
    context,
    authority,
    accountType,
    eventHandlingStrategy,
    DefaultPersistenceLayer(context, CapturingPersistenceBehaviour()),
    distanceCalculationStrategy,
    locationCleaningStrategy,
    capturingListener,
    sensorCapture,
) {
    init {
        checkNotNull(loginActivityProvider.getLoginActivity()) { "No LOGIN_ACTIVITY was set from the SDK using app." }
    }

    /**
     * Creates a new completely initialized [DataCapturingService].
     *
     * @param context The context (i.e. `Activity`) handling this service.
     * @param authority The `ContentProvider` authority used to identify the content provider used by this
     * `DataCapturingService`. You should use something world wide unique, like your domain, to
     * avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param eventHandlingStrategy The [EventHandlingStrategy] used to react to selected events
     * triggered by the [DataCapturingBackgroundService].
     * @param capturingListener A [DataCapturingListener] that is notified of important events during data
     * capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     * frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     * usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     */
    // Used by SDK implementing apps (CY)
    constructor(
        context: Context,
        authority: String,
        accountType: String,
        eventHandlingStrategy: EventHandlingStrategy,
        capturingListener: DataCapturingListener,
        sensorFrequency: Int,
        loginActivityProvider: LoginActivityProvider,
    ) : this(
        context,
        authority,
        accountType,
        eventHandlingStrategy,
        DefaultDistanceCalculation(),
        DefaultLocationCleaning(),
        capturingListener,
        SensorCaptureEnabled(sensorFrequency),
        loginActivityProvider,
    )

    /**
     * Frees up resources used by CyfaceDataCapturingService
     *
     * @throws SynchronisationException if no current Android [Context] is available
     */
    @Suppress("unused") // This is called by the SDK implementing app in its onDestroyView
    @Throws(SynchronisationException::class)
    fun shutdownDataCapturingService() {
        wiFiSurveyor.stopSurveillance()
        shutdownConnectionStatusReceiver()
    }

    /**
     * Starts a `WifiSurveyor`. A synchronization account must be available at that time.
     *
     * @throws SetupException when no account is available.
     */
    @Suppress("unused") // This is called by the SDK implementing app after an account was created
    @Throws(SetupException::class)
    fun startWifiSurveyor() {
        try {
            // We require SDK users (other than SR) to always have exactly one account available
            val account = wiFiSurveyor.account
            wiFiSurveyor.startSurveillance(account)
        } catch (e: SynchronisationException) {
            throw SetupException(e)
        }
    }

    /**
     * Removes the account for a specific username from the system.
     *
     *
     * This method calls [WiFiSurveyor.stopSurveillance] before removing the account as the surveillance expects
     * an account to be registered.
     *
     *
     * If no account exists with that username, no account is removed.
     *
     * @param username The username of the user to remove the auth token for.
     * @throws SynchronisationException If no current Android Context is available
     */
    @Suppress("unused") // Used by sdk implementing apps (CY)
    @Throws(SynchronisationException::class)
    fun removeAccount(username: String) {
        wiFiSurveyor.stopSurveillance()

        wiFiSurveyor.deleteAccount(username)
    }

    /**
     * Starts the capturing process with a [DataCapturingListener], that is notified of important events occurring
     * while the capturing process is running.
     *
     *
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the [StartUpFinishedHandler] to receive
     * a callback, when the service has been started.
     *
     *
     * This method is thread safe to call.
     *
     *
     * **ATTENTION:** If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     *
     *
     * This wrapper avoids an unrecoverable state after the app crashed with an un[MeasurementStatus.FINISHED]
     * [Measurement]. "Dead" `MeasurementStatus#OPEN` and [MeasurementStatus.PAUSED] measurements are
     * then marked as `FINISHED`.
     *
     * @param modality The [Modality] used to capture this data. If you have no way to know which kind of
     * `Modality` was used, just use [Modality.UNKNOWN].
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a [UIListener] to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     */
    @Suppress("unused") // This is called by the SDK implementing app to start a measurement
    @Throws(DataCapturingException::class, MissingPermissionException::class)
    override fun start(modality: Modality, finishedHandler: StartUpFinishedHandler) {
        try {
            super.start(modality, finishedHandler)
        } catch (e: CorruptedMeasurementException) {
            val corruptedMeasurements: MutableList<Measurement> = ArrayList()
            val openMeasurements = persistenceLayer.loadMeasurements(MeasurementStatus.OPEN)
            val pausedMeasurements = persistenceLayer
                .loadMeasurements(MeasurementStatus.PAUSED)
            corruptedMeasurements.addAll(openMeasurements)
            corruptedMeasurements.addAll(pausedMeasurements)

            for ((id) in corruptedMeasurements) {
                Log.w(Constants.TAG, "Finishing corrupted measurement (mid $id).")
                try {
                    // Because of MOV-790 we disable the validation in setStatus and do this manually below
                    persistenceLayer.setStatus(id, MeasurementStatus.FINISHED, true)
                } catch (e1: NoSuchMeasurementException) {
                    throw IllegalStateException(e)
                }
            }
            Validate.isTrue(!persistenceLayer.hasMeasurement(MeasurementStatus.OPEN))
            Validate.isTrue(!persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED))
            persistenceLayer.persistenceBehaviour!!.resetIdentifierOfCurrentlyCapturedMeasurement()

            // Now try again to start Capturing - now there can't be any corrupted measurements
            try {
                super.start(modality, finishedHandler)
            } catch (e1: CorruptedMeasurementException) {
                throw IllegalStateException(e1)
            }
        }
    }
}
