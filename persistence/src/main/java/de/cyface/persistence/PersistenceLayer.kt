/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.persistence

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import de.cyface.persistence.Constants.TAG
import de.cyface.persistence.dao.DefaultFileDao
import de.cyface.persistence.dao.FileDao
import de.cyface.persistence.exception.NoDeviceIdException
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Identifier
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.persistence.model.Pressure
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.IdentifierRepository
import de.cyface.persistence.serialization.NoSuchFileException
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.serializer.model.Point3DType
import de.cyface.utils.CursorIsNullException
import de.cyface.utils.Validate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * This class wraps the Cyface Android persistence API as required by the `DataCapturingListener` and its delegate
 * objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 18.2.0
 * @since 2.0.0
 */
class PersistenceLayer<B : PersistenceBehaviour?> {
    /**
     * The [Context] required to locate the app's internal storage directory.
     */
    val context: Context?

    /**
     * The [PersistenceBehaviour] defines how the `Persistence` layer works. We need this behaviour to
     * differentiate if the [PersistenceLayer] is used for live capturing and or to load existing data.
     *
     * **ATTENTION:** This should not be used by SDK implementing apps.
     */
    var persistenceBehaviour: B? = null
        private set

    /**
     * The [FileDao] used to interact with files.
     *
     * **ATTENTION:** This should not be used by SDK implementing apps.
     */
    val fileDao: FileDao

    /**
     * The database which contains all persisted data which is not written to binary files.
     */
    val database: Database?
    private val identifierRepository: IdentifierRepository?

    // FIXME: use this when one async task failure should not cancel all others
    // FIXME: onDestroy() => job.cancel()
    private val job = SupervisorJob()

    // The scope keeps track of coroutines, can cancel them and is notified about failures
    // No need to cancel this scope as it'll be torn down with the process
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /**
     * **This constructor is only for testing.**
     *
     * It's required by the `DataCapturingLocalTest` to be able to [@Spy] on this object.
     */
    constructor() {
        context = null
        database = null
        identifierRepository = null
        fileDao = DefaultFileDao()
    }

    /**
     * Creates a new completely initialized `PersistenceLayer`.
     *
     * @param context The [Context] required to locate the app's internal storage directory.
     * @param persistenceBehaviour A [PersistenceBehaviour] which tells if this [PersistenceLayer] is used
     * to capture live data.
     */
    constructor(context: Context, persistenceBehaviour: B) {
        this.context = context
        // FIXME: see https://developer.android.com/codelabs/android-room-with-a-view-kotlin#12
        this.database = Database.getDatabase(context.applicationContext)
        this.identifierRepository = IdentifierRepository(database.identifierDao()!!)
        this.persistenceBehaviour = persistenceBehaviour
        fileDao = DefaultFileDao()
        val accelerationsFolder =
            fileDao.getFolderPath(context, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        val rotationsFolder =
            fileDao.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        val directionsFolder =
            fileDao.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        checkOrCreateFolder(accelerationsFolder)
        checkOrCreateFolder(rotationsFolder)
        checkOrCreateFolder(directionsFolder)
        persistenceBehaviour!!.onStart(this)
    }

    /**
     * Ensures that the specified exists.
     *
     * @param folder The [File] pointer to the folder which is created if it does not yet exist
     */
    private fun checkOrCreateFolder(folder: File) {
        if (!folder.exists()) {
            Validate.isTrue(folder.mkdir())
        }
    }

    /**
     * Creates a new, [MeasurementStatus.OPEN] [Measurement] for the provided [Modality].
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param modality The `Modality` to create a new `Measurement` for.
     * @return The newly created `Measurement`.
     */
    fun newMeasurement(modality: Modality): Measurement {
        val timestamp = System.currentTimeMillis()
        val measurement = Measurement(
            MeasurementStatus.OPEN,
            modality,
            PERSISTENCE_FILE_FORMAT_VERSION,
            0.0,
            timestamp
        )
        runBlocking {
            val measurementId = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.insert(measurement)
            }
            measurement.id = measurementId
        }
        // To ensure this works, i.e. blocking code alters the object
        Validate.notNull(measurement.id)
        return measurement
    }

    /**
     * Provides information about whether there is currently a [Measurement] in the specified
     * [MeasurementStatus].
     *
     * @param status The `MeasurementStatus` in question
     * @return `true` if a `Measurement` of the {@param status} exists.
     */
    @Throws(CursorIsNullException::class)
    fun hasMeasurement(status: MeasurementStatus): Boolean {
        Log.v(TAG, "Checking if app has an $status measurement.")
        var measurements: List<Measurement?>?
        runBlocking {
            measurements = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.loadAllByStatus(status)
            }
        }
        val hasMeasurement = measurements!!.isNotEmpty()
        Log.v(
            TAG,
            if (hasMeasurement) "At least one measurement is $status." else "No measurement is $status."
        )
        return hasMeasurement
    }

    /**
     * Returns all [Measurement]s, no matter the current [MeasurementStatus].
     * If you only want measurements of a specific [MeasurementStatus] call
     * [.loadMeasurements] instead.
     *
     * @return A list containing all `Measurement`s currently stored on this device by this application. An empty
     * list if there are no such measurements, but never `null`.
     */
    @Throws(CursorIsNullException::class)  // Used by cyface flavour tests and possibly by implementing apps
    fun loadMeasurements(): List<Measurement?> {
        var measurements: List<Measurement?>
        runBlocking {
            measurements = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.getAll()!!
            }
        }
        return measurements
    }

    /**
     * Provide one specific [Measurement] from the data storage if it exists.
     *
     * Attention: As the loaded `Measurement` object and the persistent version of it in the
     * [PersistenceLayer] are not directly connected the loaded object is not notified when
     * the it's counterpart in the `PersistenceLayer` is changed (e.g. the [MeasurementStatus]).
     *
     * @param measurementIdentifier The device wide unique identifier of the `Measurement` to load.
     * @return The loaded `Measurement` if it exists; `null` otherwise.
     */
    @Throws(CursorIsNullException::class)  // Sdk implementing apps (SR) use this to load single measurements
    fun loadMeasurement(measurementIdentifier: Long): Measurement? {
        var measurement: Measurement?
        runBlocking {
            measurement = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.loadById(measurementIdentifier)
            }
        }
        return measurement
    }

    /**
     * Provide one specific [Event] from the data storage if it exists.
     *
     * Attention: At the loaded `Event` object and the persistent version of it in the
     * [PersistenceLayer] are not directly connected the loaded object is not notified when
     * the it's counterpart in the `PersistenceLayer` is changed (e.g. the [EventType]).
     *
     * @param eventId The device wide unique identifier of the `Event` to load.
     * @return The loaded `Event` if it exists; `null` otherwise.
     */
    @Throws(CursorIsNullException::class)  // Sdk implementing apps (CY)
    fun loadEvent(eventId: Long): Event? {
        var event: Event?
        runBlocking {
            event = withContext(scope.coroutineContext) {
                database!!.eventDao()!!.loadById(eventId)
            }
        }
        return event
    }

    /**
     * Provide the [MeasurementStatus] of one specific [Measurement] from the data storage.
     *
     * **ATTENTION:** Please be aware that the returned status is only valid at the time this
     * method is called. Changes of the `MeasurementStatus` in the persistence layer are not pushed
     * to the `MeasurementStatus` returned by this method.
     *
     * @param measurementIdentifier The device wide unique identifier of the measurement to load.
     * @return The loaded `MeasurementStatus`
     * @throws NoSuchMeasurementException If the [Measurement] does not exist.
     */
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun loadMeasurementStatus(measurementIdentifier: Long): MeasurementStatus {

        // FIXME: for performance reasons, we could add another dao method which only loads the status (we did not do
        // this before)
        return loadMeasurement(measurementIdentifier)!!.status
    }

    /**
     * Loads all [Measurement] which are in a specific [MeasurementStatus] from the data
     * storage.
     *
     * @param status the `MeasurementStatus` for which all `Measurement`s are to be loaded
     * @return All the {code Measurement}s in the specified {@param state}. An empty list if there are no
     * such measurements, but never `null`.
     */
    @Throws(CursorIsNullException::class)  // Implementing apps (SR) use this api to load the finished measurements
    fun loadMeasurements(status: MeasurementStatus): List<Measurement?>? {
        var measurements: List<Measurement?>?
        runBlocking {
            measurements = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.loadAllByStatus(status)
            }
        }
        return measurements
    }

    /**
     * Marks a [MeasurementStatus.FINISHED] [Measurement] as [MeasurementStatus.SYNCED],
     * [MeasurementStatus.SKIPPED] or [MeasurementStatus.DEPRECATED] and deletes the sensor data.
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param measurementId The id of the [Measurement] to remove.
     * @throws NoSuchMeasurementException If the [Measurement] does not exist.
     */
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun markFinishedAs(newStatus: MeasurementStatus, measurementId: Long) {

        // The status in the database could be different from the one in the object so load it again
        Validate.isTrue(loadMeasurementStatus(measurementId) === MeasurementStatus.FINISHED)
        setStatus(measurementId, newStatus, false)

        // TODO [CY-4359]: implement cyface variant where not only sensor data but also GeoLocations are deleted
        try {
            val accelerationFile = Point3DFile.loadFile(
                context!!, fileDao, measurementId, Point3DType.ACCELERATION
            )
                .file
            Validate.isTrue(accelerationFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No acceleration file found to delete, nothing to do")
        }
        try {
            val rotationFile = Point3DFile.loadFile(
                context!!, fileDao, measurementId, Point3DType.ROTATION
            ).file
            Validate.isTrue(rotationFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No rotation file found to delete, nothing to do")
        }
        try {
            val directionFile = Point3DFile.loadFile(
                context!!, fileDao, measurementId, Point3DType.DIRECTION
            )
                .file
            Validate.isTrue(directionFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No direction file found to delete, nothing to do")
        }
    }

    /**
     * This method asynchronously loads or creates a new device identifier, if none exists.
     *
     * The device identifier is persisted into the database where the measurement id is also stored.
     * This way we ensure either both or none of both is reset upon re-installation or app reset.
     *
     * **ATTENTION:** This method should not be called from outside the SDK. Use
     * `DataCapturingService#getDeviceIdentifier()` instead.
     *
     * @return The device identifier
     */
    @Throws(CursorIsNullException::class) // FIXME remove all CursorNotFoundExceptions
    fun restoreOrCreateDeviceId(): String {
        return try {
            loadDeviceId().deviceId
        } catch (e: NoDeviceIdException) {
            createDeviceId()
        }
    }

    /**
     * Loads the device identifier from the persistence layer.
     *
     * @return The device is as string
     * @throws NoDeviceIdException when there are no [Identifier]s found
     */
    @Throws(NoDeviceIdException::class)
    private fun loadDeviceId(): Identifier {
        var identifier: Identifier?
        runBlocking {
            val identifiers: List<Identifier?>? = withContext(scope.coroutineContext) {
                identifierRepository!!.getAll()
            }

            Validate.isTrue(identifiers!!.size <= 1, "More entries than expected")
            if (identifiers.isEmpty()) {
                throw NoDeviceIdException("No entries in IdentifierTable.")
            } else {
                val did = identifiers[0]
                Log.v(TAG, "Providing device identifier $did")
                identifier = did!!
            }
        }
        return identifier!!
    }

    /**
     * Creates a new device identifier and persists it into the persistence layer.
     *
     * @return The created device identifier.
     */
    private fun createDeviceId(): String {
        val deviceId = UUID.randomUUID().toString()
        runBlocking {
            withContext(scope.coroutineContext) {
                identifierRepository!!.insert(Identifier(deviceId))
            }
        }

        // Show info in log so that we see if this happens more than once (or on app data reset)
        Log.i(TAG, "Created new device id: $deviceId")
        return deviceId
    }

    /**
     * Removes one [Measurement] from the local persistent data storage.
     *
     * @param measurementIdentifier The id of the `Measurement` to remove.
     */
    // Sdk implementing apps (SR) use this to delete measurements
    fun delete(measurementIdentifier: Long) {
        deletePoint3DData(measurementIdentifier)

        // Delete {@link GeoLocation}s, {@link Event}s and {@link Measurement} entry from database
        // FIXME: See if this is still necessary as we now added ForeignKey and onDelete = CASCADING
        runBlocking {
            withContext(scope.coroutineContext) {
                database!!.geoLocationDao()!!.deleteItemByMeasurementId(measurementIdentifier)
                database.eventDao()!!.deleteItemByMeasurementId(measurementIdentifier)
                database.pressureDao()!!.deleteItemByMeasurementId(measurementIdentifier)
                database.measurementDao()!!.deleteItemById(measurementIdentifier)
            }
        }
    }

    /**
     * Removes one [Event] from the local persistent data storage.
     *
     * @param eventId The id of the `Event` to remove.
     */
    // Sdk implementing apps (CY) use this
    fun deleteEvent(eventId: Long) {
        runBlocking {
            withContext(scope.coroutineContext) {
                database!!.eventDao()!!.deleteItemById(eventId)
            }
        }
    }

    /**
     * Removes the [de.cyface.persistence.model.ParcelablePoint3D]s for one [Measurement] from the local persistent data storage.
     *
     * @param measurementIdentifier The `Measurement` id of the data to remove.
     */
    private fun deletePoint3DData(measurementIdentifier: Long) {
        val accelerationFolder =
            fileDao.getFolderPath(context!!, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        val rotationFolder =
            fileDao.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        val directionFolder =
            fileDao.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        if (accelerationFolder.exists()) {
            val accelerationFile = fileDao.getFilePath(
                context, measurementIdentifier,
                Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION
            )
            if (accelerationFile.exists()) {
                Validate.isTrue(accelerationFile.delete())
            }
        }
        if (rotationFolder.exists()) {
            val rotationFile = fileDao.getFilePath(
                context, measurementIdentifier,
                Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION
            )
            if (rotationFile.exists()) {
                Validate.isTrue(rotationFile.delete())
            }
        }
        if (directionFolder.exists()) {
            val directionFile = fileDao.getFilePath(
                context, measurementIdentifier,
                Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION
            )
            if (directionFile.exists()) {
                Validate.isTrue(directionFile.delete())
            }
        }
    }

    /**
     * Returns the average speed of the measurement with the provided measurement identifier.
     *
     * Loads the [Track]s from the database to calculate the metric on the fly [STAD-384].
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
     * [de.cyface.persistence.model.ParcelableGeoLocation]s
     * @return The average speed in meters per second.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Suppress("unused") // Part of the API
    @Throws(CursorIsNullException::class)
    fun loadAverageSpeed(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): Double {
        val tracks = loadTracks(measurementIdentifier)
        var speedSum = 0.0
        var speedCounter = 0
        for (track in tracks) {
            var sum = 0.0
            var counter = 0
            for (location in track.geoLocations) {
                if (locationCleaningStrategy.isClean(location)) {
                    sum += location!!.speed
                    counter += 1
                }
            }
            speedSum += sum
            speedCounter += counter
        }
        return if (speedCounter > 0) speedSum / speedCounter.toDouble() else 0.0
    }

    /**
     * Returns the sum of the positive altitude changes of the measurement with the provided measurement identifier.
     *
     * To calculate the ascend, the [ParcelablePressure] values are loaded from the database if such values are
     * available, otherwise the the [Track]s with the [de.cyface.persistence.model.ParcelableGeoLocation] are loaded from the database to
     * calculate the metric on the fly [STAD-384]. In case no altitude information is available, `null` is
     * returned.
     *
     * **Attention:** This method executes blocking code (database access) and cannot be executed on the main thread.
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param forceGnssAscend `true` if the ascend calculated based on GNSS data should be returned regardless if
     * barometer data is available.
     * @return The ascend in meters.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Suppress("unused") // Part of the API
    @JvmOverloads
    @Throws(CursorIsNullException::class)
    fun loadAscend(measurementIdentifier: Long, forceGnssAscend: Boolean = false): Double? {

        // Check if locations with altitude values are available
        val tracks = loadTracks(measurementIdentifier)
        if (tracks.isNotEmpty()) {
            var hasPressures = false
            for (track in tracks) {
                if (track.pressures.isNotEmpty()) {
                    hasPressures = true
                    break
                }
            }
            return if (hasPressures && !forceGnssAscend) {
                ascendFromPressures(tracks, PRESSURE_SLIDING_WINDOW_SIZE)
            } else {
                ascendFromGNSS(tracks)
            }
        }
        return null
    }

    /**
     * Calculate based on atmospheric pressure.
     *
     * @param tracks The track to calculate the ascend for.
     * @param slidingWindowSize The window size to use to average the pressure values.
     * @return The ascend in meters.
     */
    fun ascendFromPressures(tracks: List<Track>, slidingWindowSize: Int): Double? {
        var totalAscend: Double? = null
        for (track in tracks) {

            // Calculate average pressure because some devices measure large pressure differences when
            // the display-fingerprint is used and pressure is applied to the display: Pixel 6 [STAD-400]
            // This filter did not affect ascend calculation of devices without the bug: Pixel 3a
            val pressures: MutableList<Double> = ArrayList()
            for (pressure in track.pressures) {
                pressures.add(pressure!!.pressure)
            }
            val averagePressures = averages(pressures, slidingWindowSize) ?: continue
            val altitudes: MutableList<Double> = ArrayList()
            for (pressure in averagePressures) {
                // As we're only interested in ascend and elevation profile, using a static reference pressure is
                // sufficient [STAD-385] [STAD-391]
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressure.toFloat()
                ).toDouble()
                altitudes.add(altitude)
            }

            // Tracks without much altitude should return 0 not null
            var ascend = if (altitudes.isEmpty()) null else 0.0
            var lastAltitude: Double? = null
            for (altitude in altitudes) {
                if (lastAltitude == null) {
                    lastAltitude = altitude
                    continue
                }
                val newAscend = altitude - lastAltitude
                if (abs(newAscend) < ASCEND_THRESHOLD_METERS) {
                    continue
                }
                if (newAscend > 0) {
                    ascend = ascend!! + newAscend
                }
                lastAltitude = altitude
            }
            if (ascend != null) {
                totalAscend = if (totalAscend != null) totalAscend + ascend else ascend
            }
        }
        return totalAscend
    }

    /**
     * Calculate based on GNSS.altitude.
     *
     * @param tracks The track to calculate the ascend for.
     * @return The ascend in meters.
     */
    fun ascendFromGNSS(tracks: List<Track>): Double? {
        var totalAscend: Double? = null
        for (track in tracks) {
            var ascend: Double? = null
            var lastAltitude: Double? = null
            for (location in track.geoLocations) {
                val altitude = location!!.altitude
                if (ascend == null && altitude != null) {
                    // Tracks without much altitude should return 0 not null
                    ascend = 0.0
                }
                val verticalAccuracy = location.verticalAccuracy
                if (verticalAccuracy == null || verticalAccuracy <= VERTICAL_ACCURACY_THRESHOLD_METERS) {
                    if (altitude == null) {
                        continue
                    }
                    if (lastAltitude == null) {
                        lastAltitude = altitude
                        continue
                    }
                    if (abs(altitude - lastAltitude) < ASCEND_THRESHOLD_METERS) {
                        continue
                    }
                    val newAscend = altitude - lastAltitude
                    if (newAscend > 0) {
                        ascend = ascend!! + newAscend
                    }
                    lastAltitude = altitude
                }
            }
            if (ascend != null) {
                totalAscend = if (totalAscend != null) totalAscend + ascend else ascend
            }
        }
        return totalAscend
    }

    /**
     * Calculates the average value of a sliding window over a list of values.
     *
     * E.g. for window size 3: [3, 0, 0, 6] => [1, 2]
     *
     * @param values The values to calculate the averages for.
     * @param windowSize The size of the window to calculate each average on.
     * @return The calculated averages.
     */
    fun averages(values: List<Double>, windowSize: Int): List<Double>? {
        if (values.size <= windowSize) {
            return null
        }
        val averages: MutableList<Double> = ArrayList()
        for (i in windowSize - 1 until values.size) {
            var sum = 0.0
            val window = values.subList(i - windowSize + 1, i + 1)
            Validate.isTrue(window.size == windowSize)
            for (value in window) {
                sum += value
            }
            averages.add(sum / window.size)
        }
        return averages
    }

    /**
     * Returns the duration of the measurement with the provided measurement identifier without the time between pause
     * and resume. [STAD-367]
     *
     * Loads the [Event]s from the database to remove the time between pause and resume.
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @return The average speed in meters per second.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     * @throws NoSuchMeasurementException If the [Measurement] does not exist.
     */
    @Suppress("unused") // Part of the API
    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    fun loadDuration(measurementIdentifier: Long): Long {

        // Extract lifecycle events only
        val lifecycleEvents: MutableList<Event?> = ArrayList()
        for (event in loadEvents(measurementIdentifier)) {
            val type = event!!.type
            if (type == EventType.LIFECYCLE_START || type == EventType.LIFECYCLE_PAUSE || type == EventType.LIFECYCLE_RESUME || type == EventType.LIFECYCLE_STOP) {
                lifecycleEvents.add(event)
            }
        }

        // Add duration for each lifecycle event pair which fits:
        // - START-STOP, START-PAUSE, RESUME-PAUSE, RESUME-STOP
        // - for ongoing measurements when the last event is START or RESUME (for live duration)
        var duration = 0L
        var previousEvent: Event? = null
        for (i in lifecycleEvents.indices) {
            val event = lifecycleEvents[i]
            val isLast = i == lifecycleEvents.size - 1
            val isOngoing = loadMeasurementStatus(measurementIdentifier) == MeasurementStatus.OPEN
            if (isLast && isOngoing && (event!!.type === EventType.LIFECYCLE_START || event!!.type === EventType.LIFECYCLE_RESUME)) {
                val newDuration = System.currentTimeMillis() - event!!.timestamp
                Validate.isTrue(newDuration >= 0, "Invalid duration: $newDuration")
                duration += newDuration
            } else if (previousEvent != null) {
                val previousType = previousEvent.type
                val type = event!!.type
                val startStop =
                    previousType === EventType.LIFECYCLE_START && type === EventType.LIFECYCLE_STOP
                val startPause =
                    previousType === EventType.LIFECYCLE_START && type === EventType.LIFECYCLE_PAUSE
                val resumePause =
                    previousType === EventType.LIFECYCLE_RESUME && type === EventType.LIFECYCLE_PAUSE
                val resumeStop =
                    previousType === EventType.LIFECYCLE_RESUME && type === EventType.LIFECYCLE_STOP
                if (startStop || startPause || resumePause || resumeStop) {
                    val newDuration = event.timestamp - previousEvent.timestamp
                    Validate.isTrue(newDuration >= 0, "Invalid duration: $newDuration")
                    duration += newDuration
                }
            }
            previousEvent = event
        }
        return duration
    }

    /**
     * Loads the [Track]s for the provided [Measurement].
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @return The [Track]s associated with the `Measurement`. If no [de.cyface.persistence.model.ParcelableGeoLocation]s exists, an
     * empty
     * list is returned.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Throws(CursorIsNullException::class)  // May be used by SDK implementing app
    fun loadTracks(measurementIdentifier: Long): List<Track> {

        var events: List<Event?>?
        var locations: List<GeoLocation?>?
        var pressures: List<Pressure?>?
        runBlocking {
            events = withContext(scope.coroutineContext) {
                database!!.eventDao()!!.loadAllByMeasurementId(measurementIdentifier)
            }

            // Load GeoLocation and Pressure
            // FIXME: Consider using Kotlin Coroutines for async code when upgrading to main branch
            // Or re-implement the UI with LiveData which handles data binding with Room for us.
            locations = withContext(scope.coroutineContext) {
                database!!.geoLocationDao()!!.loadAllByMeasurementId(measurementIdentifier)
            }
            pressures = withContext(scope.coroutineContext) {
                database!!.pressureDao()!!.loadAllByMeasurementId(measurementIdentifier)
            }
        }
        return loadTracks(locations, events, pressures)
    }

    /**
     * Loads the [Track]s from the provided data.
     *
     * @param locations The locations to build the tracks from.
     * @param events The events to build the tracks from.
     * @param pressures The pressures to build the track from.
     * @return The [Track]s build from the provided data. If no [de.cyface.persistence.model.ParcelableGeoLocation]s exists, an
     * empty list is returned.
     */
    private fun loadTracks(
        locations: List<ParcelableGeoLocation?>?, events: List<Event?>?,
        pressures: List<ParcelablePressure?>?
    ): List<Track> {
        if (locations!!.isEmpty()) {
            return emptyList()
        }

        // FIXME: check if this works
        var mutableLocations = locations.toMutableList()
        val mutableEvents = events!!.toMutableList()
        var mutablePressures = pressures!!.toMutableList()
        val tracks = ArrayList<Track>()
        // The geoLocation iterator always needs to point to the first GeoLocation of the next sub track
        val i = 0

        // Slice Tracks before resume events
        var pauseEventTime: Long? = null
        val eventCursor: Iterator<Event?> = mutableEvents.iterator()
        while (eventCursor.hasNext() && i < mutableLocations.size) {
            val event = eventCursor.next()
            val eventType = event!!.type

            // Search for next resume event and capture it's previous pause event
            if (eventType !== EventType.LIFECYCLE_RESUME) {
                if (eventType === EventType.LIFECYCLE_PAUSE) {
                    pauseEventTime = event.timestamp
                }
                continue
            }
            Validate.notNull(pauseEventTime)
            val resumeEventTime = event.timestamp

            // Collect all GeoLocationsV6 and Pressure points until the pause event
            val track = collectNextSubTrack(mutableLocations, mutablePressures, pauseEventTime)

            // Add sub-track to track
            if (track.geoLocations.isNotEmpty()) {
                tracks.add(track)
            }

            // Pause reached: Move geoLocationCursor to the first data point of the next sub-track
            // We do this to ignore data points between pause and resume event (STAD-140)
            mutableLocations =
                mutableLocations.filter { p -> p!!.timestamp >= resumeEventTime } as MutableList<ParcelableGeoLocation?>
            mutablePressures =
                mutablePressures.filter { p -> p!!.timestamp >= resumeEventTime } as MutableList<ParcelablePressure?>
        }

        // Return if there is no tail (sub track ending at LIFECYCLE_STOP instead of LIFECYCLE_PAUSE)
        if (mutableLocations.size == 0) {
            return tracks
        }

        // Collect tail sub track
        // This is ether the track between start[, pause] and stop or resume[, pause] and stop.
        val track = Track(mutableLocations, mutablePressures)
        Validate.isTrue(track.geoLocations.isNotEmpty())
        tracks.add(track)
        return tracks
    }

    /**
     * Loads the "cleaned" [Track]s for the provided [Measurement].
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
     * [de.cyface.persistence.model.ParcelableGeoLocation]s
     * @return The [Track]s associated with the `Measurement`. If no `GeoLocation`s exists, an empty
     * list is returned.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Throws(CursorIsNullException::class)  // Used by SDK implementing apps (SR, CY)
    fun loadTracks(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): List<Track> {

        var locations: List<GeoLocation?>?
        var pressures: List<Pressure?>?
        val events = loadEvents(measurementIdentifier)
        runBlocking {
            locations = withContext(scope.coroutineContext) {
                locationCleaningStrategy.loadCleanedLocations(
                    database!!.geoLocationDao()!!,
                    measurementIdentifier
                )
            }
            pressures = withContext(scope.coroutineContext) {
                database!!.pressureDao()!!.loadAllByMeasurementId(measurementIdentifier)
            }
        }
        return if (locations!!.isEmpty()) emptyList() else loadTracks(locations, events, pressures)
    }

    /**
     * Loads the [Event]s for the provided [Measurement].
     *
     * **Attention: The caller needs to wrap this method call with a try-finally block to ensure the returned
     * `Cursor` is always closed after use.**
     *
     * @param measurementIdentifier The id of the `Measurement` to load the `Event`s for.
     * @return The `Cursor` pointing to the `Event`s of the `Measurement` with the provided
     * {@param measurementId}.
     * @throws CursorIsNullException when accessing the `ContentProvider` failed
     */
    @Throws(CursorIsNullException::class)
    fun loadEvents(measurementIdentifier: Long): List<Event?> {
        var events: List<Event?>
        runBlocking {
            events = withContext(scope.coroutineContext) {
                database!!.eventDao()!!.loadAllByMeasurementId(measurementIdentifier)!!
            }
        }
        return events
    }

    /**
     * Loads all [Event]s of a specific [EventType] for the provided [Measurement] from the data
     * storage.
     *
     * @param measurementId The id of the `Measurement` to load the `Event`s for.
     * @param eventType the `EventType` of which all `Event`s are to be loaded
     * @return All the {code Event}s of the `Measurement` with the provided {@param measurementId} of the
     * specified {@param eventType}. An empty list if there are no such Events, but never `null`.
     */
    @Throws(CursorIsNullException::class)  // Implementing apps (CY) use this
    fun loadEvents(measurementId: Long, eventType: EventType): List<Event?>? {
        var events: List<Event?>?
        runBlocking {
            events = withContext(scope.coroutineContext) {
                database!!.eventDao()!!.loadAllByMeasurementIdAndType(measurementId, eventType)
            }
        }
        return events
    }

    /**
     * Collects a sub [Track] of a `Measurement`.
     *
     * @param locations The ordered list of `PersistedGeoLocation`s which starts at the first
     * [GeoLocation] of the sub track to be collected.
     * @param pressures The ordered list of `PersistedPressure`s which starts at the first
     * [Pressure] of the sub track to be collected.
     * @param pauseEventTime the Unix timestamp of the [EventType.LIFECYCLE_PAUSE] which defines the end of
     * this sub Track.
     * @return The sub `Track`.
     */
    private fun collectNextSubTrack(
        locations: MutableList<ParcelableGeoLocation?>,
        pressures: MutableList<ParcelablePressure?>, pauseEventTime: Long?
    ): Track {
        val track = Track()
        var location = locations[0]
        while (location != null && location.timestamp <= pauseEventTime!!) {
            track.addLocation(location)
            // Load next data point to check it's timestamp in next iteration
            locations.removeAt(0)
            location = locations[0]
        }
        var pressure = pressures[0]
        while (pressure != null && pressure.timestamp <= pauseEventTime!!) {
            track.addPressure(pressure)
            // Load next data point to check it's timestamp in next iteration
            pressures.removeAt(0)
            pressure = pressures[0]
        }
        return track
    }

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     *
     * **ATTENTION:** This method is called automatically and should not be called from outside the SDK.
     */
    fun shutdown() {
        persistenceBehaviour!!.shutdown()
    }

    /**
     * When pausing or stopping a [Measurement] we store the
     * [.PERSISTENCE_FILE_FORMAT_VERSION] in the [Measurement] to make sure we can
     * deserialize the [Point3DFile]s with previous `PERSISTENCE_FILE_FORMAT_VERSION`s.
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param persistenceFileFormatVersion The `MeasurementSerializer#PERSISTENCE_FILE_FORMAT_VERSION` required
     * for deserialization
     * @param measurementId The id of the measurement to update
     */
    fun storePersistenceFileFormatVersion(
        persistenceFileFormatVersion: Short,
        measurementId: Long
    ) {
        Log.d(TAG, "Storing persistenceFileFormatVersion.")
        var updates: Int?
        runBlocking {
            updates = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!
                    .updateFileFormatVersion(measurementId, persistenceFileFormatVersion)
            }
        }
        Validate.isTrue(updates == 1)
    }

    /**
     * Loads the currently captured [Measurement] from the cache, if possible, or from the
     * [PersistenceLayer].
     *
     * @return the currently captured [Measurement]
     * @throws NoSuchMeasurementException If this method has been called while no `Measurement` was active. To
     * avoid this use [PersistenceLayer.hasMeasurement] to check whether there is
     * an actual [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] measurement.
     */
    @Throws(
        NoSuchMeasurementException::class,
        CursorIsNullException::class
    )  // Implementing apps use this to get the ongoing measurement info
    fun loadCurrentlyCapturedMeasurement(): Measurement {
        return persistenceBehaviour!!.loadCurrentlyCapturedMeasurement()
    }

    /**
     * Loads the currently captured [Measurement] explicitly from the [PersistenceLayer].
     *
     * **ATTENTION:** SDK implementing apps should use [.loadCurrentlyCapturedMeasurement] instead.
     *
     * @return the currently captured [Measurement]
     * @throws NoSuchMeasurementException If this method has been called while no `Measurement` was active. To
     * avoid this use [PersistenceLayer.hasMeasurement] to check whether there is
     * an actual [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] measurement.
     */
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun loadCurrentlyCapturedMeasurementFromPersistence(): Measurement? {
        Log.v(TAG, "Trying to load currently captured measurement from PersistenceLayer!")
        val openMeasurements = loadMeasurements(MeasurementStatus.OPEN)
        val pausedMeasurements = loadMeasurements(MeasurementStatus.PAUSED)
        if (openMeasurements!!.isEmpty() && pausedMeasurements!!.isEmpty()) {
            throw NoSuchMeasurementException("No currently captured measurement found!")
        }
        check(openMeasurements.size + pausedMeasurements!!.size <= 1) { "More than one currently captured measurement found!" }
        return (if (openMeasurements.size == 1) openMeasurements else pausedMeasurements)[0]
    }

    /**
     * Updates the [MeasurementStatus] in the data persistence layer.
     *
     * **ATTENTION:** This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the [Measurement] to be updated
     * @param newStatus The new `MeasurementStatus`
     * @param allowCorruptedState `True` if this method is called to clean up corrupted measurements
     * and, thus, it's possible that there are still unfinished measurements
     * after updating one unfinished measurement to finished. Default is `False`.
     * @throws NoSuchMeasurementException if there was no `Measurement` with the id
     * {@param measurementIdentifier}.
     */
    @Throws(NoSuchMeasurementException::class, CursorIsNullException::class)
    fun setStatus(
        measurementIdentifier: Long,
        newStatus: MeasurementStatus,
        allowCorruptedState: Boolean
    ) {
        var updates: Int?
        runBlocking {
            updates = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.update(measurementIdentifier, newStatus)
            }
        }
        Validate.isTrue(updates == 1)
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (newStatus) {
            MeasurementStatus.OPEN -> Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED))
            MeasurementStatus.PAUSED -> Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN))
            MeasurementStatus.FINISHED ->                 // Because of MOV-790 we don't check this when cleaning up corrupted measurement*s*
                if (!allowCorruptedState) {
                    Validate.isTrue(!hasMeasurement(MeasurementStatus.OPEN))
                    Validate.isTrue(!hasMeasurement(MeasurementStatus.PAUSED))
                }
            MeasurementStatus.SYNCED, MeasurementStatus.SKIPPED, MeasurementStatus.DEPRECATED -> {}
            else -> throw IllegalArgumentException(String.format("Unknown status: %s", newStatus))
        }
        Log.d(TAG, "Set measurement $measurementIdentifier to $newStatus")
    }

    /**
     * Updates the [de.cyface.persistence.model.Measurement.distance] entry of the currently captured [Measurement].
     *
     * **ATTENTION:** This should not be used by SDK implementing apps.
     *
     * @param measurementIdentifier The id of the [Measurement] to be updated
     * @param newDistance The new `Measurement#distance` to be stored.
     * @throws NoSuchMeasurementException if there was no `Measurement` with the id
     * {@param measurementIdentifier}.
     */
    @Throws(NoSuchMeasurementException::class)
    fun setDistance(measurementIdentifier: Long, newDistance: Double) {
        var updates: Int?
        runBlocking {
            updates = withContext(scope.coroutineContext) {
                database!!.measurementDao()!!.updateDistance(measurementIdentifier, newDistance)
            }
        }
        Validate.isTrue(updates == 1)
    }

    /**
     * Stores a new [Event] in the [PersistenceLayer] which is linked to a [Measurement].
     *
     * @param eventType The [EventType] to be logged.
     * @param measurement The `Measurement` which is linked to the `Event`.
     * @param timestamp The timestamp in ms at which the event was triggered
     */
    @JvmOverloads
    fun logEvent(
        eventType: EventType, measurement: Measurement,
        timestamp: Long = System.currentTimeMillis()
    ) {
        logEvent(eventType, measurement, timestamp, null)
    }

    /**
     * Stores a new [Event] in the [PersistenceLayer] which is linked to a [Measurement].
     *
     * @param eventType The [EventType] to be logged.
     * @param measurement The `Measurement` which is linked to the `Event`.
     * @param timestamp The timestamp in ms at which the event was triggered
     * @param value The (optional) [Event.value]
     * @return The id of the added `Event`
     */
    fun logEvent(
        eventType: EventType, measurement: Measurement,
        timestamp: Long, value: String?
    ): Long {
        Log.v(
            TAG,
            "Storing Event:" + eventType + (if (value == null) "" else " ($value)") + " for Measurement "
                    + measurement.id + " at " + timestamp
        )

        // value may be null when the type does not require a value, e.g. LIFECYCLE_START
        var id: Long?
        runBlocking {
            id = withContext(scope.coroutineContext) {
                database!!.eventDao()!!
                    .insert(Event(timestamp, eventType, value, measurement.id))
            }
        }
        return id!!
    }

    /**
     * Returns the directory used to store temporary files such as the files prepared for synchronization.
     *
     * @return The directory to be used for temporary files
     */
    val cacheDir: File
        get() = context!!.cacheDir

    companion object {
        /**
         * The current version of the file format used to persist [de.cyface.persistence.model.ParcelablePoint3D] data.
         * It's stored in each [Measurement] database entry and allows to have stored and process
         * measurements and files with different `#PERSISTENCE_FILE_FORMAT_VERSION` at the same time.
         */
        const val PERSISTENCE_FILE_FORMAT_VERSION: Short = 3

        /**
         * The minimum number of meters before the ascend is increased, to filter sensor noise.
         */
        private const val ASCEND_THRESHOLD_METERS = 2.0

        /**
         * The minimum accuracy in meters for GNSS altitudes to be used in ascend calculation.
         */
        private const val VERTICAL_ACCURACY_THRESHOLD_METERS = 12.0

        /**
         * The size of the sliding window to be used to average the pressure data to filter outliers [STAD-400].
         */
        private const val PRESSURE_SLIDING_WINDOW_SIZE = 20
    }
}