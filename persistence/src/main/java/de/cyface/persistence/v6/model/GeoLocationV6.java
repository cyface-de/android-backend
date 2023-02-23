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

import static de.cyface.persistence.v6.Constants.TAG;

import java.util.Locale;
import java.util.Objects;

import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Ignore;

/**
 * This class represents a geographical location, usually captured by a GNSS.
 * <p>
 * An instance of this class represents a data point captured and cached but not yet persisted. Such an
 * {@link PersistedGeoLocation} requires the measurement id to be set.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 * <p>
 * {@code ParcelableGeoLocation} DB Version 17 now contains accuracy in meters.
 * {@link GeoLocationV6} accuracy is still in the old format (cm), vertical in the new (m)
 * This is fixed after merging `measures` and `v6` databases (both in m)
 * <p>
 * This is fixed automatically in `measures` V16-V17 upgrade
 * which is already implemented in the SDK 7 branch.
 * <p>
 * <b>DO NOT CHANGE THIS</b> until we migrate the SDK 6 apps/dbs to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
public class GeoLocationV6 extends DataPointV6 {

    /**
     * The captured latitude of this data point in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    private final double lat;

    /**
     * The captured longitude of this data point in decimal coordinates as a value between -180.0 and 180.0.
     */
    private final double lon;

    /**
     * The captured altitude of this data point in meters above WGS 84 if available.
     */
    private final Double altitude;

    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    private final double speed;

    /**
     * The current accuracy of the measuring device in centimeters.
     * <p>
     * TODO: Use same unit as {@link #verticalAccuracy} when migration completely to Room
     * TODO: Consider making accuracy `Double` and write `null` when `Location.hasAccuracy()` is false,
     * but only after migrating to Room. The transfer file format might need adjustment for that.
     */
    private final double accuracy;

    /**
     * The current vertical accuracy of the measuring device in meters if available.
     */
    private final Double verticalAccuracy;

    /**
     * {@code True} if this location is considered "clean" by the provided {@code LocationCleaningStrategy}.
     * <p>
     * This is not persisted, as the validity can be different depending on the strategy implementation.
     */
    private Boolean isValid;

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since
     *            1.1.1970.
     * @param lat The captured latitude of this data point in decimal coordinates as a value between -90.0 (south
     *            pole) and 90.0 (north pole).
     * @param lon The captured longitude of this data point in decimal coordinates as a value between -180.0
     *            and 180.0.
     * @param altitude The captured altitude of this data point in meters above WGS 84.
     * @param speed The current speed of the measuring device according to its location sensor in meters per second.
     * @param accuracy The current accuracy of the measuring device in centimeters.
     * @param verticalAccuracy The current vertical accuracy of the measuring device in meters.
     */
    public GeoLocationV6(final long timestamp, final double lat, final double lon, final Double altitude,
                         final double speed, final double accuracy, final Double verticalAccuracy) {
        super(timestamp);

        if (lat < -90. || lat > 90.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %f.", lat));
        }
        if (lon < -180. || lon > 180.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %f.", lon));
        }
        // lowest and highest point on earth with a few meters added because of inaccuracy
        if (altitude != null && (altitude < -500. || altitude > 10_000.)) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for altitude. Is required to be between -500.0 and 10_000.0 but was %f.", altitude));
        }
        if (speed < 0.) {
            // Occurred on Huawei 10 Mate Pro (RAD-51)
            Log.w(TAG,
                    String.format(Locale.US, "Illegal value for speed. Is required to be positive but was %f.", speed));
        }
        if (accuracy < 0.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for accuracy. Is required to be positive but was %f.", accuracy));
        }
        if (verticalAccuracy != null && (verticalAccuracy < 0.)) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for verticalAccuracy. Is required to be positive but was %f.", verticalAccuracy));
        }

        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.verticalAccuracy = verticalAccuracy;
    }

    /**
     * @return The captured latitude of this data point in decimal coordinates as a value between -90.0 (south pole)
     *         and 90.0 (north pole).
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The captured longitude of this data point in decimal coordinates as a value between -180.0 and
     *         180.0.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The captured altitude of this data point in meters above WGS 84 of available.
     */
    public Double getAltitude() {
        return altitude;
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
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * @return The current vertical accuracy of the measuring device in meters if available.
     */
    public Double getVerticalAccuracy() {
        return verticalAccuracy;
    }

    /**
     * @return {@code True} if this location is considered "clean" by the provided {@code LocationCleaningStrategy}.
     */
    @SuppressWarnings("unused") // Part of the API
    public Boolean isValid() {
        return isValid;
    }

    /**
     * @param valid {@code True} if this location is considered "clean" by the provided
     *            {@code LocationCleaningStrategy}.
     */
    public void setValid(boolean valid) {
        isValid = valid;
    }

    /*
     * MARK: Parcelable Interface
     */

    /**
     * Constructor as required by {@code Parcelable} implementation.
     *
     * @param in A {@code Parcel} that is a serialized version of a data point.
     */
    @Ignore // Parcelable requires this constructor, make {@code Room} ignore this constructor.
    protected GeoLocationV6(final @NonNull Parcel in) {
        super(in);
        lat = in.readDouble();
        lon = in.readDouble();
        altitude = in.readDouble();
        speed = in.readDouble();
        accuracy = in.readDouble();
        verticalAccuracy = in.readDouble();
        isValid = in.readByte() != 0;
    }

    /**
     * The {@code Parcelable} creator as required by the Android Parcelable specification.
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
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeDouble(altitude);
        dest.writeDouble(speed);
        dest.writeDouble(accuracy);
        dest.writeDouble(verticalAccuracy);
        dest.writeByte((byte)(isValid ? 1 : 0));
    }

    @NonNull
    @Override
    public String toString() {
        return "GeoLocationV6{" +
                "lat=" + lat +
                ", lon=" + lon +
                ", altitude=" + altitude +
                ", speed=" + speed +
                ", accuracy=" + accuracy +
                ", verticalAccuracy=" + verticalAccuracy +
                ", isValid=" + isValid +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoLocationV6 that = (GeoLocationV6)o;
        return Double.compare(that.lat, lat) == 0
                && Double.compare(that.lon, lon) == 0 && Double.compare(that.speed, speed) == 0
                && Double.compare(that.accuracy, accuracy) == 0 && Objects.equals(altitude, that.altitude)
                && Objects.equals(verticalAccuracy, that.verticalAccuracy) && Objects.equals(isValid, that.isValid);
    }

    // To ease migration with `main` branch, we keep the models similar to `GeoLocation` but might want to change this
    // in future. https://github.com/cyface-de/android-backend/pull/258#discussion_r1071077508
    @Override
    public int hashCode() {
        return Objects.hash(lat, lon, altitude, speed, accuracy, verticalAccuracy);
    }
}
