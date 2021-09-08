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
package de.cyface.persistence.model;

import static android.content.ContentValues.TAG;

import java.util.Locale;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.persistence.LocationCleaningStrategy;

/**
 * A position captured by the {@code DataCapturingService}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.1
 * @since 1.0.0
 */
public class GeoLocation implements Parcelable {

    /**
     * The captured latitude of this {@code GeoLocation} in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    private final double lat;
    /**
     * The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0 and 180.0.
     */
    private final double lon;
    /**
     * The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     */
    private final long timestamp;
    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    private final double speed;
    /**
     * The current accuracy of the measuring device in centimeters.
     */
    private final float accuracy;
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
     * @param accuracy The current accuracy of the measuring device in centimeters.
     */
    public GeoLocation(final double lat, final double lon, final long timestamp, final double speed,
            final float accuracy) {
        if (lat < -90. || lat > 90.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %f.", lat));
        }
        if (lon < -180. || lon > 180.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %f.", lon));
        }
        if (speed < 0.) {
            // Occurred on Huawai 10 Mate Pro (RAD-51) and Huawei P30 Android 10 (2021/07)
            Log.w(TAG,
                    String.format(Locale.US, "Illegal value for speed. Is required to be positive but was %f.", speed));
        }
        if (accuracy < 0.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for accuracy. Is required to be positive but was %f.", accuracy));
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for timestamp. Is required to be greater then 0L but was %d.", timestamp));
        }

        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.speed = speed;
        this.accuracy = accuracy;
        this.isValid = true;
    }

    /**
     * @return The captured latitude of this GeoLocation in decimal coordinates as a value between -90.0 (south pole)
     *         and 90.0 (north pole).
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0 and
     *         180.0.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The current speed of the measuring device according to its location sensor in meters per second.
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * @return The current accuracy of the measuring device in centimeters.
     */
    public float getAccuracy() {
        return accuracy;
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
    protected GeoLocation(final @NonNull Parcel in) {
        lat = in.readDouble();
        lon = in.readDouble();
        timestamp = in.readLong();
        speed = in.readDouble();
        accuracy = in.readFloat();
        isValid = in.readByte() != 0;
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    @SuppressWarnings("Convert2Diamond") // `cannot use '<>' with anonymous inner classes`
    public static final Creator<GeoLocation> CREATOR = new Creator<GeoLocation>() {
        @Override
        public GeoLocation createFromParcel(Parcel in) {
            return new GeoLocation(in);
        }

        @Override
        public GeoLocation[] newArray(int size) {
            return new GeoLocation[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeLong(timestamp);
        dest.writeDouble(speed);
        dest.writeFloat(accuracy);
        dest.writeByte((byte)(isValid ? 1 : 0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoLocation location = (GeoLocation)o;
        return Double.compare(location.lat, lat) == 0 &&
                Double.compare(location.lon, lon) == 0 &&
                timestamp == location.timestamp &&
                Double.compare(location.speed, speed) == 0 &&
                Float.compare(location.accuracy, accuracy) == 0 &&
                isValid == location.isValid;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(lat);
        result = (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lon);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        result = 31 * result + (int)(timestamp ^ (timestamp >>> 32));
        temp = Double.doubleToLongBits(speed);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        result = 31 * result + (accuracy != +0.0f ? Float.floatToIntBits(accuracy) : 0);
        result = 31 * result + (isValid ? 1231 : 1237);
        return result;
    }
}
