package de.cyface.datacapturing.backend;

import android.location.GpsStatus;
import android.location.LocationManager;

/**
 * Implementation of a <code>GeoLocationDeviceStatusHandler</code> used for devices with Android prior to Nougat (API
 * 24).
 *
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 1.0.0
 * @see GnssStatusCallback
 */
public class GeoLocationStatusListener extends GeoLocationDeviceStatusHandler {
    /**
     * The Android system listener wrapped by this class.
     */
    private final GpsStatus.Listener listener;

    /**
     * Creates a new completely initialized <code>GeoLocationStatusListener</code>.
     *
     * @param manager The <code>LocationManager</code> used to get geo location status updates.
     * @throws SecurityException If fine location permission has not been granted.
     */
    GeoLocationStatusListener(final LocationManager manager) throws SecurityException {
        super(manager);
        this.listener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                switch (event) {
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        handleSatelliteStatusChange();
                        break;
                    case GpsStatus.GPS_EVENT_FIRST_FIX:
                        handleFirstFix();
                        break;
                }
            }
        };
        locationManager.addGpsStatusListener(this.listener);
    }

    @Override
    public void shutdown() {
        locationManager.removeGpsStatusListener(listener);
    }
}
