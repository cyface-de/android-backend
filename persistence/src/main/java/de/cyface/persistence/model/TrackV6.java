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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;

/**
 * A {@link TrackV6} consists of {@link PersistedGeoLocation}s and {@link PersistedPressure}s (data points) collected
 * for a
 * {@link Measurement}. Its data points are ordered by time.
 * <p>
 * A {@code TrackV6} begins with the first data point of each type collected after start or resume was triggered
 * and stops with the last collected data point of each type before the next resume command is triggered or when the
 * very last location is reached.
 * <p>
 * <b>Attention:</b>
 * Keep this class unchanged until SDK 6 apps (databases) are migrated to SDK 7.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
public class TrackV6 {

    /**
     * The {@link PersistedGeoLocation}s collected for this {@link TrackV6}.
     */
    private final List<PersistedGeoLocation> geoLocations;

    /**
     * The {@link PersistedPressure}s collected for this {@link TrackV6}.
     */
    private final List<PersistedPressure> pressures;

    /**
     * Creates a completely initialized instance of this class.
     */
    public TrackV6() {
        this.geoLocations = new ArrayList<>();
        this.pressures = new ArrayList<>();
    }

    /**
     * Creates a completely initialized instance of this class.
     *
     * @param locations The locations to add to the track.
     * @param pressures The pressures to add to the track.
     */
    public TrackV6(final List<PersistedGeoLocation> locations, final List<PersistedPressure> pressures) {
        this.geoLocations = new ArrayList<>(locations);
        this.pressures = new ArrayList<>(pressures);
    }

    /**
     * @param location The {@link PersistedGeoLocation} to be added at the end of the {@link TrackV6}.
     */
    public void addLocation(@NonNull final PersistedGeoLocation location) {
        geoLocations.add(location);
    }

    /**
     * @param pressure The {@link PersistedPressure} to be added at the end of the {@link TrackV6}.
     */
    public void addPressure(@NonNull final PersistedPressure pressure) {
        pressures.add(pressure);
    }

    /**
     * @return The {@link PersistedGeoLocation}s collected for this {@link TrackV6}.
     */
    public List<PersistedGeoLocation> getGeoLocations() {
        return new ArrayList<>(geoLocations);
    }

    /**
     * @return The {@link PersistedPressure}s collected for this {@link TrackV6}.
     */
    public List<PersistedPressure> getPressures() {
        return pressures;
    }

    @NonNull
    @Override
    public String toString() {
        return "TrackV6{" +
                "geoLocations=" + geoLocations +
                ", pressures=" + pressures +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TrackV6 trackV6 = (TrackV6)o;
        return geoLocations.equals(trackV6.geoLocations) && pressures.equals(trackV6.pressures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geoLocations, pressures);
    }
}
