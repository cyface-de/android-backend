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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * An {@code @Entity} which represents the data type of a pressure {@link DataPointV6}, usually captured by a barometer.
 * <p>
 * An instance of this class represents one row in a database table containing the pressure data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Entity()
public class Pressure extends DataPointV6 {

    /**
     * The atmospheric pressure of this data point in hPa (millibar).
     */
    @ColumnInfo(name = "pressure")
    private final double pressure;

    /**
     * The device-unique id of the measurement this data point belongs to.
     * <p>
     * TODO: Link `ForeignKey` when `Measurement` is migrated to `Room` (w/onDelete = CASCADE)
     */
    @ColumnInfo(name = "measurement_fk")
    private Long measurementId;

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @param pressure The atmospheric pressure of this data point in hPa (millibar).
     */
    public Pressure(final long timestamp, final double pressure) {
        super(timestamp);

        // lowest and highest pressure on earth with a few meters added because of inaccuracy
        if (pressure < 300. || pressure > 1_100.) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Illegal value for pressure. Is required to be between 300.0 and 1_100.0 but was %f.", pressure));
        }

        this.pressure = pressure;
    }

    /**
     * @return The atmospheric pressure of this data point in hPa (millibar).
     */
    public double getPressure() {
        return pressure;
    }

    /**
     * @return The device-unique id of the measurement this data point belongs to.
     */
    public long getMeasurementId() {
        return measurementId;
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
    protected Pressure(final @NonNull Parcel in) {
        super(in);
        pressure = in.readDouble();
    }

    /**
     * The {@code Parcelable} creator as required by the Android Parcelable specification.
     */
    public static final Creator<Pressure> CREATOR = new Creator<Pressure>() {
        @Override
        public Pressure createFromParcel(Parcel in) {
            return new Pressure(in);
        }

        @Override
        public Pressure[] newArray(int size) {
            return new Pressure[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeDouble(pressure);
    }

    @NonNull
    @Override
    public String toString() {
        return "Pressure{" +
                "pressure=" + pressure +
                ", measurementId=" + measurementId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        Pressure pressure1 = (Pressure)o;
        return Double.compare(pressure1.pressure, pressure) == 0 && measurementId == pressure1.measurementId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pressure, measurementId);
    }
}
