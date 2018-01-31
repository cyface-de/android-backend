package de.cyface.datacapturing.backend;

import android.annotation.TargetApi;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;

/**
 * Created by muthmann on 29.01.18.
 */
@TargetApi(Build.VERSION_CODES.N)
public class GnssStatusCallback extends GPSStatusHandler {
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

    private final LocationManager manager;

    GnssStatusCallback(final LocationManager manager) throws SecurityException {
        this.manager = manager;
        manager.registerGnssStatusCallback(callback);
    }

    @Override
    public void shutdown() {
        manager.unregisterGnssStatusCallback(callback);
    }
}
