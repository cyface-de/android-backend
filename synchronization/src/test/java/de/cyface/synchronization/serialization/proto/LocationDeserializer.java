package de.cyface.synchronization.serialization.proto;

import java.util.ArrayList;
import java.util.List;

import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.serialization.proto.LocationOffsetter;
import de.cyface.protos.model.LocationRecords;

public class LocationDeserializer {

    public static List<GeoLocation> deserialize(final LocationRecords entries) {

        // The de-offsetter must be initialized once for each location
        final LocationDeOffsetter deOffsetter = new LocationDeOffsetter();

        final List<GeoLocation> ret = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {

            // The proto serialized comes in a different format and in offset-format
            final LocationOffsetter.Location offsets = new LocationOffsetter.Location(entries.getTimestamp(i), entries.getLatitude(i),
                    entries.getLongitude(i), entries.getAccuracy(i), entries.getSpeed(i));
            final LocationOffsetter.Location absolutes = deOffsetter.absolute(offsets);
            final DeFormatter.Location deFormatted = new DeFormatter.Location(absolutes.getTimestamp(),
                    absolutes.getLatitude(), absolutes.getLongitude(), absolutes.getSpeed(), absolutes.getAccuracy());

            final GeoLocation entry = new GeoLocation(deFormatted.getLatitude(), deFormatted.getLongitude(),
                    deFormatted.getTimestamp(), deFormatted.getSpeed(), deFormatted.getAccuracy());
            ret.add(entry);
        }
        return ret;
    }
}
