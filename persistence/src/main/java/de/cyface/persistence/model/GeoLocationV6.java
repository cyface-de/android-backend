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
package de.cyface.persistence.model;

import java.util.Locale;
import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * A position captured by the {@code DataCapturingService} which also includes altitude data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
public class GeoLocationV6 extends GeoLocation implements Parcelable {

    /**
     * The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     */
    private final double altitude;
    /**
     * The current vertical accuracy of the measuring device in centimeters.
     */
    private final float verticalAccuracy;

    /**
     * Creates a new completely initialized <code>GeoLocationV6</code>.
     *
     * @param lat The captured latitude of this GeoLocationV6 in decimal coordinates as a value between -90.0 (south
     *            pole)
     *            and 90.0 (north pole).
     * @param lon The captured longitude of this {@code GeoLocationV6} in decimal coordinates as a value between -180.0
     *            and 180.0.
     * @param altitude The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     * @param timestamp The timestamp at which this <code>GeoLocationV6</code> was captured in milliseconds since
     *            1.1.1970.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in centimeters.
     * @param verticalAccuracy The current vertical accuracy of the measuring device in centimeters.
     */
    public GeoLocationV6(final double lat, final double lon, final double altitude, final long timestamp,
            final double speed,
            final float accuracy, final float verticalAccuracy) {
        super(lat, lon, timestamp, speed, accuracy);
        if (altitude < -500. || altitude > 10_000.) { // lowest and highest point on earth with a few meters added
                                                      // because of inaccuracy
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for altitude. Is required to be between -500.0 and 10_000.0 but was %f.", altitude));
        }
        if (verticalAccuracy < 0.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for verticalAccuracy. Is required to be positive but was %f.", verticalAccuracy));
        }

        this.altitude = altitude;
        this.verticalAccuracy = verticalAccuracy;
    }

    /**
     * Creates a new completely initialized <code>GeoLocationV6</code>.
     *
     * @param location The {@link GeoLocation} this {@link GeoLocationV6} is based on.
     * @param altitude The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     * @param verticalAccuracy The current vertical accuracy of the measuring device in centimeters.
     */
    public GeoLocationV6(final GeoLocation location, final double altitude, final float verticalAccuracy) {
        this(location.getLat(), location.getLon(), altitude, location.getTimestamp(), location.getSpeed(), location.getAccuracy(), verticalAccuracy);
    }

    /**
     * @return The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * @return The current vertical accuracy of the measuring device in centimeters.
     */
    public float getVerticalAccuracy() {
        return verticalAccuracy;
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>GeoLocationV6</code>.
     */
    protected GeoLocationV6(final @NonNull Parcel in) {
        super(in);
        altitude = in.readDouble();
        verticalAccuracy = in.readFloat();
    }

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<GeoLocationV6> CREATOR = new Creator<GeoLocationV6>() {
        @Override
        public GeoLocationV6 createFromParcel(Parcel in) {
            return new GeoLocationV6(in);
        }

        @Override
        public GeoLocationV6[] newArray(int size) {
            return new GeoLocationV6[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeDouble(altitude);
        dest.writeFloat(verticalAccuracy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        GeoLocationV6 that = (GeoLocationV6)o;
        return Double.compare(that.altitude, altitude) == 0
                && Float.compare(that.verticalAccuracy, verticalAccuracy) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), altitude, verticalAccuracy);
    }
}
