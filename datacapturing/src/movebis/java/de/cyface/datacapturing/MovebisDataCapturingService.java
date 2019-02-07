package de.cyface.datacapturing;

import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

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
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;
import de.cyface.synchronization.SynchronisationException;
import de.cyface.utils.CursorIsNullException;

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
 * this using {@link #registerJWTAuthToken(String, String)} with a token for a certain username. For anonymization it
 * is ok to use some garbage username here. If a user is no longer required, you can deregister it using
 * {@link #deregisterJWTAuthToken(String)}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
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
     * The maximum rate of location updates to receive in seconds. Set this to <code>0L</code>
     * if you would like to be notified as often as possible.
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
     *            this service.
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in seconds. Set this to <code>0L</code>
     *            if you would like to be notified as often as possible.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     *             fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    @SuppressWarnings("WeakerAccess") // Sdk implementing apps (SR) use this to create the DataCapturingService
    public MovebisDataCapturingService(final @NonNull Context context, final @NonNull String dataUploadServerAddress,
            final @NonNull UIListener uiListener, final long locationUpdateRate,
            @NonNull final EventHandlingStrategy eventHandlingStrategy) throws SetupException, CursorIsNullException {
        this(context, "de.cyface.provider", "de.cyface", dataUploadServerAddress, uiListener, locationUpdateRate,
                eventHandlingStrategy);
    }

    /**
     * Creates a new completely initialized {@link MovebisDataCapturingService}.
     * This variant is required to test the ContentProvider.
     *
     * ATTENTION: This constructor is only for testing to be able to inject authority and account type. Use
     * {@link MovebisDataCapturingService#MovebisDataCapturingService(Context, String, UIListener, long, EventHandlingStrategy)}
     * instead.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param authority The <code>ContentProvider</code> authority used to identify the content provider used by this
     *            <code>DataCapturingService</code>. You should use something world wide unique, like your domain, to
     *            avoid collisions between different apps using the Cyface SDK.
     * @param accountType The type of the account to use to synchronize data.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in seconds. Set this to <code>0L</code>
     *            if you would like to be notified as often as possible.
     * @param eventHandlingStrategy The {@link EventHandlingStrategy} used to react to selected events
     *            triggered by the {@link DataCapturingBackgroundService}.
     * @throws SetupException If initialization of this service facade fails or writing the components preferences
     *             fails.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     */
    MovebisDataCapturingService(final @NonNull Context context, final @NonNull String authority,
            final @NonNull String accountType, final @NonNull String dataUploadServerAddress,
            final @NonNull UIListener uiListener, final long locationUpdateRate,
            @NonNull final EventHandlingStrategy eventHandlingStrategy) throws SetupException, CursorIsNullException {
        super(context, context.getContentResolver(), authority, accountType, dataUploadServerAddress,
                eventHandlingStrategy);
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
     * After the token has been added it starts periodic data synchronization if not yet active.
     *
     * @param username The username of the user to add an auth token for.
     * @param token The auth token to add.
     * @throws SynchronisationException If unable to create an appropriate account with the Android account system.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Because sdk implementing apps (SR) use this to inject a token
    public void registerJWTAuthToken(final @NonNull String username, final @NonNull String token)
            throws SynchronisationException {
        AccountManager accountManager = AccountManager.get(getContext());

        Account synchronizationAccount = getWiFiSurveyor().getOrCreateAccount(username);

        accountManager.setAuthToken(synchronizationAccount, AUTH_TOKEN_TYPE, token);
        getWiFiSurveyor().startSurveillance(synchronizationAccount);
    }

    /**
     * Removes the <a href="https://jwt.io/">JWT</a> auth token for a specific username from the system. If that
     * username was not registered with {@link #registerJWTAuthToken(String, String)} this method simply does nothing.
     *
     * @param username The username of the user to remove the auth token for.
     */
    @SuppressWarnings({"WeakerAccess", "unused"}) // Because sdk implementing apps (SR) use this to inject a token
    public void deregisterJWTAuthToken(final @NonNull String username) {
        getWiFiSurveyor().deleteAccount(username);
    }

    /*
     * Uncommented as this seems not to be used by SR.
     * Sets whether this <code>MovebisDataCapturingService</code> should synchronize data only on WiFi or on all data
     * connections.
     * @param state If <code>true</code> the <code>MovebisDataCapturingService</code> synchronizes data only if
     * connected to a WiFi network; if <code>false</code> it synchronizes as soon as a data connection is
     * available. The second option might use up the users data plan rapidly so use it sparingly.
     * /
     * public void syncOnWiFiOnly(final boolean state) {
     * getWiFiSurveyor().syncOnWiFiOnly(state);
     * }
     */

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
}
