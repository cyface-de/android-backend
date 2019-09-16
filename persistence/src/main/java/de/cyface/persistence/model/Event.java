package de.cyface.persistence.model;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@code Event}s are things that happen on the user device which may be important and are, thus, logged.
 * For examples see the {@link EventType}s.
 *
 * @author Armin Schnabel
 * @version 1.3.0
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
     * A String which provides information about the {@link Event} which is not already clarified by
     * {@link #getType()}, e.g.: Even type {@link EventType#VEHICLE_TYPE_CHANGE} requires a {@code #value}, e.g.
     * {@link Vehicle#CAR} which defines the new {@link Vehicle}. Or {@code Null} if the {@code EventType} does not
     * required this attribute.
     */
    private final String value;

    /**
     * @param type The {@link EventType} collected by this {@link Event}.
     * @param timestamp The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970.
     */
    public Event(EventType type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
        this.value = null;
    }

    /**
     * @param type The {@link EventType} collected by this {@link Event}.
     * @param timestamp The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970.
     * @param value A String which provides information about the {@link Event} which is not already clarified by
     *            {@link #getType()}, e.g.: Even type {@link EventType#VEHICLE_TYPE_CHANGE} requires a {@code #value},
     *            e.g. {@link Vehicle#CAR} which defines the new {@link Vehicle}. If no such information is required use
     *            the other constructor.
     */
    public Event(@NonNull final EventType type, final long timestamp, @NonNull final String value) {
        this.type = type;
        this.timestamp = timestamp;
        this.value = value;
    }

    @NonNull
    public EventType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Event event = (Event)o;
        // noinspection EqualsReplaceableByObjectsCall - not available in this minSDK version
        return timestamp == event.timestamp &&
                (value == null ? event.value == null : value.equals(event.value)) &&
                type == event.type;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {type, timestamp, value});
    }

    @NonNull
    @Override
    public String toString() {
        return "Event{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", value='" + value + '\'' +
                '}';
    }

    /**
     * Defines the {@code EventType}s which may be collected.
     * <p>
     * An example are the use of the life-cycle methods such as start, pause, resume, etc. which are required to
     * slice {@link Measurement}s into {@link Track}s before they are resumed.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 4.0.0
     */
    public enum EventType {
        LIFECYCLE_START("LIFECYCLE_START"), LIFECYCLE_PAUSE("LIFECYCLE_PAUSE"), LIFECYCLE_RESUME(
                "LIFECYCLE_RESUME"), LIFECYCLE_STOP("LIFECYCLE_STOP"), VEHICLE_TYPE_CHANGE("VEHICLE_TYPE_CHANGE");

        private String databaseIdentifier;

        EventType(final String databaseIdentifier) {
            this.databaseIdentifier = databaseIdentifier;
        }

        public String getDatabaseIdentifier() {
            return databaseIdentifier;
        }
    }
}
