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

import de.cyface.protos.model.Event
import de.cyface.utils.Validate
import kotlin.math.pow


/**
 * Serializes [Event]s in the [MeasurementSerializer.TRANSFER_FILE_FORMAT_VERSION].
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.0.0
 */
class EventSerializer {
    /**
     * The serialized events.
     */
    private val events: MutableList<Event>

    /**
     * Fully initialized constructor of this class.
     *
     *
     * Use [.readFrom] to add `Event` from the database.
     * And [.result] to receive the `Event`s in the serialized format.
     */
    init {
        events = ArrayList()
    }

    /**
     * Reads an [Event] in the database format, to be exported in the binary format in [result].
     *
     * @param event the [Event] to read.
     */
    fun readFrom(event: de.cyface.persistence.model.Event) {
        // The ProtoBuf `events` field is `repeated`, i.e. build one Event per entry.
        val builder = Event.newBuilder()
        builder.timestamp = event.timestamp

        val typeString: String = event.type.databaseIdentifier
        val type = Event.EventType.valueOf(typeString)
        builder.type = type

        if (event.value != null) {
            // ProtoBuf `string` must contain UTF-8 encoded text and cannot be longer than 2^32.
            Validate.isTrue(event.value.length <= 2.0.pow(32.0))
            builder.value = event.value
        }
        events.add(builder.build())
    }

    /**
     * @return the `Event`s in the serialized format.
     */
    fun result(): List<Event> {
        return events
    }
}