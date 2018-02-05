package de.cyface.datacapturing.backend;

import android.location.GpsStatus;
import android.location.LocationManager;

/**
 * Implementation of a <code>GeoLocationDeviceStatusHandler</code> used for devices with Android prior to Nougat (API 24).
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 * @see GnssStatusCallback
 */
public class GPSStatusListener extends GeoLocationDeviceStatusHandler {
    /**
     * The Android system listener wrapped by this class.
     */
    private final GpsStatus.Listener listener;

    /**
     * Creates a new completely intialized <code>GPSStatusListener</code>.
     *
     * @param manager The <code>LocationManager</code> used to get geo location status updates.
     * @throws SecurityException If permission to access location via GPS has not been granted.
     */
    GPSStatusListener(final LocationManager manager) throws SecurityException {
        super(manager);
        this.listener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                switch (event) {
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        handleSatteliteStatusChange();
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
