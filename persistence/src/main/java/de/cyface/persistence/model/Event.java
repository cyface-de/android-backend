package de.cyface.persistence.model;

import java.util.Arrays;

import androidx.annotation.NonNull;

/**
 * {@code Event}s are things that happen on the user device which may be important and are, thus, logged.
 * For examples see the {@link EventType}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class Event {

    /**
     * The {@code EventType} collected by this {@link Event}.
     */
    private EventType type;
    /**
     * The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970.
     */
    private long timestamp;

    /**
     * @param type The {@link EventType} collected by this {@link Event}.
     * @param timestamp The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970.
     */
    public Event(EventType type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }

    public EventType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Event event = (Event)o;
        return timestamp == event.timestamp && type == event.type;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {type, timestamp});
    }

    @NonNull
    @Override
    public String toString() {
        return "Event{" + "type=" + type + ", timestamp=" + timestamp + '}';
    }

    /**
     * Defines the {@code EventType}s which may be collected.
     * <p>
     * An example are the use of the life-cycle methods such as start, pause, resume, etc. which are required to
     * slice {@link Measurement}s into {@link Track}s before they are resumed.
     */
    public enum EventType {
        LIFECYCLE_START("LIFECYCLE_START"), LIFECYCLE_PAUSE("LIFECYCLE_PAUSE"), LIFECYCLE_RESUME(
                "LIFECYCLE_RESUME"), LIFECYCLE_STOP("LIFECYCLE_STOP");

        private String databaseIdentifier;

        EventType(final String databaseIdentifier) {
            this.databaseIdentifier = databaseIdentifier;
        }

        public String getDatabaseIdentifier() {
            return databaseIdentifier;
        }
    }
}
