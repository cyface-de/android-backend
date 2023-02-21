/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Objects

/**
 * An `@Entity` which represents a persisted [Measurement] captured by the `DataCapturingService`.
 *
 * An instance of this class represents one row in a database table containing the measurement data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 * @property uid The system wide unique identifier of this measurement. If the entry was not saved
 * yet it may be `null`, since the persistence layers assigns a unique identifier on saving an entry.
 * @property status The capturing status of the measurement, i.e. whether the capturing is still ongoing.
 * @property modality The modality selected when starting this measurement.
 * @property fileFormatVersion The [de.cyface.persistence.PersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION]
 *           used to serialize the data in the file-based persistence layer of this measurement.
 * @property distance The distance this measurement has captured in meters.
 * @property timestamp The Unix timestamp in milliseconds indicating the start time of the measurement.
 */
@Entity//(tableName = "Measurement")
data class Measurement( // FIXME: make all models `data class`
    @field:PrimaryKey(autoGenerate = true) var uid : Long = 0, // FIXME: Move this to an abstract class "DatabaseEntry" like "DataPoint"
    val status: MeasurementStatus,
    val modality: Modality,
    val fileFormatVersion: Short,
    val distance: Double,
    val timestamp: Long
) /*: Measurement*/ {

    /**
     * Creates a new instance of this class which was not yet persisted and has [uid] set to null. // FIXME: can this also be null?
     *
     * @param status The capturing status of the measurement, i.e. whether the capturing is still ongoing.
     * @param modality The modality selected when starting this measurement.
     * @param fileFormatVersion The [de.cyface.persistence.PersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION]
     *           used to serialize the data in the file-based persistence layer of this measurement.
     * @param distance The distance this measurement has captured in meters.
     * @param timestamp The Unix timestamp in milliseconds indicating the start time of the measurement.
     */
    constructor(
        status: MeasurementStatus, modality: Modality, fileFormatVersion: Short, distance: Double, timestamp: Long
    ) : this(0, status, modality, fileFormatVersion, distance, timestamp)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Measurement

        if (uid != other.uid) return false
        if (status != other.status) return false
        if (modality != other.modality) return false
        if (fileFormatVersion != other.fileFormatVersion) return false
        if (distance != other.distance) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(uid)
    }
}