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

import android.location.LocationManager

/**
 * Abstract base class for classes informing the system about the current state of the geo location
 * device. It reacts to fix events and if those events occur often enough it tells its
 * `CapturingProcessListener`s about the state change.
 *
 * @author Klemens Muthmann
 * @version 2.0.3
 * @since 1.0.0
 * @param locationManager The Android service used to get location status updates.
 */
abstract class GeoLocationDeviceStatusHandler(val locationManager: LocationManager) {
    /**
     * `true` if the service has a geo location fix; `false` otherwise.
     */
    private var hasGeoLocationFix = false

    /**
     * Time of last location update. This is required to check whether fixes occur as often as desired.
     */
    private var timeOfLastLocationUpdate: Long = 0

    /**
     * The `List` of listeners to inform about geo location updates.
     */
    private val listener: MutableCollection<CapturingProcessListener> = mutableListOf()

    /**
     * Adds all the listeners from the provided `List` to this objects list of listeners that are informed
     * about geo location device status updates.
     *
     * @param listener A `List` of listeners that are interested of geo location status changes.
     */
    fun setDataCapturingListener(listener: Collection<CapturingProcessListener>?) {
        requireNotNull(listener) { "Illegal argument: listener was null!" }

        this.listener.addAll(listener)
    }

    /**
     * @return `true` if the service has a geo location fix; `false` otherwise.
     */
    open fun hasLocationFix(): Boolean {
        return hasGeoLocationFix
    }

    /**
     * Resets the time for the last location update to a new value.
     *
     * @param timeOfLastLocationUpdate The new time of a last location update.
     */
    fun setTimeOfLastLocationUpdate(timeOfLastLocationUpdate: Long) {
        this.timeOfLastLocationUpdate = timeOfLastLocationUpdate
    }

    /**
     * Tells the system that this `GeoLocationDeviceStatusHandler` is going down and no longer
     * interested about geo location device status updates. This method should be called when the
     * system shuts down to free up resources.
     */
    abstract fun shutdown()

    /**
     * Called each time the service receives an update from the geo location satellites.
     */
    fun handleSatelliteStatusChange() {
        // If time of last location update was less then 2 seconds we still have a fix.
        val timePassedSinceLastSatelliteUpdate =
            System.currentTimeMillis() - timeOfLastLocationUpdate
        hasGeoLocationFix =
            timePassedSinceLastSatelliteUpdate < MAX_TIME_SINCE_LAST_SATELLITE_UPDATE
    }

    /**
     * Android provides a special callback for the first geo location fix. This is handled by this method.
     */
    fun handleFirstFix() {
        hasGeoLocationFix = true
        handleLocationFixEvent()
    }

    /**
     * Informs all listeners if fix has been lost or is still available.
     */
    private fun handleLocationFixEvent() {
        if (hasGeoLocationFix) {
            for (listener in this.listener) {
                listener.onLocationFix()
            }
        } else {
            for (listener in this.listener) {
                listener.onLocationFixLost()
            }
        }
    }

    companion object {
        /**
         * Interval in which location updates need to occur for the device to consider itself
         * having a fix. Reasoning behind this number is the following: Usually the geo location
         * device provides updates every second, give or take a few milliseconds. According to
         * sampling theorem we could guarantee updates every 2 seconds if a proper fix is available.
         */
        private const val MAX_TIME_SINCE_LAST_SATELLITE_UPDATE = 2000
    }
}
