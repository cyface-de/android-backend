/*
 * Copyright 2017-2025 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.datacapturing.backend

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.LocationManager

/**
 * Implementation for a `GeoLocationDeviceStatusHandler` for version above and including Android Nougat (API 24).
 *
 * @author Klemens Muthmann
 * @version 1.0.4
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
