package de.cyface.datacapturing.de.cyface.datacapturing.backend;

import android.location.GpsStatus;
import android.location.LocationManager;

/**
 * Created by muthmann on 29.01.18.
 */

public class GpsStatusListener extends GpsStatusHandler {
    private final GpsStatus.Listener listener;
    private final LocationManager manager;

    public GpsStatusListener(final LocationManager mananger) throws SecurityException {
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
        this.manager = mananger;
        mananger.addGpsStatusListener(this.listener);
    }

    @Override
    public void shutdown() {
        manager.removeGpsStatusListener(listener);
    }
}
