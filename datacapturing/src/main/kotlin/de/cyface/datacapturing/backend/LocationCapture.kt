package de.cyface.datacapturing.backend

import android.annotation.SuppressLint
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread

/**
 * Handles geo-location capturing.
 */
class LocationCapture {
    /**
     * The Android service that provides updates about location changes from the location provider.
     */
    @Transient private lateinit var service: LocationManager

    /**
     * Status handler that informs listeners about geo-location device status changes like GNSS
     * "fix" and "no-fix". The device is in this case a location provider.
     */
    @Transient private lateinit var statusHandler: GeoLocationDeviceStatusHandler

    /**
     * A `HandlerThread` to handle new capture events in the background without blocking the calling
     * thread. This is based on information from https://stackoverflow.com/q/6069485/5815054.
     */
    @Transient private var eventHandlerThread: HandlerThread? = null

    /**
     * Sets up the Android service for data capturing.
     *
     * @param locationManager The service to register the data capturing from.
     * @param statusHandler Status handler that informs listeners about geo-location device status
     * changes like GNSS "fix" and "no-fix". The device is in this case a location provider.
     */
    internal fun setup(
        locationManager: LocationManager,
        statusHandler: GeoLocationDeviceStatusHandler,
    ) {
        this.service = locationManager
        this.statusHandler = statusHandler
    }

    /**
     * Registers the requested location data.
     *
     * @param listener The listener to inform about sensor capture events.
     * @throws `SecurityException` If user did not provide permission to access geo location.
     */
    @SuppressLint("MissingPermission") // UI has to handle permission request before starting this
    fun register(listener: LocationListener) {
        this.eventHandlerThread = HandlerThread("de.cyface.location_event").apply { start() }

        this.service.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            listener,
            eventHandlerThread!!.looper,
        )
    }

    /**
     * see [GeoLocationDeviceStatusHandler.setDataCapturingListener]
     */
    fun setDataCapturingListener(listener: MutableCollection<CapturingProcessListener>) {
        statusHandler.setDataCapturingListener(listener)
    }

    /**
     * see [GeoLocationDeviceStatusHandler.setTimeOfLastLocationUpdate]
     */
    fun setTimeOfLastLocationUpdate(timestamp: Long) {
        statusHandler.setTimeOfLastLocationUpdate(timestamp)
    }

    /**
     * see [GeoLocationDeviceStatusHandler.hasLocationFix]
     */
    fun hasLocationFix(): Boolean {
        return statusHandler.hasLocationFix()
    }

    /**
     * Cleans up resources related to location capturing.
     *
     * @param listener The listener to unregister from the Android service.
     */
    fun cleanup(listener: LocationListener) {
        service.removeUpdates(listener)
        statusHandler.shutdown()
        eventHandlerThread?.quitSafely()
    }
}
