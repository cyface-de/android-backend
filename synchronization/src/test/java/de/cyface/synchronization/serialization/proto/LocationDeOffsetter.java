package de.cyface.synchronization.serialization.proto;

import org.apache.commons.lang3.Validate;

import de.cyface.persistence.serialization.proto.LocationOffsetter;

public class LocationDeOffsetter {
    private final DeOffsetter ts;
    private final DeOffsetter lat;
    private final DeOffsetter lon;
    private final DeOffsetter acc;
    private final DeOffsetter spe;

    public LocationDeOffsetter() {
        ts = new DeOffsetter();
        lat = new DeOffsetter();
        lon = new DeOffsetter();
        acc = new DeOffsetter();
        spe = new DeOffsetter();
    }

    public LocationOffsetter.Location absolute(LocationOffsetter.Location offsets) {
        final long timestamp = ts.absolute(offsets.getTimestamp());
        final long latitude = lat.absolute(offsets.getLatitude());
        final long longitude = lon.absolute(offsets.getLongitude());
        final long accuracy = acc.absolute(offsets.getAccuracy());
        final long speed = spe.absolute(offsets.getSpeed());
        Validate.isTrue(latitude <= Integer.MAX_VALUE);
        Validate.isTrue(longitude <= Integer.MAX_VALUE);
        Validate.isTrue(accuracy <= Integer.MAX_VALUE);
        Validate.isTrue(speed <= Integer.MAX_VALUE);
        return new LocationOffsetter.Location(timestamp, (int)latitude, (int)longitude, (int)accuracy, (int)speed);
    }
}