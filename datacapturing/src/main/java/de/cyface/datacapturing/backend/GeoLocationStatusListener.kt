package de.cyface.datacapturing.backend

import android.annotation.SuppressLint
import android.location.GpsStatus
import android.location.LocationManager

/**
 * Implementation of a `GeoLocationDeviceStatusHandler` used for devices with Android prior to Nougat (API
 * 24).
 *
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 1.0.0
 * @see GnssStatusCallback
 * @param manager The `LocationManager` used to get geo location status updates.
 * @throws SecurityException If fine location permission has not been granted.
 */
@SuppressLint("MissingPermission") // UI has to ensure permissions are available
class GeoLocationStatusListener internal constructor(manager: LocationManager?) :
    GeoLocationDeviceStatusHandler(manager!!) {
    /**
     * The Android system listener wrapped by this class.
     */
    private val listener = GpsStatus.Listener { event ->
        when (event) {
            GpsStatus.GPS_EVENT_SATELLITE_STATUS -> handleSatelliteStatusChange()
            GpsStatus.GPS_EVENT_FIRST_FIX -> handleFirstFix()
        }
    }

    init {
        locationManager.addGpsStatusListener(this.listener)
    }

    override fun shutdown() {
        locationManager.removeGpsStatusListener(listener)
    }
}
