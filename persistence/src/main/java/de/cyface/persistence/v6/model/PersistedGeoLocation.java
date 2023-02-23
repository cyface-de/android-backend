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
import androidx.room.Ignore;

/**
 * An {@code @Entity} which represents a persisted {@link GeoLocationV6}, usually captured by a GNSS.
 * <p>
 * An instance of this class represents one row in a database table containing the location data.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
@Entity(tableName = "Location")
public class PersistedGeoLocation extends GeoLocationV6 {

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
     * {@code True} if this location is considered "clean" by the provided {@code LocationCleaningStrategy}.
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
    private final long measurementId;

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
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    public PersistedGeoLocation(final long timestamp, final double lat, final double lon, final Double altitude,
            final double speed, final double accuracy, final Double verticalAccuracy, final long measurementId) {
        super(timestamp, lat, lon, altitude, speed, accuracy, verticalAccuracy);
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.verticalAccuracy = verticalAccuracy;
        this.measurementId = measurementId;
    }

    /**
     * Creates a new completely initialized instance of this class.
     *
     * @param location The cached {@link GeoLocationV6} to create the {@link PersistedGeoLocation} from.
     * @param measurementId The device-unique id of the measurement this data point belongs to.
     */
    public PersistedGeoLocation(final GeoLocationV6 location, final long measurementId) {
        super(location.getTimestamp(), location.getLat(), location.getLon(), location.getAltitude(),
                location.getSpeed(), location.getAccuracy(), location.getVerticalAccuracy());
        this.lat = location.getLat();
        this.lon = location.getLon();
        this.altitude = location.getAltitude();
        this.speed = location.getSpeed();
        this.accuracy = location.getAccuracy();
        this.verticalAccuracy = location.getVerticalAccuracy();
        this.measurementId = measurementId;
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
     * @return The device-unique id of the measurement this data point belongs to.
     */
    public long getMeasurementId() {
        return measurementId;
    }

    /**
     * @param valid {@code True} if this location is considered "clean" by the provided
     *            {@code LocationCleaningStrategy}.
     */
    public void setValid(boolean valid) {
        isValid = valid;
    }

    @NonNull
    @Override
    public String toString() {
        return "PersistedGeoLocation{" +
                "lat=" + lat +
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
        PersistedGeoLocation that = (PersistedGeoLocation)o;
        return Double.compare(that.lat, lat) == 0
                && Double.compare(that.lon, lon) == 0 && Double.compare(that.speed, speed) == 0
                && Double.compare(that.accuracy, accuracy) == 0 && measurementId == that.measurementId
                && Objects.equals(altitude, that.altitude) && Objects.equals(verticalAccuracy, that.verticalAccuracy)
                && Objects.equals(isValid, that.isValid);
    }

    // To ease migration with `main` branch, we keep the models similar to `GeoLocation` but might want to change this
    // in future. https://github.com/cyface-de/android-backend/pull/258#discussion_r1071077508
    @Override
    public int hashCode() {
        return Objects.hash(lat, lon, altitude, speed, accuracy, verticalAccuracy, measurementId);
    }
}
