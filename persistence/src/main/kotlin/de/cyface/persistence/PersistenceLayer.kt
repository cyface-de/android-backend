/*
 * Copyright 2023-2025 Cyface GmbH
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
import de.cyface.persistence.dao.AttachmentDao
import de.cyface.persistence.io.FileIOHandler
import de.cyface.persistence.dao.IdentifierDao
import de.cyface.persistence.dao.LocationDao
import de.cyface.persistence.dao.PressureDao
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Identifier
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.Pressure
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.EventRepository
import de.cyface.persistence.repository.MeasurementRepository
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.persistence.strategy.LocationCleaningStrategy
import java.io.File

/**
 * Interface for [DefaultPersistenceLayer] created to be able to mock [DefaultPersistenceLayer] in
 * `DataCapturingLocalTest`.
 *
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 7.5.0
 * @property context The [Context] required to locate the app's internal storage directory.
 * @property fileIOHandler The [FileIOHandler] used to interact with files.
 * **ATTENTION:** This should not be used by SDK implementing apps.
 * @property database The database to execute raw queries on, see `Cursor` usages in the code.
 * @property identifierDao The repository to load the [Identifier] data from.
 * @property measurementRepository The source to load the [Measurement] data from.
 * @property eventRepository The source to load the [Event] data from.
 * @property locationDao The source to load the [GeoLocation] data from.
 * @property pressureDao The source to load the [Pressure] data from.
 * @property attachmentDao The source to load the [de.cyface.persistence.model.Attachment] data from.
 */
interface PersistenceLayer<B : PersistenceBehaviour?> {
    val context: Context?
    val fileIOHandler: FileIOHandler
    val database: Database?
    val identifierDao: IdentifierDao?
    val measurementRepository: MeasurementRepository?
    val eventRepository: EventRepository?
    val locationDao: LocationDao?
    val pressureDao: PressureDao?
    val attachmentDao: AttachmentDao?

    /**
     * Creates a new, [MeasurementStatus.OPEN] [Measurement] for the provided [Modality].
     *
     * **ATTENTION:** This method should not be called from outside the SDK.
     *
     * @param modality The `Modality` to create a new `Measurement` for.
     * @return The newly created `Measurement`.
     */
    fun newMeasurement(modality: Modality): Measurement

    /**
     * Provides information about whether there is currently a [Measurement] in the specified
     * [MeasurementStatus].
     *
     * @param status The `MeasurementStatus` in question
     * @return `true` if a `Measurement` of the {@param status} exists.
     */
    fun hasMeasurement(status: MeasurementStatus): Boolean

    /**
     * Returns all [Measurement]s, no matter the current [MeasurementStatus].
     * If you only want measurements of a specific [MeasurementStatus] call
     * [.loadMeasurements] instead.
     *
     * @return A list containing all `Measurement`s currently stored on this device by this application. An empty
     * list if there are no such measurements, but never `null`.
     */
    // Used by cyface flavour tests and possibly by implementing apps
    fun loadMeasurements(): List<Measurement>

    /**
     * Provide one specific [Measurement] from the data storage if it exists.
     *
     * Attention: As the loaded `Measurement` object and the persistent version of it in the
     * [DefaultPersistenceLayer] are not directly connected the loaded object is not notified when
     * the it's counterpart in the `PersistenceLayer` is changed (e.g. the [MeasurementStatus]).
     *
     * @param measurementIdentifier The device wide unique identifier of the `Measurement` to load.
     * @return The loaded `Measurement` if it exists; `null` otherwise.
     */
    // Sdk implementing apps (SR) use this to load single measurements
    fun loadMeasurement(measurementIdentifier: Long): Measurement?

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
    fun restoreOrCreateDeviceId(): String

    /**
     * Removes one [Measurement] from the local persistent data storage.
     *
     * @param measurementIdentifier The id of the `Measurement` to remove.
     */
    fun delete(measurementIdentifier: Long)

    /**
     * Loads the [Track]s for the provided [Measurement].
     *
     * @param measurementIdentifier The id of the [Measurement] to load the track for.
     * @return The [Track]s associated with the [Measurement]. If no
     * [de.cyface.persistence.model.ParcelableGeoLocation]s exists, an empty list is returned.
     */
    // May be used by SDK implementing app
    fun loadTracks(measurementIdentifier: Long): List<Track>

    /**
     * Loads the "cleaned" [Track]s for the provided [Measurement].
     *
     * @param measurementIdentifier The id of the `Measurement` to load the track for.
     * @param locationCleaningStrategy The [LocationCleaningStrategy] used to filter the
     * [de.cyface.persistence.model.ParcelableGeoLocation]s
     * @return The [Track]s associated with the `Measurement`. If no `GeoLocation`s exists, an empty
     * list is returned.
     */
    // Used by SDK implementing apps (SR, CY)
    fun loadTracks(
        measurementIdentifier: Long,
        locationCleaningStrategy: LocationCleaningStrategy
    ): List<Track>

    /**
     * This method cleans up when the persistence layer is no longer needed by the caller.
     *
     * **ATTENTION:** This method is called automatically and should not be called from outside the SDK.
     */
    fun shutdown()

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
    )

    /**
     * Stores a new [Event] in the [DefaultPersistenceLayer] which is linked to a [Measurement].
     *
     * @param eventType The [EventType] to be logged.
     * @param measurement The `Measurement` which is linked to the `Event`.
     * @param timestamp The timestamp in ms at which the event was triggered
     */
    fun logEvent(
        eventType: EventType, measurement: Measurement,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Returns the directory used to store temporary files such as the files prepared for synchronization.
     *
     * @return The directory to be used for temporary files
     */
    val cacheDir: File

    /**
     * Loads all measurements which are not in the [MeasurementStatus.OPEN] or
     * [MeasurementStatus.PAUSED] state starting with the newest measurement.
     */
    fun loadCompletedMeasurements(): List<Measurement>
}
