package de.cyface.persistence.serialization.proto;

import org.apache.commons.lang3.Validate;

public class LocationOffsetter {
    private final Offsetter ts;
    private final Offsetter lat;
    private final Offsetter lon;
    private final Offsetter acc;
    private final Offsetter spe;

    public LocationOffsetter() {
        ts = new Offsetter();
        lat = new Offsetter();
        lon = new Offsetter();
        acc = new Offsetter();
        spe = new Offsetter();
    }

    public LocationOffsets offset(Formatter.Location location) {
        final long timestamp = ts.offset(location.getTimestamp());
        final long latitude = lat.offset(location.getLatitude());
        final long longitude = lon.offset(location.getLongitude());
        final long accuracy = acc.offset(location.getAccuracy());
        final long speed = spe.offset(location.getSpeed());
        Validate.isTrue(latitude <= Integer.MAX_VALUE);
        Validate.isTrue(longitude <= Integer.MAX_VALUE);
        Validate.isTrue(accuracy <= Integer.MAX_VALUE);
        Validate.isTrue(speed <= Integer.MAX_VALUE);
        return new LocationOffsets(timestamp, (int)latitude, (int)longitude, (int)accuracy, (int)speed);
    }

    public static class LocationOffsets {
        private final long timestamp;
        private final int latitude;
        private final int longitude;
        private final int accuracy;
        private final int speed;

        public LocationOffsets(long timestamp, int latitude, int longitude, int accuracy, int speed) {
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.speed = speed;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getLatitude() {
            return latitude;
        }

        public int getLongitude() {
            return longitude;
        }

        public int getAccuracy() {
            return accuracy;
        }

        public int getSpeed() {
            return speed;
        }
    }
}