/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.datacapturing.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import de.cyface.persistence.model.ParcelablePoint3D;
import de.cyface.persistence.model.ParcelablePressure;

/**
 * Immutable data handling object for captured data.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 1.0.0
 */
public final class CapturedData implements Parcelable {
    /**
     * All accelerations captured since the last position was captured.
     */
    private final List<ParcelablePoint3D> accelerations;
    /**
     * All rotations captured since the last position was captured.
     */
    private final List<ParcelablePoint3D> rotations;
    /**
     * All directions captured since the last position was captured.
     */
    private final List<ParcelablePoint3D> directions;
    /**
     * All pressures captured since the last position was captured.
     */
    private final List<ParcelablePressure> pressures;

    /**
     * Creates a new captured data object from the provided data. The lists are copied and thus may be changed after
     * this constructor has been called without changes occurring in this object.
     * We are not using Collections.unmodifiableList(acc) as we experienced them NOT to be unmodifiable which resulted
     * into data loss. The reason for this could be that we are using parcelable which creates copies instead of
     * references.
     * We could try it with Serializable?
     *
     * @param accelerations The raw acceleration values as points in a 3D space.
     *            The list contains all captured values since the last GNSS fix.
     * @param rotations The raw rotational acceleration values as returned by the gyroscope.
     *            - * The list contains all captured values since the last GNSS fix.
     * @param directions The intensity of the earth's magnetic field on each of the three axis in space.
     *            The list contains all captured values since the last GNSS fix.
     * @param pressures The atmospheric pressure as returned by the barometer.
     *            The list contains all captured values since the last GNSS fix.
     */
    public CapturedData(final @NonNull List<ParcelablePoint3D> accelerations, final @NonNull List<ParcelablePoint3D> rotations,
                        final @NonNull List<ParcelablePoint3D> directions, final @NonNull List<ParcelablePressure> pressures) {
        this.accelerations = new LinkedList<>(accelerations);
        this.rotations = new LinkedList<>(rotations);
        this.directions = new LinkedList<>(directions);
        this.pressures = new LinkedList<>(pressures);
    }

    /**
     * @return All accelerations captured since the last position was captured.
     */
    public List<ParcelablePoint3D> getAccelerations() {
        return Collections.unmodifiableList(accelerations);
    }

    /**
     * @return All rotations captured since the last position was captured.
     */
    public List<ParcelablePoint3D> getRotations() {
        return Collections.unmodifiableList(rotations);
    }

    /**
     * @return All directions captured since the last position was captured.
     */
    public List<ParcelablePoint3D> getDirections() {
        return Collections.unmodifiableList(directions);
    }

    /**
     * @return All pressures captured since the last position was captured.
     */
    public List<ParcelablePressure> getPressures() {
        return Collections.unmodifiableList(pressures);
    }

    /*
     * MARK: Code for parcelable interface
     */

    /**
     * Recreates this object from the provided <code>Parcel</code>.
     *
     * @param in Serialized form of a <code>CapturedData</code> object.
     */
    protected CapturedData(Parcel in) {
        accelerations = in.createTypedArrayList(ParcelablePoint3D.CREATOR);
        rotations = in.createTypedArrayList(ParcelablePoint3D.CREATOR);
        directions = in.createTypedArrayList(ParcelablePoint3D.CREATOR);
        pressures = in.createTypedArrayList(ParcelablePressure.CREATOR);
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<CapturedData> CREATOR = new Creator<>() {
        @Override
        public CapturedData createFromParcel(Parcel in) {
            return new CapturedData(in);
        }

        @Override
        public CapturedData[] newArray(int size) {
            return new CapturedData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(accelerations);
        dest.writeTypedList(rotations);
        dest.writeTypedList(directions);
        dest.writeTypedList(pressures);
    }

    /*
     * MARK: Object Methods
     */

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        CapturedData that = (CapturedData)o;

        if (!accelerations.equals(that.accelerations))
            return false;
        if (!rotations.equals(that.rotations))
            return false;
        if (!directions.equals(that.directions))
            return false;
        return pressures.equals(that.pressures);

    }

    @Override
    public int hashCode() {
        int result = accelerations.hashCode();
        result = 31 * result + rotations.hashCode();
        result = 31 * result + directions.hashCode();
        result = 31 * result + pressures.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CapturedData{" + "accelerations=" + accelerations + ", rotations=" + rotations + ", directions="
                + directions + ", pressures=" + pressures + '}';
    }
}
