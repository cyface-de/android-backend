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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * An {@code @Entity} which represents a persisted {@link Pressure}, usually captured by a barometer.
 * <p>
 * An instance of this class represents one row in a database table containing the pressure data.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Entity(tableName = "Pressure")
public class PersistedPressure extends Pressure {

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
    private final long measurementId;

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param timestamp The timestamp at which this data point was captured in milliseconds since 1.1.1970.
     * @param pressure The atmospheric pressure of this data point in hPa (millibar).
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    public PersistedPressure(final long timestamp, final double pressure, final long measurementId) {
        super(timestamp, pressure);
        this.pressure = pressure;
        this.measurementId = measurementId;
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

    @NonNull
    @Override
    public String toString() {
        return "PersistedPressure{" +
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
        PersistedPressure pressure1 = (PersistedPressure)o;
        return Double.compare(pressure1.pressure, pressure) == 0
                && Objects.equals(measurementId, pressure1.measurementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pressure, measurementId);
    }
}
