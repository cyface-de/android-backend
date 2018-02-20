package de.cyface.datacapturing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import de.cyface.datacapturing.exception.SetupException;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.datacapturing.ui.UIListener;

/**
 * In implementation of the {@link DataCapturingService} as required inside the Movebis project. This implementation
 * provides access to location updates even outside of a running data capturing session.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
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
        public void onLocationChanged(Location location) {
            uiListener.onLocationUpdate(location);
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
     * A listener for events which the UI might be interested in.
     */
    private final UIListener uiListener;

    /**
     * Creates a new completely initialized {@link MovebisDataCapturingService}.
     *
     * @param context The context (i.e. <code>Activity</code>) handling this service.
     * @param dataUploadServerAddress The server address running an API that is capable of receiving data captured by
     *            this service.
     * @param uiListener A listener for events which the UI might be interested in.
     * @param locationUpdateRate The maximum rate of location updates to receive in seconds. Set this to <code>0L</code>
     *            if you would like to be notified as often as possible.
     * @throws SetupException If Initialization of this service facade fails or writing the components preferences
     *             fails.
     */
    @SuppressLint("MissingPermission") // This is ok. We are checking the permission, but lint is too dump to notice.
    public MovebisDataCapturingService(final @NonNull Context context, final @NonNull String dataUploadServerAddress,
            final @NonNull UIListener uiListener, final long locationUpdateRate) throws SetupException {
        super(context, dataUploadServerAddress);
        preMeasurementLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        if (preMeasurementLocationManager == null) {
            throw new SetupException("Unable to load location manager. Only got null!");
        }
        this.uiListener = uiListener;

        boolean fineLocationAccessIsGranted = checkFineLocationAccess(context);
        boolean coarseLocationAccessIsGranted = checkCoarseLocationAccess(context);

        if (fineLocationAccessIsGranted) {
            preMeasurementLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateRate, 0L,
                    locationListener);
        }
        if (coarseLocationAccessIsGranted) {
            preMeasurementLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, locationUpdateRate,
                    0L, locationListener);
        }
    }

    /**
     * Checks whether the user has granted the <code>ACCESS_COARSE_LOCATION</code> permission and notifies the UI to ask
     * for it if not.
     * 
     * @param context Current <code>Activity</code> context.
     * @return Either <code>true</code> if permission was or has been granted; <code>false</code> otherwise.
     */
    private boolean checkCoarseLocationAccess(Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || uiListener.onRequirePermission(Manifest.permission.ACCESS_COARSE_LOCATION, new Reason(
                        "this app uses information about WiFi and cellular networks to display your position. Please provide your permission to track the networks you are currently using, to see your position on the map."));
    }

    /**
     * Checks whether the user has granted the <code>ACCESS_FINE_LOCATION</code> permission and notifies the UI to ask
     * for it if not.
     *
     * @param context Current <code>Activity</code> context.
     * @return Either <code>true</code> if permission was or has been granted; <code>false</code> otherwise.
     */
    private boolean checkFineLocationAccess(final @NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || uiListener.onRequirePermission(Manifest.permission.ACCESS_FINE_LOCATION, new Reason(
                        "This app uses GPS sensors to display your position. If you would like your position to be shown as exactly as possible please allow access to the GPS sensors."));
    }
}
