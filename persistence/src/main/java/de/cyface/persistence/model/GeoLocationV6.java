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

import static android.content.ContentValues.TAG;

import java.util.Locale;
import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import de.cyface.persistence.LocationCleaningStrategy;

/**
 * An {@code @Entity} which represents the data type of a geographical location, usually captured by a GNSS.
 * <p>
 * An instance of this class represents one row in a database table containing the location data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
// foreignKeys not linked as `Measurement` is not stored with `Room`
// FIXME: agree upon table names. In Room documentation it's singular, we used plural in SQLite API
@Entity(tableName = "location" /* foreignKeys = {@ForeignKey(entity = Measurement.class, ..., ..., onDelete = ForeignKey.CASCADE)} */)
// TODO: We should try to avoid using Parcelable on an @Entity object:
// https://stackoverflow.com/a/56058763/5815054
public class GeoLocationV6 implements Parcelable {

    // FIXME agree upon name of id column. Room documentation uses `int uid`.
    @PrimaryKey(autoGenerate = true)
    public long uid;

    /**
     * The timestamp at which this {@link GeoLocationV6} was captured in milliseconds since 1.1.1970.
     */
    @ColumnInfo(name = "timestamp")
    public long timestamp;

    /**
     * The captured latitude of this {@link GeoLocationV6} in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    @ColumnInfo(name = "lat")
    public double lat;

    /**
     * The captured longitude of this {@link GeoLocationV6} in decimal coordinates as a value between -180.0 and 180.0.
     */
    @ColumnInfo(name = "lon")
    public double lon;

    /**
     * The captured altitude of this {@link GeoLocationV6} in meters above WGS 84.
     */
    @ColumnInfo(name = "altitude")
    public double altitude;

    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    @ColumnInfo(name = "speed")
    public double speed;

    /**
     * The current accuracy of the measuring device in meters.
     *
     * FIXME: the format and unit is still in discussion.
     */
    @ColumnInfo(name = "accuracy")
    public double accuracy;

    /**
     * The current vertical accuracy of the measuring device in meters.
     *
     * FIXME: the format and unit is still in discussion.
     */
    @ColumnInfo(name = "vertical_accuracy")
    public double verticalAccuracy;

    /**
     * {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     *
     * FIXME: Discuss if is this actually needs to be persisted. We did not do persist before.
     */
    @ColumnInfo(name = "is_valid")
    public boolean isValid;

    // foreignKeys not linked as `Measurement` is not stored with `Room`
    @ColumnInfo(name = "measurement_fk")
    public long measurementId;

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
     * /
    public GeoLocationV6(final double lat, final double lon, final double altitude, final long timestamp,
            final double speed,
            final float accuracy, final float verticalAccuracy) {
        if (lat < -90. || lat > 90.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for latitude. Is required to be between -90.0 and 90.0 but was %f.", lat));
        }
        if (lon < -180. || lon > 180.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for longitude. Is required to be between -180.0 and 180.0 but was %f.", lon));
        }
        if (speed < 0.) {
            // Occurred on Huawai 10 Mate Pro (RAD-51)
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
        if (altitude < -500. || altitude > 10_000.) { // lowest and highest point on earth with a few meters added
                                                      // because of inaccuracy
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for altitude. Is required to be between -500.0 and 10_000.0 but was %f.", altitude));
        }
        if (verticalAccuracy < 0.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for verticalAccuracy. Is required to be positive but was %f.", verticalAccuracy));
        }

        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.speed = speed;
        this.accuracy = accuracy;
        this.isValid = true;
        this.altitude = altitude;
        this.verticalAccuracy = verticalAccuracy;
    }*/

    /**
     * Creates a new completely initialized <code>GeoLocationV6</code>.
     *
     * @param location The {@link GeoLocation} this {@link GeoLocationV6} is based on.
     * @param altitude The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     * @param verticalAccuracy The current vertical accuracy of the measuring device in centimeters.
     * /
    public GeoLocationV6(final GeoLocation location, final double altitude, final float verticalAccuracy) {
        this(location.getLat(), location.getLon(), altitude, location.getTimestamp(), location.getSpeed(),
                location.getAccuracy(), verticalAccuracy);
    }*/

    /**
     * @return The captured latitude of this GeoLocation in decimal coordinates as a value between -90.0 (south pole)
     *         and 90.0 (north pole).
     * /
    public double getLat() {
        return lat;
    }

    /**
     * @return The captured longitude of this {@code GeoLocation} in decimal coordinates as a value between -180.0 and
     *         180.0.
     * /
    public double getLon() {
        return lon;
    }

    /**
     * @return The timestamp at which this <code>GeoLocation</code> was captured in milliseconds since 1.1.1970.
     * /
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The current speed of the measuring device according to its location sensor in meters per second.
     * /
    public double getSpeed() {
        return speed;
    }

    /**
     * @return FIXME
     * /
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * @param valid {@code True} if this location is considered "clean" by the provided
     *            {@link LocationCleaningStrategy}.
     * /
    public void setValid(boolean valid) {
        isValid = valid;
    }

    /**
     * @return {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     * /
    public boolean isValid() {
        return isValid;
    }*/

    /**
     * @return The captured altitude of this {@code GeoLocationV6} in meters above WGS 84.
     * /
    public double getAltitude() {
        return altitude;
    }*/

    /**
     * @return The current vertical accuracy of the measuring device in centimeters.
     * /
    public float getVerticalAccuracy() {
        return verticalAccuracy;
    }*/

    /*
     * MARK: Parcelable Interface
     */

    public GeoLocationV6() {
        // Nothing to do
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>GeoLocationV6</code>.
     */
    // Parcelable interface requires this constructor, make {@code Room} ignore this constructor.
    @Ignore
    protected GeoLocationV6(final @NonNull Parcel in) {
        lat = in.readDouble();
        lon = in.readDouble();
        timestamp = in.readLong();
        speed = in.readDouble();
        accuracy = in.readFloat();
        isValid = in.readByte() != 0;
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
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeLong(timestamp);
        dest.writeDouble(speed);
        dest.writeDouble(accuracy);
        dest.writeByte((byte)(isValid ? 1 : 0));
        dest.writeDouble(altitude);
        dest.writeDouble(verticalAccuracy);
    }

    /*
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
    }*/
}
