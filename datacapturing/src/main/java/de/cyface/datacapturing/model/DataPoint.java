package de.cyface.datacapturing.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * <p>
 * An abstract base class for all data points processed by Cyface. It provides the generic functionality of a data point
 * to be unique on this device and to have a Unix timestamp associated with it.
 * </p>
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class DataPoint implements Parcelable {
    /**
     * <p>
     * The system wide unique identifier for this {@code DataPoint}. If the point was not saved yet it may be
     * {@code null}, since the persistence layers assigns a unqiue identifier on saving a data point.
     * For AndroidDataAccessLayer.getIdOfNextUnSyncedMeasurement() to work the id must be long ASC
     * </p>
     */
    private Long identifier;
    /**
     * <p>
     * The Unix timestamp at which this {@code DataPoint} was measured.
     * </p>
     */
    private final long timestamp;

    /**
     * <p>
     * Creates a new completely initialized {@code DataPoint}.
     * </p>
     *
     * @param identifier The system wide unique identifier for this {@code DataPoint}.
     * @param timestamp The Unix timestamp at which this {@code DataPoint} was measured.
     */
    public DataPoint(final Long identifier, final long timestamp) {
        if(timestamp<0L) {
            throw new IllegalArgumentException("Illegal argument: timestamp was less than 0L!");
        }

        this.identifier = identifier;
        this.timestamp = timestamp;
    }

    /**
     * @return The system wide unique identifier for this {@code DataPoint}. If the point was not saved yet it may be
     *         {@code null}, since the persistence layers assigns a unqiue identifier on saving a data point.
     */
    public long getIdentifier() {
        return identifier;
    }

    /**
     * @return The Unix timestamp at which this {@code DataPoint} was measured.
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataPoint dataPoint = (DataPoint)o;

        return identifier == dataPoint.identifier;

    }

    @Override
    public int hashCode() {
        return (int)(identifier ^ (identifier >>> 32));
    }

    /*
     * Code for Parcelable interface.
     */

    public DataPoint(Parcel in) {
        this.identifier = (Long)in.readValue(getClass().getClassLoader());
        this.timestamp = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(identifier);
        dest.writeLong(timestamp);
    }
}
