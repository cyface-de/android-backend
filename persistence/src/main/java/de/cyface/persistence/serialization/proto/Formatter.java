package de.cyface.persistence.serialization.proto;

import org.apache.commons.lang3.Validate;

public class Formatter {

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param coordinate the coordinate-part, e.g.: 51.012345 or 13.012300
     * @return the formatted number, e.g. 51_012345 or 13_012300
     */
    private static int coordinate(double coordinate) {
        final long converted = Math.round(coordinate * 1_000_000);
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param speedMps the speed in m/s, e.g.: 11.0m/s
     * @return the formatted number, e.g. 11_00 cm/s
     */
    private static int speed(double speedMps) {
        final long converted = Math.round(speedMps * 100);
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    public static class Location {
        private final long timestamp;
        private final int latitude;
        private final int longitude;
        private final int speed;
        private final int accuracy;

        public Location(long timestamp, double latitude, double longitude, double speed, int accuracy) {

            this.timestamp = timestamp; // already in ms
            this.latitude = Formatter.coordinate(latitude);
            this.longitude = Formatter.coordinate(longitude);
            this.speed = Formatter.speed(speed);
            this.accuracy = accuracy; // already in cm
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

        public int getSpeed() {
            return speed;
        }

        public int getAccuracy() {
            return accuracy;
        }
    }
}
