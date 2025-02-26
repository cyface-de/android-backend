/*
 * Copyright 2021-2023 Cyface GmbH
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
package de.cyface.persistence.serialization

import android.database.Cursor
import androidx.core.database.getDoubleOrNull
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.LocationTable
import de.cyface.protos.model.LocationRecords
import de.cyface.serializer.Formatter
import de.cyface.serializer.LocationOffsetter

/**
 * Serializes `Location`s in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION].
 *
 * Use [.readFrom] to add `Location` from the database.
 * And [.result] to receive the `Location`s in the serialized format.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 7.0.0
 */
class LocationSerializer {
    /**
     * The builder holding the serialized locations.
     */
    private val builder: LocationRecords.Builder = LocationRecords.newBuilder()

    /**
     * The offsetter to use for this measurement.
     */
    private val offsetter: LocationOffsetter = LocationOffsetter()

    /**
     * Loads and parses `Location`s from a database `Cursor`.
     *
     * @param cursor the `Cursor` to load the `Location` data from.
     */
    fun readFrom(cursor: Cursor) {
        while (cursor.moveToNext()) {
            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.TIMESTAMP))
            val latitude =
                cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_LAT))
            val longitude =
                cursor.getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_LON))
            val speedMeterPerSecond = cursor
                .getDouble(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_SPEED))
            val accuracy =
                cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow(LocationTable.COLUMN_ACCURACY))

            // The proto serializer expects some fields in a different format and in offset-format
            val formatted = Formatter.Location(
                timestamp, latitude, longitude,
                speedMeterPerSecond,
                // TODO: When adding verticalAccuracy to protos, make accuracy nullable [STAD-481]
                accuracy ?: 0.0
            )
            val offsets = offsetter.offset(formatted)
            builder.addTimestamp(offsets.timestamp)
                .addLatitude(offsets.latitude)
                .addLongitude(offsets.longitude)
                .addAccuracy(offsets.accuracy)
                .addSpeed(offsets.speed)
        }
    }

    /**
     * @return the locations in the serialized format.
     */
    fun result(): LocationRecords {
        require(builder.isInitialized)
        return builder.build()
    }
}
