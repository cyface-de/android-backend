package de.cyface.datacapturing.backend;

import android.annotation.TargetApi;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;

/**
 * Implementation for a <code>GeoLocationDeviceStatusHandler</code> for version above and including Android Nougat (API 24).
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 * @see GPSStatusListener
 */
@TargetApi(Build.VERSION_CODES.N)
public class GnssStatusCallback extends GeoLocationDeviceStatusHandler {
    /**
     * Callback that is notified of GPS sensor status changes.
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
     *
     * @param manager The <code>LocationManager</code> used by this class to get update about GPS status changes.
     * @throws SecurityException If permission to access location via GPS has not been granted.
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
