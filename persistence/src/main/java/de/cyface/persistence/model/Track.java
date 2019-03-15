package de.cyface.persistence.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * A {@code Track} consists of {@link GeoLocation}s collected for a {@link Measurement}. Its {@code GeoLocation}s are
 * ordered by time.
 * <p>
 * A {@code Track} begins with the first {@code GeoLocation} collected after start or resume was triggered
 * and stops with the last collected {@code GeoLocation} before the next resume command is triggered or when the very
 * last locations is reached.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.0.0
 */
public class Track {

    /**
     * The {@link GeoLocation}s collected for this {@code Track}.
     */
    private List<GeoLocation> geoLocations;

    public Track() {
        this.geoLocations = new ArrayList<>();
    }

    /**
     * @param location The {@link GeoLocation} to be added at the end of the {@link Track}.
     */
    public void add(@NonNull final GeoLocation location) {
        geoLocations.add(location);
    }

    public List<GeoLocation> getGeoLocations() {
        return geoLocations;
    }

    @NonNull
    @Override
    public String toString() {
        return "Track{" + "geoLocations=" + geoLocations + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Track track = (Track)o;
        return geoLocations.equals(track.geoLocations);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(geoLocations.toArray());
    }
}
