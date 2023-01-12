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

import static de.cyface.persistence.Constants.TAG;

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
@Entity(tableName = "Location")
public class GeoLocationV6 implements Parcelable {

    /**
     * The database identifier of this data point.
     */
    @PrimaryKey(autoGenerate = true)
    private int uid;

    /**
     * The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     */
    @ColumnInfo(name = "timestamp")
    private final long timestamp;

    /**
     * The captured latitude of this data point in decimal coordinates as a value between -90.0 (south pole)
     * and 90.0 (north pole).
     */
    @ColumnInfo(name = "lat")
    private final double lat;

    /**
     * The captured longitude of this data point in decimal coordinates as a value between -180.0 and 180.0.
     */
    @ColumnInfo(name = "lon")
    private final double lon;

    /**
     * The captured altitude of this data point in meters above WGS 84 if available.
     */
    @ColumnInfo(name = "altitude")
    private final Double altitude;

    /**
     * The current speed of the measuring device according to its location sensor in meters per second.
     */
    @ColumnInfo(name = "speed")
    private final double speed;

    /**
     * The current accuracy of the measuring device in centimeters.
     * <p>
     * TODO: Use same unit as {@link #verticalAccuracy} when migration completely to Room
     * TODO: Consider making accuracy `Double` and write `null` when `Location.hasAccuracy()` is false,
     * but only after migrating to Room. The transfer file format might need adjustment for that.
     */
    @ColumnInfo(name = "accuracy")
    private final double accuracy;

    /**
     * The current vertical accuracy of the measuring device in meters if available.
     */
    @ColumnInfo(name = "vertical_accuracy")
    private final Double verticalAccuracy;

    /**
     * {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     * <p>
     * This is not persisted, as the validity can be different depending on the strategy implementation.
     */
    @Ignore
    private Boolean isValid;

    /**
     * The device-unique id of the measurement this data point belongs to.
     * <p>
     * TODO: Link `ForeignKey` when `Measurement` is migrated to `Room` (w/onDelete = CASCADE)
     */
    @ColumnInfo(name = "measurement_fk")
    private long measurementId;

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

        if (timestamp < 0L) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for timestamp. Is required to be greater then 0L but was %d.", timestamp));
        }
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

        this.timestamp = timestamp;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.verticalAccuracy = verticalAccuracy;
    }

    /**
     * @return The database identifier of this data point.
     */
    public int getUid() {
        return uid;
    }

    /**
     * @return The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     */
    public long getTimestamp() {
        return timestamp;
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
     * @return {@code True} if this location is considered "clean" by the provided {@link LocationCleaningStrategy}.
     */
    @SuppressWarnings("unused") // Part of the API
    public Boolean isValid() {
        return isValid;
    }

    /**
     * @return The device-unique id of the measurement this data point belongs to.
     */
    public long getMeasurementId() {
        return measurementId;
    }

    /**
     * @param valid {@code True} if this location is considered "clean" by the provided
     *            {@link LocationCleaningStrategy}.
     */
    public void setValid(boolean valid) {
        isValid = valid;
    }

    /**
     * @param uid The database identifier of this data point.
     */
    public void setUid(int uid) {
        this.uid = uid;
    }

    /**
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    public void setMeasurementId(long measurementId) {
        this.measurementId = measurementId;
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
        uid = in.readInt(); // FIXME: Added, DataPoint encodes identifier but GeoLocation has no id
        timestamp = in.readLong();
        lat = in.readDouble();
        lon = in.readDouble();
        altitude = in.readDouble();
        speed = in.readDouble();
        accuracy = in.readDouble();
        verticalAccuracy = in.readDouble();
        isValid = in.readByte() != 0;
        measurementId = in.readLong(); // FIXME: Added, do we need this? GeoLocation has no such field
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
        dest.writeLong(uid); // FIXME: Added, DataPoint encodes identifier but GeoLocation has no id
        dest.writeLong(timestamp);
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeDouble(altitude);
        dest.writeDouble(speed);
        dest.writeDouble(accuracy);
        dest.writeDouble(verticalAccuracy);
        dest.writeByte((byte)(isValid ? 1 : 0));
        dest.writeLong(measurementId); // FIXME: Added, do we need this? GeoLocation has no such field
    }

    @NonNull
    @Override
    public String toString() {
        return "GeoLocationV6{" +
                "uid=" + uid +
                ", timestamp=" + timestamp +
                ", lat=" + lat +
                ", lon=" + lon +
                ", altitude=" + altitude +
                ", speed=" + speed +
                ", accuracy=" + accuracy +
                ", verticalAccuracy=" + verticalAccuracy +
                ", isValid=" + isValid +
                ", measurementId=" + measurementId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GeoLocationV6 that = (GeoLocationV6)o;
        return uid == that.uid && timestamp == that.timestamp && Double.compare(that.lat, lat) == 0
                && Double.compare(that.lon, lon) == 0 && Double.compare(that.speed, speed) == 0
                && Double.compare(that.accuracy, accuracy) == 0 && measurementId == that.measurementId
                && Objects.equals(altitude, that.altitude) && Objects.equals(verticalAccuracy, that.verticalAccuracy)
                && Objects.equals(isValid, that.isValid);
    }

    @Override
    public int hashCode() { // FIXME: w/ or w/o uid, measurement_fk? (definitely w/o isValid)
        return Objects.hash(timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy);
    }
}
