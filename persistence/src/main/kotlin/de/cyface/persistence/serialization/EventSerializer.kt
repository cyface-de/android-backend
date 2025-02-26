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
package de.cyface.persistence.serialization

import android.database.Cursor
import androidx.annotation.Nullable
import de.cyface.persistence.content.BaseColumns
import de.cyface.persistence.content.EventTable
import de.cyface.protos.model.Event
import kotlin.math.pow

/**
 * Serializes [Event]s in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION].
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 7.0.0
 */
class EventSerializer {
    /**
     * The serialized events.
     */
    private val events: MutableList<Event> = mutableListOf()

    /**
     * Loads and parses [de.cyface.persistence.model.Event]s from a database `Cursor`.
     *
     * @param cursor the `Cursor` to load the `Event` data from.
     */
    fun readFrom(cursor: Cursor) {
        // The ProtoBuf `events` field is `repeated`, i.e. build one Event per entry.
        while (cursor.moveToNext()) {
            val builder = Event.newBuilder()
            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.TIMESTAMP))
            val typeString = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_TYPE))

            @Nullable // Because not all EventTypes use this field
            val value = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE))
            val type = Event.EventType.valueOf(typeString)
            builder.setTimestamp(timestamp).type = type
            if (value != null) {
                // ProtoBuf `string` must contain UTF-8 encoded text and cannot be longer than 2^32.
                require(value.length <= 2.0.pow(32.0))
                builder.value = value
            }
            events.add(builder.build())
        }
    }

    /**
     * @return the `Event`s in the serialized format.
     */
    fun result(): List<Event> {
        return events
    }
}
