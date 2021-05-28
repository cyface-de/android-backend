package de.cyface.persistence.serialization.proto;

import static de.cyface.persistence.serialization.Point3dType.ACCELERATION;
import static de.cyface.persistence.serialization.Point3dType.DIRECTION;
import static de.cyface.persistence.serialization.Point3dType.ROTATION;

import org.apache.commons.lang3.Validate;

import de.cyface.persistence.serialization.Point3dType;

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

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param value the acceleration value in m/s^2, e.g.: +9.81 m/s (earth gravity)
     * @return the formatted number, e.g. 9_810 mm/s^2
     */
    private static int acceleration(float value) {
        final long converted = Math.round(value * 1_000);
        // noinspection ConstantConditions
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param value the rotation value in rad/s, e.g.: 0.083 rad/s
     * @return the formatted number, e.g. 83 rad/1000s (not /ms!)
     */
    private static int rotation(float value) {
        final long converted = Math.round(value * 1_000);
        // noinspection ConstantConditions
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param value the direction value in µT, e.g.: 0.67 µT
     * @return the formatted number, e.g. 67 µT/100 (unit: 10 nT)
     */
    private static int direction(float value) {
        final long converted = Math.round(value * 100);
        // noinspection ConstantConditions
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

    public static class Point3d {
        private final long timestamp;
        private final int x;
        private final int y;
        private final int z;

        public Point3d(Point3dType type, long timestamp, float x, float y, float z) {
            Validate.isTrue(type.equals(ACCELERATION) || type.equals(ROTATION) || type.equals(DIRECTION));
            this.timestamp = timestamp; // already in ms
            this.x = type == ACCELERATION ? acceleration(x) : type == ROTATION ? rotation(x) : direction(x);
            this.y = type == ACCELERATION ? acceleration(y) : type == ROTATION ? rotation(y) : direction(y);
            this.z = type == ACCELERATION ? acceleration(z) : type == ROTATION ? rotation(z) : direction(z);
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
