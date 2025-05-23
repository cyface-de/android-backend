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
package de.cyface.persistence

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import de.cyface.persistence.Constants.TAG
import de.cyface.persistence.dao.AttachmentDao
import de.cyface.persistence.io.DefaultFileIOHandler
import de.cyface.persistence.io.FileIOHandler
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.dao.PressureDao
import de.cyface.persistence.exception.NoDeviceIdException
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.AttachmentStatus
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Identifier
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelablePressure
import de.cyface.persistence.model.Pressure
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.EventRepository
import de.cyface.persistence.repository.MeasurementRepository
import de.cyface.persistence.serialization.NoSuchFileException
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.strategy.LocationCleaningStrategy
import de.cyface.serializer.model.Point3DType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * This class wraps the Cyface Android persistence API as required by the `DataCapturingListener` and its delegate
 * objects.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 20.0.0
 * @since 2.0.0
 * @property persistenceBehaviour The [PersistenceBehaviour] defines how the `Persistence` layer works.
 * We need this behaviour to differentiate if the [DefaultPersistenceLayer] is used for live capturing
 * and or to load existing data.
 *
 * **ATTENTION:** This should not be used by SDK implementing apps.
 */
class DefaultPersistenceLayer<B : PersistenceBehaviour?> : PersistenceLayer<B> {

    override val context: Context?

    var persistenceBehaviour: B? = null
        private set

    override val fileIOHandler: FileIOHandler

    override val database: Database?

    override val identifierDao: IdentifierDao?

    override val measurementRepository: MeasurementRepository?

    override val eventRepository: EventRepository?

    override val locationDao: LocationDao?

    override val pressureDao: PressureDao?

    override val attachmentDao: AttachmentDao?

    private val deviceIdLock = Mutex()

    /**
     * **This constructor is only for testing.**
     *
     * It's required by the `DataCapturingLocalTest` to be able to [@Spy] on this object.
     */
    constructor() {
        context = null
        database = null
        identifierDao = null
        measurementRepository = null
        eventRepository = null
        locationDao = null
        pressureDao = null
        attachmentDao = null
        fileIOHandler = DefaultFileIOHandler()
    }

    /**
     * Creates a new completely initialized `PersistenceLayer`.
     *
     * @param context The [Context] required to locate the app's internal storage directory.
     * @param persistenceBehaviour A [PersistenceBehaviour] which tells if this [DefaultPersistenceLayer] is used
     * to capture live data.
     */
    constructor(context: Context, persistenceBehaviour: B) {
        this.context = context
        this.database = Database.build(context.applicationContext)
        this.identifierDao = database.identifierDao()
        this.measurementRepository = MeasurementRepository(database.measurementDao())
        this.eventRepository = EventRepository(database.eventDao())
        this.locationDao = database.locationDao()
        this.pressureDao = database.pressureDao()
        this.attachmentDao = database.attachmentDao()
        this.persistenceBehaviour = persistenceBehaviour
        this.fileIOHandler = persistenceBehaviour!!.fileIoHandler()
        val accelerationsFolder =
            fileIOHandler.getFolderPath(context, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        val rotationsFolder =
            fileIOHandler.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        val directionsFolder =
            fileIOHandler.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        checkOrCreateFolder(accelerationsFolder)
        checkOrCreateFolder(rotationsFolder)
        checkOrCreateFolder(directionsFolder)
        persistenceBehaviour.onStart(this)
    }

    /**
     * Ensures that the specified exists.
     *
     * @param folder The [Attachment] pointer to the folder which is created if it does not yet exist
     */
    private fun checkOrCreateFolder(folder: java.io.File) {
        if (!folder.exists()) {
            require(folder.mkdir())
        }
    }

    override suspend fun newMeasurement(modality: Modality): Measurement {
        val timestamp = System.currentTimeMillis()
        val measurement = Measurement(
            MeasurementStatus.OPEN,
            modality,
            PERSISTENCE_FILE_FORMAT_VERSION,
            0.0,
            timestamp,
            0,
        )
        val measurementId = measurementRepository!!.insert(measurement)
        measurement.id = measurementId
        requireNotNull(measurement.id) // Ensure the blocking code altered the object
        return measurement
    }

    override suspend fun hasMeasurement(status: MeasurementStatus): Boolean {
        Log.v(TAG, "Checking if app has an $status measurement.")
        val measurements = measurementRepository!!.loadAllByStatus(status)
        val hasMeasurement = measurements.isNotEmpty()
        Log.v(
            TAG,
            if (hasMeasurement) "At least one measurement is $status." else "No measurement is $status."
        )
        return hasMeasurement
    }

    override suspend fun loadCompletedMeasurements(): List<Measurement> {
        return measurementRepository!!.loadAllCompleted()
    }

    override suspend fun loadMeasurements(): List<Measurement> {
        return measurementRepository!!.getAll()
    }

    override suspend fun loadMeasurement(measurementIdentifier: Long): Measurement? {
        return measurementRepository!!.loadById(measurementIdentifier)
    }

    /**
     * Provide one specific [Event] from the data storage if it exists.
     *
     * Attention: At the loaded `Event` object and the persistent version of it in the
     * [DefaultPersistenceLayer] are not directly connected the loaded object is not notified when
     * the it's counterpart in the `PersistenceLayer` is changed (e.g. the [EventType]).
     *
     * @param eventId The device wide unique identifier of the `Event` to load.
     * @return The loaded `Event` if it exists; `null` otherwise.
     */
    @Suppress("unused") // Was used by a SDK implementing app (CY)
    suspend fun loadEvent(eventId: Long): Event? {
        return eventRepository!!.loadById(eventId)
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
    @Throws(NoSuchMeasurementException::class)
    suspend fun loadMeasurementStatus(measurementIdentifier: Long): MeasurementStatus {
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
    // Implementing apps (SR) use this api to load the finished measurements
    suspend fun loadMeasurements(status: MeasurementStatus): List<Measurement> {
        return measurementRepository!!.loadAllByStatus(status)
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
    @Throws(NoSuchMeasurementException::class)
    suspend fun markFinishedAs(newStatus: MeasurementStatus, measurementId: Long) {

        // The status in the database could be different from the one in the object so load it again
        require(loadMeasurementStatus(measurementId) === MeasurementStatus.FINISHED)
        setStatus(measurementId, newStatus, false)

        // TODO [CY-4359]: implement cyface variant where not only sensor data but also GeoLocations are deleted
        try {
            val accelerationFile = Point3DFile.loadFile(
                context!!, fileIOHandler, measurementId, Point3DType.ACCELERATION
            )
                .file
            require(accelerationFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No acceleration file found to delete, nothing to do", e)
        }
        try {
            val rotationFile = Point3DFile.loadFile(
                context!!, fileIOHandler, measurementId, Point3DType.ROTATION
            ).file
            require(rotationFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No rotation file found to delete, nothing to do", e)
        }
        try {
            val directionFile = Point3DFile.loadFile(
                context!!, fileIOHandler, measurementId, Point3DType.DIRECTION
            )
                .file
            require(directionFile.delete())
        } catch (e: NoSuchFileException) {
            Log.v(TAG, "markAsSynchronized: No direction file found to delete, nothing to do", e)
        }

        // Also delete syncable attachments binaries when the measurement is skipped or deprecated
        if (newStatus == MeasurementStatus.SKIPPED || newStatus == MeasurementStatus.DEPRECATED) {
            val attachments = attachmentDao!!.loadAllByMeasurementIdAndStatus(measurementId, AttachmentStatus.SAVED)
            attachments.forEach {
                // When re-writing this in a non-blocking way, ensure sync can handle this
                val newAttachmentStatus = when (newStatus) {
                    MeasurementStatus.SKIPPED -> AttachmentStatus.SKIPPED
                    MeasurementStatus.DEPRECATED -> AttachmentStatus.DEPRECATED
                    else -> throw IllegalArgumentException("Unexpected status: $newStatus")
                }
                markSavedAs(newAttachmentStatus, it)
                Log.d(TAG, "Cleaned up attachment (id ${it.id}): $newAttachmentStatus")
            }
            cleanupEmptyFolder(measurementId)
        }
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
    @Throws(NoSuchMeasurementException::class)
    suspend fun markSyncableAttachmentsAs(newStatus: MeasurementStatus, measurementId: Long) {

        // The status in the database could be different from the one in the object so load it again
        require(loadMeasurementStatus(measurementId) === MeasurementStatus.SYNCABLE_ATTACHMENTS)
        setStatus(measurementId, newStatus, false)

        cleanupEmptyFolder(measurementId)
    }

    private suspend fun cleanupEmptyFolder(measurementId: Long) {
        val attachment = attachmentDao!!.loadOneByMeasurementId(measurementId)
        attachment?.let {
            val parentDir = it.path.toFile().parentFile
            if (parentDir!!.isDirectory && parentDir.list()?.isEmpty() == true) {
                if (!parentDir.delete()) {
                    Log.w(TAG, "Failed to delete empty directory: ${parentDir.absolutePath}")
                } else {
                    Log.d(TAG, "Deleted empty directory: ${parentDir.absolutePath}")
                }
            } else {
                Log.w(TAG, "Skipped deleting directory, not empty: ${parentDir.absolutePath}")
            }
        }
    }

    /**
     * Marks a [AttachmentStatus.SAVED] [de.cyface.persistence.model.Attachment] as [AttachmentStatus.SYNCED],
     * [AttachmentStatus.SKIPPED] or [AttachmentStatus.DEPRECATED] and deletes the binary attachment data.
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param attachmentId The id of the [Attachment] to remove.
     */
    suspend fun markSavedAs(newStatus: AttachmentStatus, attachmentId: Long) {
        // The status in the database could be different from the one in the object so load it again
        val attachment = attachmentDao!!.loadById(attachmentId)!!
        markSavedAs(newStatus, attachment)
    }

    /**
     * Marks a [AttachmentStatus.SAVED] [de.cyface.persistence.model.Attachment] as [AttachmentStatus.SYNCED],
     * [AttachmentStatus.SKIPPED] or [AttachmentStatus.DEPRECATED] and deletes the binary attachment data.
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param attachment The [Attachment] to remove.
     */
    private suspend fun markSavedAs(newStatus: AttachmentStatus, attachment: Attachment) {

        // The status in the database could be different from the one in the object so load it again
        require(attachment.status === AttachmentStatus.SAVED) {
            "Unexpected status: ${attachment.status}"
        }
        require(
            newStatus == AttachmentStatus.SYNCED || newStatus == AttachmentStatus.SKIPPED ||
                    newStatus == AttachmentStatus.DEPRECATED
        ) { "Unexpected status change from ${attachment.status} to $newStatus" }
        attachmentDao!!.updateStatus(attachment.id, newStatus)

        // Deleting first as a second upload approach would be handled by the API
        val file = attachment.path.toFile()
        require(file.delete())
    }

    override suspend fun restoreOrCreateDeviceId(): String {
        // Lock required or else the digural MainActivity.onCreate and DCS.initialize create two ids
        return deviceIdLock.withLock {
            try {
                loadDeviceId().deviceId
            } catch (e: NoDeviceIdException) {
                createDeviceId()
            }
        }
    }

    /**
     * Loads the device identifier from the persistence layer.
     *
     * @return The device is as string
     * @throws NoDeviceIdException when there are no [Identifier]s found
     */
    @Throws(NoDeviceIdException::class)
    private suspend fun loadDeviceId(): Identifier {
        val identifiers = identifierDao!!.getAll()
        require(identifiers.size <= 1) { "More entries than expected" }
        if (identifiers.isEmpty()) {
            throw NoDeviceIdException("No entries in IdentifierTable.")
        } else {
            val did = identifiers[0]
            return did
        }
    }

    /**
     * Creates a new device identifier and persists it into the persistence layer.
     *
     * @return The created device identifier.
     */
    private suspend fun createDeviceId(): String {
        val deviceId = UUID.randomUUID().toString()
        identifierDao!!.insert(Identifier(deviceId))

        // Show info in log so that we see if this happens more than once (or on app data reset)
        Log.i(TAG, "Created new device id: $deviceId")
        return deviceId
    }

    override suspend fun delete(measurementIdentifier: Long) {
        deletePoint3DData(measurementIdentifier)
        // measurement data like locations are deleted automatically because of `ForeignKey`
        measurementRepository!!.deleteItemById(measurementIdentifier)
    }

    /**
     * Removes one [Event] from the local persistent data storage.
     *
     * @param eventId The id of the `Event` to remove.
     */
    @Suppress("unused") // Was called by a SDK implementing app (CY)
    suspend fun deleteEvent(eventId: Long) {
        eventRepository!!.deleteItemById(eventId)
    }

    /**
     * Removes the [de.cyface.persistence.model.ParcelablePoint3D]s for one [Measurement] from the
     * local persistent data storage.
     *
     * @param measurementIdentifier The `Measurement` id of the data to remove.
     */
    private fun deletePoint3DData(measurementIdentifier: Long) {
        val accelerationFolder =
            fileIOHandler.getFolderPath(context!!, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        val rotationFolder =
            fileIOHandler.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        val directionFolder =
            fileIOHandler.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        if (accelerationFolder.exists()) {
            val accelerationFile = fileIOHandler.getFilePath(
                context, measurementIdentifier,
                Point3DFile.ACCELERATIONS_FOLDER_NAME, Point3DFile.ACCELERATIONS_FILE_EXTENSION
            )
            if (accelerationFile.exists()) {
                require(accelerationFile.delete())
            }
        }
        if (rotationFolder.exists()) {
            val rotationFile = fileIOHandler.getFilePath(
                context, measurementIdentifier,
                Point3DFile.ROTATIONS_FOLDER_NAME, Point3DFile.ROTATION_FILE_EXTENSION
            )
            if (rotationFile.exists()) {
                require(rotationFile.delete())
            }
        }
        if (directionFolder.exists()) {
            val directionFile = fileIOHandler.getFilePath(
                context, measurementIdentifier,
                Point3DFile.DIRECTIONS_FOLDER_NAME, Point3DFile.DIRECTION_FILE_EXTENSION
            )
            if (directionFile.exists()) {
                require(directionFile.delete())
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
     */
    @Suppress("unused") // Part of the API
    suspend fun loadAverageSpeed(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): Double {
        var speedSum = 0.0
        var speedCounter = 0
        val tracks = loadTracks(measurementIdentifier)
        for (track in tracks) {
            var sum = 0.0
            var counter = 0
            for (location in track.geoLocations) {
                if (locationCleaningStrategy.isClean(location)) {
                    sum += location.speed
                    counter += 1
                }
            }
            speedSum += sum
            speedCounter += counter
        }
        return if (speedCounter > 0) speedSum / speedCounter.toDouble() else 0.0
    }

    /**
     * Returns the maximum speed of the measurement with the provided measurement identifier.
     *
     * Loads the [Track]s from the database to calculate the metric on the fly [STAD-384].
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
     * [de.cyface.persistence.model.ParcelableGeoLocation]s
     * @return The maximum speed in meters per second.
     */
    @Suppress("unused") // Part of the API
    suspend fun loadMaxSpeed(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): Double {
        var maxSpeed = 0.0
        val tracks = loadTracks(measurementIdentifier)
        for (track in tracks) {
            for (location in track.geoLocations) {
                if (locationCleaningStrategy.isClean(location)) {
                    maxSpeed = max(location.speed, maxSpeed)
                }
            }
        }
        return maxSpeed
    }

    /**
     * Returns the sum of the positive altitude changes of the measurement with the provided
     * measurement identifier.
     *
     * To calculate the ascend, the [ParcelablePressure] values are loaded from the database if
     * such values are available, otherwise the the [Track]s with the
     * [de.cyface.persistence.model.ParcelableGeoLocation] are loaded from the database to
     * calculate the metric on the fly [STAD-384]. In case no altitude information is available,
     * `null` is returned.
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param forceGnssAscend `true` if the ascend calculated based on GNSS data should be returned regardless if
     * barometer data is available.
     * @return The ascend in meters.
     */
    @Suppress("unused") // Part of the API
    @JvmOverloads
    suspend fun loadAscend(measurementIdentifier: Long, forceGnssAscend: Boolean = false): Double? {

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
                val altitudes = altitudesFromPressures(tracks, PRESSURE_SLIDING_WINDOW_SIZE)
                totalAscend(altitudes)
            } else {
                val altitudes = altitudesFromGNSS(tracks)
                totalAscend(altitudes)
            }
        }
        return null
    }

    /**
     * Returns the altitudes for each sub-track of a specified measurement.
     *
     * To calculate the altitudes, the [ParcelablePressure] values are loaded from the database if
     * such values are available, otherwise the the [Track]s with the
     * [de.cyface.persistence.model.ParcelableGeoLocation] are loaded from the database to
     * calculate the metric on the fly [STAD-384]. In case no altitude information is available,
     * `null` is returned.
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param forceGnssAltitudes `true` if the altitudes calculated based on GNSS data should be returned regardless if
     * barometer data is available.
     * @return A list of lists, each representing the altitudes of a sub-track in meters.
     */
    @Suppress("unused") // Part of the API
    @JvmOverloads
    suspend fun loadAltitudes(measurementIdentifier: Long, forceGnssAltitudes: Boolean = false): List<List<Double>>? {

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
            return if (hasPressures && !forceGnssAltitudes) {
                altitudesFromPressures(tracks, PRESSURE_SLIDING_WINDOW_SIZE)
            } else {
                altitudesFromGNSS(tracks)
            }
        }
        return null
    }

    /**
     * Calculate the altitudes based on atmospheric pressure.
     *
     * @param tracks The tracks to calculate the altitudes for.
     * @param slidingWindowSize The window size to use to average the pressure values.
     * @return The altitudes in meters as list of lists, each representing a sub-track.
     */
    fun altitudesFromPressures(tracks: List<Track>, slidingWindowSize: Int): List<List<Double>> {
        val allAltitudes = mutableListOf<List<Double>>()
        for (track in tracks) {

            // Calculate average pressure because some devices measure large pressure differences when
            // the display-fingerprint is used and pressure is applied to the display: Pixel 6 [STAD-400]
            // This filter did not affect ascend calculation of devices without the bug: Pixel 3a
            val pressures: MutableList<Double> = mutableListOf()
            for (pressure in track.pressures) {
                pressures.add(pressure.pressure)
            }
            val averagePressures = averages(pressures, slidingWindowSize) ?: continue
            val altitudes = mutableListOf<Double>()
            for (pressure in averagePressures) {
                // As we're only interested in ascend and elevation profile, using a static
                // reference pressure is sufficient [STAD-385] [STAD-391]
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressure.toFloat()
                ).toDouble()
                altitudes.add(altitude)
            }
            if (altitudes.isNotEmpty()) {
                allAltitudes.add(altitudes)
            }
        }
        return allAltitudes
    }

    /**
     * Calculate the altitudes based on GNSS.altitude.
     *
     * @param tracks The tracks to calculate the altitudes for.
     * @return The altitudes in meters as list of lists, each representing a sub-track.
     */
    fun altitudesFromGNSS(tracks: List<Track>): List<List<Double>> {
        val allAltitudes = mutableListOf<List<Double>>()
        for (track in tracks) {
            val altitudes = mutableListOf<Double>()
            for (location in track.geoLocations) {
                val altitude = location.altitude
                val verticalAccuracy = location.verticalAccuracy
                if (verticalAccuracy == null || verticalAccuracy <= VERTICAL_ACCURACY_THRESHOLD_METERS) {
                    if (altitude != null) {
                        altitudes.add(altitude)
                    }
                }
            }
            if (altitudes.isNotEmpty()) {
                allAltitudes.add(altitudes)
            }
        }
        return allAltitudes
    }

    /**
     * Calculate total ascend for a list of sub-tracks.
     *
     * @param altitudes The list of altitudes to calculate the ascend for.
     * @return The ascend in meters.
     */
    fun totalAscend(altitudes: List<List<Double>>): Double? {
        var totalAscend: Double? = null
        for (trackAltitudes in altitudes) {
            // Tracks without much altitude should return 0 not null
            val ascend = if (trackAltitudes.isEmpty()) null else ascend(trackAltitudes)
            if (ascend != null) {
                totalAscend = if (totalAscend != null) totalAscend + ascend else ascend
            }
        }
        return totalAscend
    }

    /**
     * Calculate ascend from an ordered list of valid altitudes, from one sub-track.
     *
     * @param altitudes The altitudes to calculate the ascend for.
     * @return The ascend in meters.
     */
    private fun ascend(altitudes: List<Double>): Double {
        var ascend = 0.0
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
                ascend += newAscend
            }
            lastAltitude = altitude
        }
        return ascend
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
        val averages: MutableList<Double> = mutableListOf()
        for (i in windowSize - 1 until values.size) {
            var sum = 0.0
            val window = values.subList(i - windowSize + 1, i + 1)
            require(window.size == windowSize)
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
     * @throws NoSuchMeasurementException If the [Measurement] does not exist.
     */
    @Suppress("unused") // Part of the API
    @Throws(NoSuchMeasurementException::class)
    suspend fun loadDuration(measurementIdentifier: Long): Long {

        // Extract lifecycle events only
        val lifecycleEvents: MutableList<Event?> = mutableListOf()
        for (event in loadEvents(measurementIdentifier)) {
            val type = event.type
            if (type == EventType.LIFECYCLE_START || type == EventType.LIFECYCLE_PAUSE ||
                type == EventType.LIFECYCLE_RESUME || type == EventType.LIFECYCLE_STOP) {
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
            if (isLast && isOngoing && (event!!.type === EventType.LIFECYCLE_START ||
                        event!!.type === EventType.LIFECYCLE_RESUME)) {
                val newDuration = System.currentTimeMillis() - event!!.timestamp
                require(newDuration >= 0) { "Invalid duration: $newDuration" }
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
                    require(newDuration >= 0) { "Invalid duration: $newDuration" }
                    duration += newDuration
                }
            }
            previousEvent = event
        }
        return duration
    }

    override suspend fun loadTracks(measurementIdentifier: Long): List<Track> {
        val events = eventRepository!!.loadAllByMeasurementId(measurementIdentifier)!!
        val locations = locationDao!!.loadAllByMeasurementId(measurementIdentifier)
        val pressures = pressureDao!!.loadAllByMeasurementId(measurementIdentifier)
        return loadTracks(locations, events, pressures)
    }

    /**
     * Loads the [Track]s from the provided data.
     *
     * @param locations The locations to build the tracks from.
     * @param events The events to build the tracks from.
     * @param pressures The pressures to build the track from.
     * @return The [Track]s built or an empty `List` if no [GeoLocation]s exist.
     */
    private fun loadTracks(
        locations: List<GeoLocation>, events: List<Event>,
        pressures: List<Pressure>
    ): List<Track> {
        if (locations.isEmpty()) {
            return emptyList()
        }

        var mutableLocations = locations.toMutableList()
        val mutableEvents = events.toMutableList()
        var mutablePressures = pressures.toMutableList()
        val tracks = mutableListOf<Track>()
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
            requireNotNull(pauseEventTime)
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
                mutableLocations.filter { p -> p.timestamp >= resumeEventTime } as MutableList<GeoLocation>
            mutablePressures =
                mutablePressures.filter { p -> p.timestamp >= resumeEventTime } as MutableList<Pressure>
        }

        // Return if there is no tail (sub track ending at LIFECYCLE_STOP instead of LIFECYCLE_PAUSE)
        if (mutableLocations.size == 0) {
            return tracks
        }

        // Collect tail sub track
        // This is either the track between start[, pause] and stop or resume[, pause] and stop.
        val track = Track(mutableLocations, mutablePressures)
        require(track.geoLocations.isNotEmpty())
        tracks.add(track)
        return tracks
    }

    override suspend fun loadTracks(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): List<Track> {
        val events = loadEvents(measurementIdentifier)
        val locations = locationCleaningStrategy.loadCleanedLocations(locationDao!!, measurementIdentifier)
        val pressures = pressureDao!!.loadAllByMeasurementId(measurementIdentifier)
        return if (locations.isEmpty()) emptyList() else loadTracks(locations, events, pressures)
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
     */
    suspend fun loadEvents(measurementIdentifier: Long): List<Event> {
        return eventRepository!!.loadAllByMeasurementId(measurementIdentifier)!!
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
    // Implementing apps (CY) use this
    suspend fun loadEvents(measurementId: Long, eventType: EventType): List<Event>? {
        return eventRepository!!.loadAllByMeasurementIdAndType(measurementId, eventType)
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
    fun collectNextSubTrack(
        locations: MutableList<GeoLocation>,
        pressures: MutableList<Pressure>, pauseEventTime: Long?
    ): Track {
        val track = Track()
        var location = if (locations.isNotEmpty()) locations[0] else null
        while (location != null && location.timestamp <= pauseEventTime!!) {
            track.addLocation(location)
            // Load next data point to check it's timestamp in next iteration
            locations.removeAt(0)
            location = if (locations.isNotEmpty()) locations[0] else null
        }
        var pressure = if (pressures.isNotEmpty()) pressures[0] else null
        while (pressure != null && pressure.timestamp <= pauseEventTime!!) {
            track.addPressure(pressure)
            // Load next data point to check it's timestamp in next iteration
            pressures.removeAt(0)
            pressure = if (pressures.isNotEmpty()) pressures[0] else null
        }
        return track
    }

    override fun shutdown() {
        persistenceBehaviour!!.shutdown()
    }

    override suspend fun storePersistenceFileFormatVersion(
        persistenceFileFormatVersion: Short,
        measurementId: Long
    ) {
        Log.d(TAG, "Storing persistenceFileFormatVersion.")
        val updates = measurementRepository!!.updateFileFormatVersion(
            measurementId,
            persistenceFileFormatVersion
        )
        require(updates == 1)
    }

    /**
     * Loads the currently captured [Measurement] from the cache, if possible, or from the
     * [DefaultPersistenceLayer].
     *
     * @return the currently captured [Measurement]
     * @throws NoSuchMeasurementException If this method has been called while no `Measurement` was active. To
     * avoid this use [DefaultPersistenceLayer.hasMeasurement] to check whether there is
     * an actual [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] measurement.
     */
    @Throws(NoSuchMeasurementException::class)
    // Implementing apps use this to get the ongoing measurement info
    suspend fun loadCurrentlyCapturedMeasurement(): Measurement {
        return persistenceBehaviour!!.loadCurrentlyCapturedMeasurement()
    }

    /**
     * Loads the currently captured [Measurement] explicitly from the [DefaultPersistenceLayer].
     *
     * **ATTENTION:** SDK implementing apps should use [.loadCurrentlyCapturedMeasurement] instead.
     *
     * @return the currently captured [Measurement]
     * @throws NoSuchMeasurementException If this method has been called while no `Measurement` was active. To
     * avoid this use [DefaultPersistenceLayer.hasMeasurement] to check whether there is
     * an actual [MeasurementStatus.OPEN] or [MeasurementStatus.PAUSED] measurement.
     */
    @Throws(NoSuchMeasurementException::class)
    suspend fun loadCurrentlyCapturedMeasurementFromPersistence(): Measurement {
        Log.v(TAG, "Trying to load currently captured measurement from PersistenceLayer!")
        val openMeasurements = loadMeasurements(MeasurementStatus.OPEN)
        val pausedMeasurements = loadMeasurements(MeasurementStatus.PAUSED)
        if (openMeasurements.isEmpty() && pausedMeasurements.isEmpty()) {
            throw NoSuchMeasurementException("No currently captured measurement found!")
        }
        check(openMeasurements.size + pausedMeasurements.size <= 1) {
            "More than one currently captured measurement found!"
        }
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
    @Throws(NoSuchMeasurementException::class)
    suspend fun setStatus(
        measurementIdentifier: Long,
        newStatus: MeasurementStatus,
        allowCorruptedState: Boolean
    ) {
        val updates = measurementRepository!!.update(measurementIdentifier, newStatus)
        require(updates == 1)
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (newStatus) {
            MeasurementStatus.OPEN -> require(!hasMeasurement(MeasurementStatus.PAUSED))
            MeasurementStatus.PAUSED -> require(!hasMeasurement(MeasurementStatus.OPEN))
            // Because of MOV-790 we don't check this when cleaning up corrupted measurements
            MeasurementStatus.FINISHED ->
                if (!allowCorruptedState) {
                    require(!hasMeasurement(MeasurementStatus.OPEN))
                    require(!hasMeasurement(MeasurementStatus.PAUSED))
                }
            MeasurementStatus.SYNCABLE_ATTACHMENTS,
            MeasurementStatus.SYNCED, MeasurementStatus.SKIPPED, MeasurementStatus.DEPRECATED -> {}
            else -> throw IllegalArgumentException(
                String.format(Locale.getDefault(), "Unknown status: %s", newStatus)
            )
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
    suspend fun setDistance(measurementIdentifier: Long, newDistance: Double) {
        val updates = measurementRepository!!.updateDistance(measurementIdentifier, newDistance)
        require(updates == 1)
    }

    override suspend fun logEvent(eventType: EventType, measurement: Measurement, timestamp: Long) {
        logEvent(eventType, measurement, timestamp, null)
    }

    /**
     * Stores a new [Event] in the [DefaultPersistenceLayer] which is linked to a [Measurement].
     *
     * @param eventType The [EventType] to be logged.
     * @param measurement The `Measurement` which is linked to the `Event`.
     * @param timestamp The timestamp in ms at which the event was triggered
     * @param value The (optional) [Event.value]
     * @return The id of the added `Event`
     */
    suspend fun logEvent(
        eventType: EventType, measurement: Measurement,
        timestamp: Long, value: String?
    ): Long {
        Log.v(
            TAG,
            "Storing Event:" + eventType + (if (value == null) "" else " ($value)") + " for Measurement "
                    + measurement.id + " at " + timestamp
        )

        // value may be null when the type does not require a value, e.g. LIFECYCLE_START
        return eventRepository!!.insert(Event(timestamp, eventType, value, measurement.id))
    }

    override val cacheDir: java.io.File
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
