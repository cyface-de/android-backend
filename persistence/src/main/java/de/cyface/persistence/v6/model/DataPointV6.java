/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.persistence.v6.model;

import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

/**
 * An abstract base class for all data points processed by Cyface. It provides the generic functionality of a data point
 * to be unique on this device and to have a Unix timestamp associated with it.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
public abstract class DataPointV6 implements Parcelable {

    /**
     * The system wide unique identifier for this {@code DataPointV6}. If the point was not saved yet it may be
     * {@code null}, since the persistence layers assigns a unique identifier on saving a data point.
     * For AndroidDataAccessLayer.getIdOfNextUnSyncedMeasurement() to work the id must be long ASC
     */
    @PrimaryKey(autoGenerate = true)
    private int uid;

    /**
     * The Unix timestamp at which this {@code DataPointV6} was measured in milliseconds.
     */
    @ColumnInfo(name = "timestamp")
    private final long timestamp;

    /**
     * Creates a new completely initialized {@code DataPointV6}.
     *
     * @param timestamp The Unix timestamp at which this {@code DataPointV6} was measured in milliseconds.
     */
    public DataPointV6(final long timestamp) {
        if (timestamp < 0L) {
            throw new IllegalArgumentException("Illegal argument: timestamp was less than 0L!");
        }

        this.timestamp = timestamp;
    }

    /**
     * @return The system wide unique identifier for this {@code DataPointV6}. If the point was not saved yet it may be
     *         {@code null}, since the persistence layers assigns a unique identifier on saving a data point.
     */
    public int getUid() {
        return uid;
    }

    /**
     * @return The Unix timestamp at which this {@code DataPoint} was measured.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param uid The database identifier of this data point.
     */
    public void setUid(int uid) {
        this.uid = uid;
    }

    /*
     * MARK: Code for Parcelable interface.
     */

    /**
     * Recreates this point from the provided {@code Parcel}.
     *
     * @param in Serialized form of a {@code DataPointV6}.
     */
    protected DataPointV6(final Parcel in) {
        this.uid = (Integer)in.readValue(getClass().getClassLoader());
        this.timestamp = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(uid);
        dest.writeLong(timestamp);
    }

    @NonNull
    @Override
    public String toString() {
        return "DataPointV6{" +
                "uid=" + uid +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DataPointV6 that = (DataPointV6)o;
        return uid == that.uid && timestamp == that.timestamp;
    }

    @Override
    // To ease migration with `main` we keep the `hashCode()` similar to `DataPoint`:
    // https://github.com/cyface-de/android-backend/pull/258#discussion_r1071071508
    public int hashCode() {
        return Objects.hash(uid);
    }
}
