package de.cyface.datacapturing.backend

import android.location.LocationManager

/**
 * Abstract base class for classes informing the system about the current state of the geo location device. It reacts to
 * fix events and if those events occur often enough it tells its `CapturingProcessListener`s about the state
 * change.
 *
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 1.0.0
 */
internal abstract class GeoLocationDeviceStatusHandler(locationManager: LocationManager?) {
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
    private val listener: MutableCollection<CapturingProcessListener> = ArrayList()

    /**
     * The `LocationManager` used to get geo location status updates.
     */
    val locationManager: LocationManager

    /**
     * Creates a new completely initialized `GeoLocationDeviceStatusHandler`.
     *
     * @param locationManager The `LocationManager` used to get geo location status updates.
     */
    init {
        requireNotNull(locationManager) { "Illegal argument: locationManager was null!" }

        this.locationManager = locationManager
    }

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
     * Tells the system that this `GeoLocationDeviceStatusHandler` is going down and no longer interested
     * about geo location device status updates. This method should be called when the system shuts down to free up
     * resources.
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
         * Interval in which location updates need to occur for the device to consider itself having a fix. Reasoning behind
         * this number is the following: Usually the geo location device provides updates every second, give or take a few
         * milliseconds. According to sampling theorem we could guarantee updates every 2 seconds if a proper fix is
         * available.
         */
        private const val MAX_TIME_SINCE_LAST_SATELLITE_UPDATE = 2000
    }
}
