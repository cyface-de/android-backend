package de.cyface.synchronization.serialization.proto;

import static de.cyface.persistence.serialization.Point3dType.ACCELERATION;
import static de.cyface.persistence.serialization.Point3dType.DIRECTION;
import static de.cyface.persistence.serialization.Point3dType.ROTATION;

import org.apache.commons.lang3.Validate;

import de.cyface.persistence.serialization.Point3dType;

public class DeFormatter {

    /**
     * Converts the number from the format expected by the Cyface ProtoBuf serializer.
     *
     * @param formatted the formatted number, e.g. 51_012345 or 13_012300
     * @return the coordinate-part, e.g.: 51.012345 or 13.012300
     */
    private static double coordinate(int formatted) {
        return formatted / 1_000_000.0;
    }

    /**
     * Converts the number from the format expected by the Cyface ProtoBuf serializer.
     *
     * @param formatted the formatted number, e.g. 11_00 cm/s
     * @return the speed in m/s, e.g.: 11.0m/s
     */
    private static double speed(int formatted) {
        return formatted / 100.0;
    }

    /**
     * Converts the number from the format expected by the Cyface ProtoBuf serializer.
     *
     * @param formatted the acceleration value in m/s^2, e.g.: +9.81 m/s (earth gravity)
     * @return the formatted number, e.g. 9_810 mm/s^2
     */
    private static float acceleration(int formatted) {
        return formatted / 1_000.0f;
    }

    /**
     * Converts the number from the format expected by the Cyface ProtoBuf serializer.
     *
     * @param formatted the formatted number, e.g. 83 rad/1000s (not /ms!)
     * @return the rotation value in rad/s, e.g.: 0.083 rad/s
     */
    private static float rotation(int formatted) {
        return formatted / 1_000.0f;
    }

    /**
     * Converts the number from the format expected by the Cyface ProtoBuf serializer.
     *
     * @param formatted the formatted number, e.g. 67 µT/100 (unit: 10 nT)
     * @return the direction value in µT, e.g.: 0.67 µT
     */
    private static float direction(int formatted) {
        return formatted / 100.0f;
    }

    public static class Location {
        private final long timestamp;
        private final double latitude;
        private final double longitude;
        private final double speed;
        private final int accuracy;

        public Location(long timestamp, int latitude, int longitude, int speed, int accuracy) {
            this.timestamp = timestamp; // already in ms
            this.latitude = DeFormatter.coordinate(latitude);
            this.longitude = DeFormatter.coordinate(longitude);
            this.speed = DeFormatter.speed(speed);
            this.accuracy = accuracy; // already in cm
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getSpeed() {
            return speed;
        }

        public int getAccuracy() {
            return accuracy;
        }
    }

    public static class Point3d {
        private final Point3dType type;
        private final long timestamp;
        private final float x;
        private final float y;
        private final float z;

        public Point3d(Point3dType type, long timestamp, int x, int y, int z) {
            Validate.isTrue(type.equals(ACCELERATION) || type.equals(ROTATION) || type.equals(DIRECTION));
            this.timestamp = timestamp; // already in ms
            this.x = type == ACCELERATION ? acceleration(x) : type == ROTATION ? rotation(x) : direction(x);
            this.y = type == ACCELERATION ? acceleration(y) : type == ROTATION ? rotation(y) : direction(y);
            this.z = type == ACCELERATION ? acceleration(z) : type == ROTATION ? rotation(z) : direction(z);
            this.type = type;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Point3dType getType() {
            return type;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public float getZ() {
            return z;
        }
    }
}
