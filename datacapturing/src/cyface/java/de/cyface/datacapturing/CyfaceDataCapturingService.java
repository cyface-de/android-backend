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
package de.cyface.datacapturing;

import static de.cyface.datacapturing.Constants.TAG;
import static de.cyface.synchronization.CyfaceAuthenticator.LOGIN_ACTIVITY;

import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.DefaultPersistenceLayer;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.strategy.DefaultDistanceCalculation;
import de.cyface.persistence.strategy.DefaultLocationCleaning;
import de.cyface.persistence.strategy.DistanceCalculationStrategy;
import de.cyface.persistence.strategy.LocationCleaningStrategy;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.synchronization.exception.SynchronisationException;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * An implementation of a <code>DataCapturingService</code> using a dummy Cyface account for data synchronization.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 12.0.2
 * @since 2.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess", "RedundantSuppression"}) // Used by SDK implementing apps (CY)
public final class CyfaceDataCapturingService extends DataCapturingService {

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param resolver Resolver used to access the content provider for storing measurements.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service. This must be in the format "https://some.url/optional/resource".
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param distanceCalculationStrategy The {@link DistanceCalculationStrategy} used to calculate the
     *            {@link Measurement#getDistance()}
     * @param locationCleaningStrategy The {@link LocationCleaningStrategy} used to filter the
     *            {@link ParcelableGeoLocation}s
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     */
    private CyfaceDataCapturingService(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final String accountType,
            @NonNull final String dataUploadServerAddress, @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DistanceCalculationStrategy distanceCalculationStrategy,
            @NonNull final LocationCleaningStrategy locationCleaningStrategy,
            @NonNull final DataCapturingListener capturingListener, final int sensorFrequency) {
        super(context, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new DefaultPersistenceLayer<>(context, authority, new CapturingPersistenceBehaviour()),
                distanceCalculationStrategy, locationCleaningStrategy, capturingListener, sensorFrequency);
        if (LOGIN_ACTIVITY == null) {
            throw new IllegalStateException("No LOGIN_ACTIVITY was set from the SDK using app.");
        }
    }

    /**
     * Creates a new completely initialized {@link DataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param resolver Resolver used to access the content provider for storing measurements.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service. This must be in the format "https://some.url/optional/resource".
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If writing the components preferences or registering the dummy user account fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"}) // Used by SDK implementing apps (CY)
    public CyfaceDataCapturingService(@NonNull final Context context, @NonNull final ContentResolver resolver,
            @NonNull final String authority, @NonNull final String accountType,
            @NonNull final String dataUploadServerAddress, @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DataCapturingListener capturingListener, final int sensorFrequency)
            throws SetupException, CursorIsNullException {
        this(context, resolver, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new DefaultDistanceCalculation(), new DefaultLocationCleaning(), capturingListener,
                sensorFrequency);
    }

    /**
     * Frees up resources used by CyfaceDataCapturingService
     * 
     * @throws SynchronisationException if no current Android {@link Context} is available
     */
    @SuppressWarnings("unused") // This is called by the SDK implementing app in its onDestroyView
    public void shutdownDataCapturingService() throws SynchronisationException {

        getWiFiSurveyor().stopSurveillance();
        shutdownConnectionStatusReceiver();
    }

    /**
     * Starts a <code>WifiSurveyor</code>. A synchronization account must be available at that time.
     *
     * @throws SetupException when no account is available.
     */
    @SuppressWarnings("unused") // This is called by the SDK implementing app after an account was created
    public void startWifiSurveyor() throws SetupException {
        try {
            // We require SDK users (other than Movebis) to always have exactly one account available
            final Account account = getWiFiSurveyor().getAccount();
            getWiFiSurveyor().startSurveillance(account);
        } catch (SynchronisationException e) {
            throw new SetupException(e);
        }
    }

    /**
     * Removes the account for a specific username from the system.
     * <p>
     * This method calls {@link WiFiSurveyor#stopSurveillance()} before removing the account as the surveillance expects
     * an account to be registered.
     * <p>
     * If no account exists with that username, no account is removed.
     *
     * @param username The username of the user to remove the auth token for.
     * @throws SynchronisationException If no current Android Context is available
     */
    @SuppressWarnings({"unused"}) // Used by sdk implementing apps (CY)
    public void removeAccount(@NonNull final String username) throws SynchronisationException {

        getWiFiSurveyor().stopSurveillance();

        getWiFiSurveyor().deleteAccount(username);
    }

    /**
     * Starts the capturing process with a {@link DataCapturingListener}, that is notified of important events occurring
     * while the capturing process is running.
     * <p>
     * This is an asynchronous method. This method returns as soon as starting the service was initiated. You may not
     * assume the service is running, after the method returns. Please use the {@link StartUpFinishedHandler} to receive
     * a callback, when the service has been started.
     * <p>
     * This method is thread safe to call.
     * <p>
     * <b>ATTENTION:</b> If there are errors while starting the service, your handler might never be called. You may
     * need to apply some timeout mechanism to not wait indefinitely.
     * <p>
     * This wrapper avoids an unrecoverable state after the app crashed with an un{@link MeasurementStatus#FINISHED}
     * {@link Measurement}. "Dead" {@code MeasurementStatus#OPEN} and {@link MeasurementStatus#PAUSED} measurements are
     * then marked as {@code FINISHED}.
     *
     * @param modality The {@link Modality} used to capture this data. If you have no way to know which kind of
     *            <code>Modality</code> was used, just use {@link Modality#UNKNOWN}.
     * @param finishedHandler A handler called if the service started successfully.
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     *             Android context was available.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     */
    @Override
    @SuppressWarnings("unused") // This is called by the SDK implementing app to start a measurement
    public void start(@NonNull Modality modality, @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {

        try {
            super.start(modality, finishedHandler);
        } catch (final CorruptedMeasurementException e) {
            final List<Measurement> corruptedMeasurements = new ArrayList<>();
            final List<Measurement> openMeasurements = this.persistenceLayer.loadMeasurements(MeasurementStatus.OPEN);
            final List<Measurement> pausedMeasurements = this.persistenceLayer
                    .loadMeasurements(MeasurementStatus.PAUSED);
            corruptedMeasurements.addAll(openMeasurements);
            corruptedMeasurements.addAll(pausedMeasurements);

            for (final Measurement measurement : corruptedMeasurements) {
                Log.w(TAG, "Finishing corrupted measurement (mid " + measurement.getId() + ").");
                try {
                    // Because of MOV-790 we disable the validation in setStatus and do this manually below
                    this.persistenceLayer.setStatus(measurement.getId(), MeasurementStatus.FINISHED, true);
                } catch (NoSuchMeasurementException e1) {
                    throw new IllegalStateException(e);
                }
            }
            Validate.isTrue(!this.persistenceLayer.hasMeasurement(MeasurementStatus.OPEN));
            Validate.isTrue(!this.persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED));
            this.persistenceLayer.getPersistenceBehaviour().resetIdentifierOfCurrentlyCapturedMeasurement();

            // Now try again to start Capturing - now there can't be any corrupted measurements
            try {
                super.start(modality, finishedHandler);
            } catch (final CorruptedMeasurementException e1) {
                throw new IllegalStateException(e1);
            }
        }
    }
}
