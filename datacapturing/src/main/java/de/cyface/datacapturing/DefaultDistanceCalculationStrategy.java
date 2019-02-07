package de.cyface.datacapturing;

import android.location.Location;

import androidx.annotation.NonNull;
import de.cyface.persistence.model.GeoLocation;

// FIXME: Is this architecture better or worse than not merging the calculateDistance into the EventsHandlingStrategy
// but just require a DistanceCalculationStrategy in the DCS constructor?
public class DefaultDistanceCalculationStrategy {

    /**
     * The {@link Location#getProvider()} String used to create a new {@link Location}.
     */
    private final static String DEFAULT_PROVIDER = "default";

    public double calculateDistance(@NonNull GeoLocation lastLocation, @NonNull GeoLocation newLocation) {

        final Location previousLocation = new Location(DEFAULT_PROVIDER);
        final Location nextLocation = new Location(DEFAULT_PROVIDER);
        previousLocation.setLatitude(lastLocation.getLat());
        previousLocation.setLongitude(lastLocation.getLon());
        nextLocation.setLatitude(newLocation.getLat());
        nextLocation.setLongitude(newLocation.getLon());

        return previousLocation.distanceTo(nextLocation);
    }
}
