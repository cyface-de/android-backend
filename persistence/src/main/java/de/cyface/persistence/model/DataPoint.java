package de.cyface.persistence.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * An abstract base class for all data points processed by Cyface. It provides the generic functionality of a data point
 * to be unique on this device and to have a Unix timestamp associated with it.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class DataPoint implements Parcelable {
    /**
     * The system wide unique identifier for this {@code DataPoint}. If the point was not saved yet it may be
     * {@code null}, since the persistence layers assigns a unique identifier on saving a data point.
     * For AndroidDataAccessLayer.getIdOfNextUnSyncedMeasurement() to work the id must be long ASC
     */
    private Long identifier;
    /**
     * The Unix timestamp at which this {@code DataPoint} was measured in milliseconds.
     */
    private final long timestamp;

    /**
     * Creates a new completely initialized {@code DataPoint}.
     *
     * @param identifier The system wide unique identifier for this {@code DataPoint}.
     * @param timestamp The Unix timestamp at which this {@code DataPoint} was measured in milliseconds.
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
     *         {@code null}, since the persistence layers assigns a unique identifier on saving a data point.
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
     * MARK: Code for Parcelable interface.
     */

    /**
     * Recreates this point from the provided <code>Parcel</code>.
     *
     * @param in Serialized form of a <code>DataPoint</code>.
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
