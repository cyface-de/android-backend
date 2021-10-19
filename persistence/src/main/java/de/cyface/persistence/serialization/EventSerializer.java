/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.serialization;

import java.util.ArrayList;
import java.util.List;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.EventTable;
import de.cyface.protos.model.Event;
import de.cyface.utils.Validate;

/**
 * Serializes {@link Event}s in the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class EventSerializer {

    /**
     * The serialized events.
     */
    private final List<Event> events;

    /**
     * Fully initialized constructor of this class.
     * <p>
     * Use {@link #readFrom(Cursor)} to add {@link de.cyface.persistence.model.Event} from the database.
     * And {@link #result()} to receive the {@code Event}s in the serialized format.
     */
    public EventSerializer() {
        this.events = new ArrayList<>();
    }

    /**
     * Loads and parses {@link de.cyface.persistence.model.Event}s from a database {@code Cursor}.
     *
     * @param cursor the {@code Cursor} to load the {@code Event} data from.
     */
    public void readFrom(@NonNull final Cursor cursor) {

        // The ProtoBuf `events` field is `repeated`, i.e. build one Event per entry.
        while (cursor.moveToNext()) {
            final Event.Builder builder = Event.newBuilder();

            final long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(EventTable.COLUMN_TIMESTAMP));
            final String typeString = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_TYPE));
            @Nullable // Because not all EventTypes use this field
            final String value = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE));

            final Event.EventType type = Event.EventType.valueOf(typeString);
            builder.setTimestamp(timestamp)
                    .setType(type);

            if (value != null) {
                // ProtoBuf `string` must contain UTF-8 encoded text and cannot be longer than 2^32.
                Validate.isTrue(value.length() <= Math.pow(2, 32));
                builder.setValue(value);
            }
            events.add(builder.build());
        }
    }

    /**
     * @return the {@code Event}s in the serialized format.
     */
    public List<Event> result() {
        return events;
    }
}