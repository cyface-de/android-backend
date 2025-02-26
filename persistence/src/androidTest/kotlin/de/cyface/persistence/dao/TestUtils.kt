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
package de.cyface.persistence.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.cyface.persistence.Database
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Attachment
import de.cyface.persistence.model.AttachmentStatus
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.Pressure
import kotlin.io.path.Path

/**
 * Utility methods used in different [de.cyface.persistence.dao] `androidTest`s.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.5.0
 */
object TestUtils {

    /**
     * Creates an in-memory database, i.e. the database is cleared when the process is killed.
     */
    fun createDatabase(): Database {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Database::class.java
        ).allowMainThreadQueries().build()
    }

    fun measurementFixture(status: MeasurementStatus = MeasurementStatus.OPEN): Measurement {
        return Measurement(
            status,
            Modality.BICYCLE,
            DefaultPersistenceLayer.PERSISTENCE_FILE_FORMAT_VERSION,
            0.0,
            1000L,
            0,
        )
    }

    fun locationFixture(
        measurementId: Long = 1L,
        speed: Double = 1.01,
        accuracy: Double = 5.0
    ): GeoLocation {
        return GeoLocation(0L, 1000L, 13.0, 51.0, 400.0, speed, accuracy, 20.0, measurementId)
    }

    fun pressureFixtures(measurementId: Long = 1L): Pressure {
        return Pressure(0L, 1000L, 1013.0, measurementId)
    }

    fun eventFixture(measurementId: Long, type: EventType = EventType.LIFECYCLE_START): Event {
        return Event(1000L, type, null, measurementId)
    }

    fun attachmentFixtures(measurementId: Long = 1L): Attachment {
        return Attachment(
            0L,
            1000L,
            AttachmentStatus.SAVED,
            de.cyface.protos.model.File.FileType.CSV,
            1,
            1234L,
            Path("./some/test/file.ext"),
            null,
            null,
            999L,
            measurementId,
        )
    }
}
