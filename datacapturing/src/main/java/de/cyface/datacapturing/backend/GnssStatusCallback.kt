package de.cyface.datacapturing.backend

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.LocationManager

/**
 * Implementation for a `GeoLocationDeviceStatusHandler` for version above and including Android Nougat (API 24).
 *
 * @author Klemens Muthmann
 * @version 1.0.3
 * @since 1.0.0
 * @param manager The `LocationManager` used by this class to get update about GNSS status changes.
 * @throws SecurityException If permission to access location via GNSS has not been granted.
 */
@SuppressLint("MissingPermission") // UI needs to ensure permissions exists
class GnssStatusCallback internal constructor(manager: LocationManager?) :
    GeoLocationDeviceStatusHandler(manager!!) {
    /**
     * Callback that is notified of Gnss receiver status changes.
     */
    private val callback: GnssStatus.Callback = object : GnssStatus.Callback() {
        override fun onFirstFix(ttffMillis: Int) {
            handleFirstFix()
        }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            handleSatelliteStatusChange()
        }
    }

    /**
     * Requires the ACCESS_FINE_LOCATION permission.
     */
    init {
        locationManager.registerGnssStatusCallback(callback)
    }

    override fun shutdown() {
        locationManager.unregisterGnssStatusCallback(callback)
    }
}
