/*
 * Copyright 2018 Cyface GmbH
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
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.CorruptedMeasurementException;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.persistence.DefaultDistanceCalculationStrategy;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.model.Vehicle;
import de.cyface.synchronization.SynchronisationException;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;

/**
 * In implementation of the {@link DataCapturingService} as required inside the Movebis project.
 * <p>
 * This implementation provides access to location updates even outside of a running data capturing session. To start
 * these updates use {@link #startUILocationUpdates()}; to stop it use {@link #stopUILocationUpdates()}. It might be
 * necessary to provide a user interface asking the user for location access permissions. You can provide this user
 * interface using {@link UIListener#onRequirePermission(String, Reason)}. This method will be called with
 * <code>ACCESS_COARSE_LOCATION</code> and <code>ACCESS_FINE_LOCATION</code> permission requests.
 * <p>
 * Before you try to measure any data you should provide a valid JWT auth token for data synchronization. You may do
 * this using {@link #registerJWTAuthToken(String, String)} with a token for a certain username. To anonymize the user
 * is ok to use some garbage username here. If a user is no longer required, you can deregister it using
 * {@link #deregisterJWTAuthToken(String)}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 10.0.4
 * @since 2.0.0
 */
@SuppressWarnings({"unused", "WeakerAccess"}) // Sdk implementing apps (SR) use to create a DataCapturingService
public class MovebisDataCapturingService extends DataCapturingService {

    /**
     * A <code>LocationManager</code> that is used to provide location updates for the UI even if no capturing is
     * running.
     */
    private final LocationManager preMeasurementLocationManager;
    /**
     * A listener for location updates, which it passes through to the user interface.
     */
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final @NonNull Location location) {
            UIListener uiListener = getUiListener();
            if (uiListener != null) {
                uiListener.onLocationUpdate(location);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Nothing to do here.
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Nothing to do here.
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Nothing to do here.
        }
    };
    /**
     * The maximum rate of location updates to receive in milliseconds which are sent to the {@link UIListener}.
     * <p>
     * This only determines the updates sent to the {@code UIListener}, not the amount of locations captured for
     * {@link Measurement}s.
     * <p>
     * Set this to {@code 0L} if you would like to be notified as often as possible.
     */
    private final long locationUpdateRate;
    /**
     * A flag set if the locationListener for UI updates is active. This helps us to prevent to register such a listener
     * multiple times.
     */
    private boolean uiUpdatesActive;

    /**
     * Creates a new completely initialized {@link MovebisDataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service. This must be in the format "https://some.url/optional/resource".
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in milliseconds which are sent to the
     *            {@link UIListener}. This only determines the updates sent to the {@code #getUiListener}, not
     *            the amount of locations captured for {@link Measurement}s. Set this to {@code 0L} if you would like to
     *            be notified as often as possible.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     *             fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // Sdk implementing apps (SR) use this to create the DataCapturingService
    public MovebisDataCapturingService(@NonNull final Context context, @NonNull final String dataUploadServerAddress,
            @NonNull final UIListener uiListener, final long locationUpdateRate,
            @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DataCapturingListener capturingListener, final int sensorFrequency)
            throws SetupException, CursorIsNullException {
        this(context, "de.cyface.provider", "de.cyface", dataUploadServerAddress, uiListener, locationUpdateRate,
                eventHandlingStrategy, capturingListener, sensorFrequency);
    }

    /**
     * Creates a new completely initialized {@link MovebisDataCapturingService}.
     * This variant is required to test the ContentProvider.
     * <p>
     * <b>ATTENTION:</b> This constructor is only for testing to be able to inject authority and account type. Use
     * {@link MovebisDataCapturingService(Context, String, UIListener, long, EventHandlingStrategy,
     * DataCapturingListener)}
     * instead.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service. This must be in the format "https://some.url/optional/resource".
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in milliseconds which are sent to the
     *            {@link UIListener}. This only determines the updates sent to the {@code UIListener}, not the amount
     *            of locations captured for {@link Measurement}s. Set this to {@code 0L} if you would like to be
     *            notified as often as possible.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @param capturingListener A {@link DataCapturingListener} that is notified of important events during data
     *            capturing.
     * @param sensorFrequency The frequency in which sensor data should be captured. If this is higher than the maximum
     *            frequency the maximum frequency is used. If this is lower than the maximum frequency the system
     *            usually uses a frequency sightly higher than this value, e.g.: 101-103/s for 100 Hz.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     *             fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    MovebisDataCapturingService(@NonNull final Context context, @NonNull final String authority,
            @NonNull final String accountType, @NonNull final String dataUploadServerAddress,
            @NonNull final UIListener uiListener, final long locationUpdateRate,
            @NonNull final EventHandlingStrategy eventHandlingStrategy,
            @NonNull final DataCapturingListener capturingListener, final int sensorFrequency)
            throws SetupException, CursorIsNullException {
        super(context, authority, accountType, dataUploadServerAddress, eventHandlingStrategy,
                new PersistenceLayer<>(context, context.getContentResolver(), authority,
                        new CapturingPersistenceBehaviour()),
                new DefaultDistanceCalculationStrategy(), capturingListener, sensorFrequency);
        this.locationUpdateRate = locationUpdateRate;
        uiUpdatesActive = false;
        preMeasurementLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        if (preMeasurementLocationManager == null) {
            throw new SetupException("Unable to load location manager. Only got null!");
        }
        this.setUiListener(uiListener);
    }

    /**
     * Starts the reception of location updates for the user interface. No tracking is started with this method. This is
     * purely intended for display purposes. The received locations are forwarded to the {@link UIListener} provided to
     * the constructor.
     */
    @SuppressWarnings("WeakerAccess") // Because sdk implementing apps (SR) use this method to receive location updates
    @SuppressLint("MissingPermission") // Because we are checking the permission, but lint does not notice this.
    public void startUILocationUpdates() {
        if (uiUpdatesActive) {
            return;
        }
        boolean fineLocationAccessIsGranted = checkFineLocationAccess(getContext());
        if (fineLocationAccessIsGranted) {
            preMeasurementLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateRate, 0L,
                    locationListener);
            uiUpdatesActive = true;
        }

        boolean coarseLocationAccessIsGranted = checkCoarseLocationAccess(getContext());
        if (coarseLocationAccessIsGranted) {
            if (!preMeasurementLocationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                Log.w(TAG, "Network provider does not exist, not requesting UI location updates from that provider");
                return;
            }
            preMeasurementLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, locationUpdateRate,
                    0L, locationListener);
            uiUpdatesActive = true;
        }
    }

    /**
     * Stops reception of location updates for the user interface.
     *
     * @see #startUILocationUpdates()
     */
    @SuppressWarnings("WeakerAccess") // Because sdk implementing apps (SR) use this method to receive location updates
    public void stopUILocationUpdates() {
        if (!uiUpdatesActive) {
            return;
        }
        preMeasurementLocationManager.removeUpdates(locationListener);
        uiUpdatesActive = false;
    }

    /**
     * Adds a <a href="https://jwt.io/">JWT</a> authentication token for a specific user to Android's account system.
     * <p>
     * After the token has been added it starts periodic data synchronization if not yet active by calling
     * {@link WiFiSurveyor#startSurveillance(Account)}.
     *
     * @param username The username of the user to add an auth token for.
     * @param token The auth token to add.
     * @throws SynchronisationException If no current Android Context is available
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Because sdk implementing apps (SR) use this to inject a token
    public void registerJWTAuthToken(final @NonNull String username, final @NonNull String token)
            throws SynchronisationException {
        final AccountManager accountManager = AccountManager.get(getContext());

        // The workflow here is slightly different from the one in CyfaceDataCapturingService.
        // If problems are reported, ensure they are exactly the same (i.e. reuse existing account).

        // Create a "dummy" account used for auto synchronization. Null password as the token is static
        final Account synchronizationAccount = getWiFiSurveyor().createAccount(username, null);

        // IntelliJ sometimes cannot resolve AUTH_TOKEN_TYPE: Invalidate cache & restart works.
        accountManager.setAuthToken(synchronizationAccount, AUTH_TOKEN_TYPE, token);
        getWiFiSurveyor().startSurveillance(synchronizationAccount);
    }

    /**
     * Removes the <a href="https://jwt.io/">JWT</a> auth token for a specific username from the system.
     * <p>
     * This method calls {@link WiFiSurveyor#stopSurveillance()} before removing the account as the surveillance expects
     * an account to be registered.
     * <p>
     * If that username was not registered with {@link #registerJWTAuthToken(String, String)} no account is removed.
     *
     * @param username The username of the user to remove the auth token for.
     * @throws SynchronisationException If no current Android Context is available
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Because sdk implementing apps (SR) use this to inject a token
    public void deregisterJWTAuthToken(@NonNull final String username) throws SynchronisationException {

        getWiFiSurveyor().stopSurveillance();

        getWiFiSurveyor().deleteAccount(username);
    }

    /**
     * Checks whether the user has granted the <code>ACCESS_COARSE_LOCATION</code> permission and notifies the UI to ask
     * for it if not.
     * 
     * @param context Current <code>Activity</code> context.
     * @return Either <code>true</code> if permission was or has been granted; <code>false</code> otherwise.
     */
    private boolean checkCoarseLocationAccess(final @NonNull Context context) {
        boolean permissionAlreadyGranted = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        UIListener uiListener = getUiListener();
        if (!permissionAlreadyGranted && uiListener != null) {
            return uiListener.onRequirePermission(Manifest.permission.ACCESS_COARSE_LOCATION, new Reason(
                    "this app uses information about WiFi and cellular networks to display your position. Please provide your permission to track the networks you are currently using, to see your position on the map."));
        } else {
            return permissionAlreadyGranted;
        }
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
     * {@link Measurement}. It deletes the {@link Point3d}s of "dead" {@link MeasurementStatus#OPEN} measurements
     * because the {@link Point3d} counts gets lost during app crash. "Dead" {@code MeasurementStatus#OPEN} and
     * {@link MeasurementStatus#PAUSED} measurements are then marked as {@code FINISHED}.
     *
     * @param vehicle The {@link Vehicle} used to capture this data. If you have no way to know which kind of
     *            <code>Vehicle</code> was used, just use {@link Vehicle#UNKNOWN}.
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
    public void start(@NonNull Vehicle vehicle, @NonNull StartUpFinishedHandler finishedHandler)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {

        try {
            super.start(vehicle, finishedHandler);
        } catch (final CorruptedMeasurementException e) {
            final List<Measurement> corruptedMeasurements = new ArrayList<>();
            final List<Measurement> openMeasurements = this.persistenceLayer.loadMeasurements(MeasurementStatus.OPEN);
            final List<Measurement> pausedMeasurements = this.persistenceLayer
                    .loadMeasurements(MeasurementStatus.PAUSED);
            corruptedMeasurements.addAll(openMeasurements);
            corruptedMeasurements.addAll(pausedMeasurements);

            for (final Measurement measurement : corruptedMeasurements) {
                Log.w(TAG, "Finishing corrupted measurement (mid " + measurement.getIdentifier() + ").");
                try {
                    // Because of MOV-790 we disable the validation in setStatus and do this manually below
                    this.persistenceLayer.setStatus(measurement.getIdentifier(), MeasurementStatus.FINISHED, true);
                } catch (final NoSuchMeasurementException e1) {
                    throw new IllegalStateException(e);
                }
            }
            Validate.isTrue(!this.persistenceLayer.hasMeasurement(MeasurementStatus.OPEN));
            Validate.isTrue(!this.persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED));

            // Now try again to start Capturing - now there can't be any corrupted measurements
            try {
                super.start(vehicle, finishedHandler);
            } catch (final CorruptedMeasurementException e1) {
                throw new IllegalStateException(e1);
            }
        }
    }
}
