/*
 * Copyright 2017-2021 Cyface GmbH
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
package de.cyface.persistence.v1.model;

import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import de.cyface.persistence.v1.LocationCleaningStrategy;
import de.cyface.persistence.v6.model.GeoLocationV6;

/**
 * A position captured by the {@code DataCapturingService}.
 * <p>
 * <b>Attention:</b>
 * {@link ParcelableGeoLocation} DB Version 17 now contains accuracy in meters.
 * {@link GeoLocationV6} accuracy is still in the old format (cm), vertical in the new (m)
 * This is fixed after merging `measures` and `v6` databases (both in m)
 * <p>
 * This is fixed automatically in `measures` V16-V17 upgrade
 * which is already implemented in the SDK 7 branch.
 * <p>
 * <b>DO NOT CHANGE THIS</b> until we migrate the SDK 6 apps/dbs to SDK 7.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 1.0.0
 */
public class ParcelableGeoLocation extends de.cyface.serializer.GeoLocation implements Parcelable {

    /**
     * {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     */
    private boolean isValid;

    /**
     * Creates a new completely initialized <code>GeoLocation</code>.
     *
     * @param lat The captured latitude of this GeoLocation in decimal coordinates as a value between -90.0 (south pole)
     *            and 90.0 (north pole).
     * @param lon The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0
     *            and 180.0.
     * @param timestamp The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since
     *            1.1.1970.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in meters.
     */
    public ParcelableGeoLocation(final double lat, final double lon, final long timestamp, final double speed,
                                 final double accuracy) {
        super(lat, lon, timestamp, speed, accuracy);
        this.isValid = true;
    }

    /**
     * @param valid {@code True} if this location is considered "clean" by the provided
     *            {@link LocationCleaningStrategy}.
     */
    public void setValid(boolean valid) {
        isValid = valid;
    }

    /**
     * @return {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     */
    public boolean isValid() {
        return isValid;
    }
    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>GeoLocation</code>.
     */
    protected ParcelableGeoLocation(final @NonNull Parcel in) {
        super(in.readDouble(), in.readDouble(), in.readLong(), in.readDouble(), in.readDouble());
        setValid(in.readByte() != 0);
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    @SuppressWarnings("Convert2Diamond") // `cannot use '<>' with anonymous inner classes`
    public static final Creator<ParcelableGeoLocation> CREATOR = new Creator<ParcelableGeoLocation>() {
        @Override
        public ParcelableGeoLocation createFromParcel(Parcel in) {
            return new ParcelableGeoLocation(in);
        }

        @Override
        public ParcelableGeoLocation[] newArray(int size) {
            return new ParcelableGeoLocation[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(getLat());
        dest.writeDouble(getLon());
        dest.writeLong(getTimestamp());
        dest.writeDouble(getSpeed());
        dest.writeDouble(getAccuracy());
        dest.writeByte((byte)(isValid ? 1 : 0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ParcelableGeoLocation that = (ParcelableGeoLocation)o;
        return isValid == that.isValid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isValid);
    }
}
