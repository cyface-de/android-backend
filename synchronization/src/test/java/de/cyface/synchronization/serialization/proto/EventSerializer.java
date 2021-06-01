package de.cyface.synchronization.serialization.proto;

import java.util.ArrayList;
import java.util.List;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.persistence.EventTable;
import de.cyface.protos.model.Event;
import de.cyface.utils.Validate;

public class EventSerializer {

    private final List<Event> events;

    public EventSerializer() {
        this.events = new ArrayList<>();
    }

    public void readFrom(@NonNull final Cursor cursor) {

        // The ProtoBuf `events` field is `repeated`, i.e. build one Event per entry.
        while (cursor.moveToNext()) {
            final Event.Builder builder = Event.newBuilder();

            final long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(EventTable.COLUMN_TIMESTAMP));
            final String typeString = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_TYPE));
            @Nullable // Because not all EventTypes use this field FIXME: write a test for this
            final String value = cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE));

            // FIXME: Write a test to ensure all eventType String from database can be parsed
            final Event.EventType type = Event.EventType.valueOf(typeString);

            builder.setTimestamp(timestamp)
                    .setType(type);

            if (value != null) {
                // ProtoBuf `string` must contain UTF-8 encoded text and cannot be longer than 2^32.
                Validate.isTrue(value.length() <= Math.pow(2, 32));
                builder.setValue(value);
            }
        }
    }

    public List<Event> result() {
        return events;
    }
}
