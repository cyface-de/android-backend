package de.cyface.datacapturing.backend;

import android.annotation.TargetApi;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;

/**
 * Implementation for a <code>GeoLocationDeviceStatusHandler</code> for version above and including Android Nougat (API 24).
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 * @see GeoLocationStatusListener
 */
@TargetApi(Build.VERSION_CODES.N)
public class GnssStatusCallback extends GeoLocationDeviceStatusHandler {
    /**
     * Callback that is notified of Gnss receiver status changes.
     */
    private final GnssStatus.Callback callback = new GnssStatus.Callback() {
        @Override
        public void onFirstFix(int ttffMillis) {
            handleFirstFix();
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            handleSatteliteStatusChange();
        }
    };

    /**
     * Creates a new completely initialized <code>GnssStatusCallback</code>.
     * <p>
     * Requires the ACCESS_FINE_LOCATION permission.
     *
     * @param manager The <code>LocationManager</code> used by this class to get update about GNSS status changes.
     * @throws SecurityException If permission to access location via GNSS has not been granted.
     */
    GnssStatusCallback(final LocationManager manager) throws SecurityException {
        super(manager);
        locationManager.registerGnssStatusCallback(callback);
    }

    @Override
    public void shutdown() {
        locationManager.unregisterGnssStatusCallback(callback);
    }
}
