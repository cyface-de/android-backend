/*
 * Copyright 2021-2025 Cyface GmbH
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
package de.cyface.datacapturing.persistence

import android.util.Log
import de.cyface.datacapturing.Constants
import de.cyface.datacapturing.model.CapturedData
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.PersistenceBehaviour
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Pressure
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.serializer.model.Point3DType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * This [PersistenceBehaviour] is used when a [DefaultPersistenceLayer] is used to capture a
 * [Measurement]s.
 *
 * @author Armin Schnabel
 * @version 2.1.3
 * @since 3.0.0
 */
class CapturingPersistenceBehaviour : PersistenceBehaviour {
    /**
     * A threadPool to execute operations on their own background threads.
     */
    private var threadPool: ExecutorService? = null

    /**
     * Caching the current [Measurement], so we do not need to ask the database each time we require the
     * current measurement. This is `null` if there is no running measurement or if we lost the
     * cache due to Android stopping the application hosting the data capturing service.
     */
    private var currentMeasurementIdentifier: Long? = null

    /**
     * The file to write the acceleration points to.
     */
    private var accelerationsFile: Point3DFile? = null

    /**
     * The file to write the rotation points to.
     */
    private var rotationsFile: Point3DFile? = null

    /**
     * The file to write the direction points to.
     */
    private var directionsFile: Point3DFile? = null

    /**
     * A reference to the [DefaultPersistenceLayer] which implements this behaviour to access it's methods.
     */
    private lateinit var persistenceLayer: DefaultPersistenceLayer<*>

    private val mutex = Mutex()

    override fun onStart(persistenceLayer: DefaultPersistenceLayer<*>) {
        this.persistenceLayer = persistenceLayer
        threadPool = Executors.newCachedThreadPool()
    }

    override fun onNewMeasurement(measurementId: Long) {
        currentMeasurementIdentifier = measurementId
    }

    override fun shutdown() {
        if (threadPool != null) {
            try {
                threadPool!!.shutdown()
                threadPool!!.awaitTermination(1, TimeUnit.SECONDS)
                threadPool!!.shutdownNow()
            } catch (e: InterruptedException) {
                throw IllegalStateException(e)
            }
        }
    }

    /**
     * Saves the provided [CapturedData] to the local persistent storage of the device.
     *
     * @param data The data to store.
     * @param measurementIdentifier The id of the [Measurement] to store the data to.
     */
    fun storeData(
        data: CapturedData,
        measurementIdentifier: Long,
        callback: WritingDataCompletedCallback,
    ) {
        if (threadPool!!.isShutdown) {
            return
        }
        if (accelerationsFile == null && data.accelerations.isNotEmpty()) {
            accelerationsFile = Point3DFile(
                persistenceLayer.context!!,
                measurementIdentifier,
                Point3DType.ACCELERATION
            )
        }
        if (rotationsFile == null && data.rotations.isNotEmpty()) {
            rotationsFile = Point3DFile(
                persistenceLayer.context!!,
                measurementIdentifier,
                Point3DType.ROTATION
            )
        }
        if (directionsFile == null && data.directions.isNotEmpty()) {
            directionsFile = Point3DFile(
                persistenceLayer.context!!,
                measurementIdentifier,
                Point3DType.DIRECTION
            )
        }
        val writer = CapturedDataWriter(
            data,
            accelerationsFile,
            rotationsFile,
            directionsFile,
            callback
        )
        threadPool!!.submit(writer)

        // Only store latest pressure point into the database, as the minimum frequency is > 10 HZ
        val pressures = data.pressures
        Log.d(
            Constants.TAG,
            String.format(
                Locale.getDefault(),
                "Captured %d pressure points, storing 1 average",
                pressures.size
            )
        )
        if (pressures.size > 0) {
            // Calculating the average pressure to be less dependent on random outliers
            var sum = 0.0
            for (p in pressures) {
                sum += p.pressure
            }
            val averagePressure = sum / pressures.size
            // Using the timestamp of the latest pressure sample
            val timestamp = pressures[pressures.size - 1].timestamp
            val pressure = Pressure(0, timestamp, averagePressure, measurementIdentifier)
            // runBlocking should be fine here to wait synchronously, as we're not on a UI thread
            runBlocking { persistenceLayer.pressureDao!!.insertAll(pressure) }
        }
    }

    /**
     * Stores the provided geo location under the currently active captured measurement.
     *
     * `runBlocking` should be fine here to wait synchronously, as we're not on a UI thread
     *
     * @param location The geo location to store.
     * @param measurementIdentifier The identifier of the measurement to store the data to.
     */
    fun storeLocation(location: ParcelableGeoLocation, measurementIdentifier: Long) = runBlocking {
        persistenceLayer.locationDao!!.insertAll(GeoLocation(location, measurementIdentifier))
    }

    /**
     * Loads the currently captured measurement and refreshes the [.currentMeasurementIdentifier] reference. This
     * method should only be called if capturing is active. It throws an error otherwise.
     *
     * @throws NoSuchMeasurementException If this method has been called while no measurement was active. To avoid this
     * use [DefaultPersistenceLayer.hasMeasurement] to check whether there is an
     * actual
     * [MeasurementStatus.OPEN] measurement.
     */
    @Throws(NoSuchMeasurementException::class)
    private suspend fun refreshIdentifierOfCurrentlyCapturedMeasurement() {
        val (id) = persistenceLayer.loadCurrentlyCapturedMeasurementFromPersistence()
        currentMeasurementIdentifier = id
        Log.d(
            de.cyface.persistence.Constants.TAG,
            "Refreshed currentMeasurementIdentifier to: $currentMeasurementIdentifier"
        )
    }

    /**
     * Resets the cached identifier of the currently active measurement.
     *
     * This is necessary when the measurement status is changed manually, e.g. when cleaning up crashed measurements.
     */
    fun resetIdentifierOfCurrentlyCapturedMeasurement() {
        currentMeasurementIdentifier = null
    }

    /**
     * Loads the current [Measurement] from the internal cache if possible, or from the persistence layer if an
     * [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] `Measurement` exists.
     *
     * @return The currently captured `Measurement`
     * @throws NoSuchMeasurementException If neither the cache nor the persistence layer have an an
     * [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] `Measurement`
     */
    @Throws(NoSuchMeasurementException::class)
    override suspend fun loadCurrentlyCapturedMeasurement(): Measurement {
        return mutex.withLock {
            if (currentMeasurementIdentifier == null &&
                (persistenceLayer.hasMeasurement(MeasurementStatus.OPEN) ||
                        persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED))
            ) {
                refreshIdentifierOfCurrentlyCapturedMeasurement()
                require(currentMeasurementIdentifier != null)
            }

            val id = currentMeasurementIdentifier
                ?: throw NoSuchMeasurementException(
                    "Trying to load measurement identifier while no measurement was open or paused!"
                )

            persistenceLayer.loadMeasurement(id)!!
        }
    }

    /**
     * Update the [MeasurementStatus] of the currently active [Measurement].
     *
     * @throws NoSuchMeasurementException When there was no currently captured `Measurement`.
     * @throws IllegalArgumentException When the {@param newStatus} was none of the supported:
     * [MeasurementStatus.FINISHED], [MeasurementStatus.PAUSED] or
     * [MeasurementStatus.OPEN].
     */
    @Throws(NoSuchMeasurementException::class)
    suspend fun updateRecentMeasurement(newStatus: MeasurementStatus) {
        require(
            newStatus === MeasurementStatus.FINISHED ||
            newStatus === MeasurementStatus.PAUSED ||
            newStatus === MeasurementStatus.OPEN
        )

        mutex.withLock {
            val currentlyCapturedMeasurementId = loadCurrentlyCapturedMeasurement().id
            val currentStatus = persistenceLayer.loadMeasurementStatus(currentlyCapturedMeasurementId)

            when (newStatus) {
                MeasurementStatus.OPEN -> require(currentStatus == MeasurementStatus.PAUSED)
                MeasurementStatus.PAUSED -> require(currentStatus == MeasurementStatus.OPEN)
                MeasurementStatus.FINISHED -> require(
                    currentStatus == MeasurementStatus.OPEN || currentStatus == MeasurementStatus.PAUSED
                )
                else -> throw IllegalArgumentException("No supported newState: $newStatus")
            }

            Log.d(Constants.TAG, "Updating recent measurement to: $newStatus")

            try {
                persistenceLayer.setStatus(currentlyCapturedMeasurementId, newStatus, false)
            } finally {
                if (newStatus == MeasurementStatus.FINISHED) {
                    resetIdentifierOfCurrentlyCapturedMeasurement()
                }
            }
        }
    }

    /**
     * Updates the [Measurement.distance] entry of the currently captured [Measurement].
     *
     * @param newDistance The new distance value to be stored.
     * @throws NoSuchMeasurementException When there was no currently captured `Measurement`.
     */
    @Throws(NoSuchMeasurementException::class)
    suspend fun updateDistance(newDistance: Double) {
        require(newDistance >= 0.0)
        mutex.withLock {
            val currentlyCapturedMeasurementId = loadCurrentlyCapturedMeasurement().id
            persistenceLayer.setDistance(currentlyCapturedMeasurementId, newDistance)
        }
    }
}
