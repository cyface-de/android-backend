/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.persistence.serialization.proto;

import static de.cyface.persistence.serialization.Point3dType.ACCELERATION;
import static de.cyface.persistence.serialization.Point3dType.DIRECTION;
import static de.cyface.persistence.serialization.Point3dType.ROTATION;

import de.cyface.persistence.serialization.Point3dType;
import de.cyface.utils.Validate;

/**
 * Formatter which formats sensor- or location point attributes to the unit expected by the Cyface ProtoBuf serializer.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class Formatter {

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param coordinate the coordinate-part, e.g.: 51.012345 or 13.012300
     * @return the formatted number, e.g. 51_012345 or 13_012300
     */
    private static int coordinate(double coordinate) {
        final long converted = Math.round(coordinate * 1_000_000.);
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    /**
     * Converts the number into the format expected by the Cyface ProtoBuf serializer.
     *
     * @param metersPerSecond the speed in m/s, e.g.: 11.0m/s
     * @return the formatted number, e.g. 11_00 cm/s
     */
    private static int speed(double metersPerSecond) {
        final long converted = Math.round(metersPerSecond * 100.);
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
        final long converted = Math.round(value * 1_000.);
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
        final long converted = Math.round(value * 1_000.);
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
        final long converted = Math.round(value * 100.);
        Validate.isTrue(converted <= Integer.MAX_VALUE);
        return (int)converted;
    }

    /**
     * {@link de.cyface.persistence.model.GeoLocation} attributes in the format and units expected by the Cyface
     * ProtoBug serializer.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 7.0.0
     */
    public static class Location {
        private final long timestamp;
        private final int latitude;
        private final int longitude;
        private final int speed;
        private final int accuracy;

        /**
         * Constructs a fully initialized instance of this class.
         *
         * @param timestamp the timestamp in milliseconds
         * @param latitude the latitude in the format: 51.012345
         * @param longitude the longitude in the format: 13.012300
         * @param speed the speed in m/s
         * @param accuracy the accuracy in cm
         */
        public Location(long timestamp, double latitude, double longitude, double speed, int accuracy) {
            this.timestamp = timestamp; // already in ms
            this.latitude = Formatter.coordinate(latitude);
            this.longitude = Formatter.coordinate(longitude);
            this.speed = Formatter.speed(speed);
            this.accuracy = accuracy; // already in cm
        }

        /**
         * Constructs a fully initialized instance of this class.
         *
         * @param timestamp the timestamp in milliseconds
         * @param latitude the latitude in the format 51_012345 (i.e. 51.012345
         * @param longitude the longitude in the format 13_012300 (i.e. 13.012300)
         * @param speed the speed in the format 11_00 cm/s (i.e. 11.0m/s)
         * @param accuracy the accuracy in cm
         */
        public Location(long timestamp, int latitude, int longitude, int accuracy, int speed) {
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.speed = speed;
        }

        /**
         * @return The Unit timestamp in milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return The latitude coordinate in the format 51_012345 (i.e. 51.012345)
         */
        public int getLatitude() {
            return latitude;
        }

        /**
         * @return The longitude coordinate in the format 13_012300 (i.e. 13.012300)
         */
        public int getLongitude() {
            return longitude;
        }

        /**
         * @return The speed in the format 11_00 cm/s (i.e. 11.0m/s)
         */
        public int getSpeed() {
            return speed;
        }

        /**
         * @return The accuracy in cm.
         */
        public int getAccuracy() {
            return accuracy;
        }
    }

    /**
     * {@link de.cyface.persistence.model.Point3d} attributes in the format and units expected by the Cyface
     * ProtoBug serializer.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 7.0.0
     */
    public static class Point3d {
        private final long timestamp;
        private final int x;
        private final int y;
        private final int z;

        /**
         * Constructs a fully initialized instance of this class.
         *
         * @param type the sensor data type
         * @param timestamp the timestamp in milliseconds
         * @param x the x-value of the sensor point. Unit: m/s^2 for acceleration, rad/s for rotation, µT for direction.
         * @param y the y-value of the sensor point. Unit: m/s^2 for acceleration, rad/s for rotation, µT for direction.
         * @param z the z-value of the sensor point. Unit: m/s^2 for acceleration, rad/s for rotation, µT for direction.
         */
        public Point3d(Point3dType type, long timestamp, float x, float y, float z) {
            Validate.isTrue(type.equals(ACCELERATION) || type.equals(ROTATION) || type.equals(DIRECTION));
            this.timestamp = timestamp; // already in ms
            this.x = type == ACCELERATION ? acceleration(x) : type == ROTATION ? rotation(x) : direction(x);
            this.y = type == ACCELERATION ? acceleration(y) : type == ROTATION ? rotation(y) : direction(y);
            this.z = type == ACCELERATION ? acceleration(z) : type == ROTATION ? rotation(z) : direction(z);
        }

        /**
         * Constructs a fully initialized instance of this class.
         *
         * @param timestamp the timestamp in milliseconds
         * @param x the x-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *            83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         * @param y the y-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *            83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         * @param z the z-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *            83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         */
        public Point3d(long timestamp, int x, int y, int z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * @return the Unit timestamp in milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return the x-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *         83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         */
        public int getX() {
            return x;
        }

        /**
         * @return the y-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *         83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         */
        public int getY() {
            return y;
        }

        /**
         * @return the z-value of the sensor point. Format: e.g. 9_810 mm/s^2 (i.e. +9.81 m/s) for acceleration,
         *         83 rad/1000s (not /ms!) (i.e. 0.083 rad/s) for rotation, 67 µT/100 (i.e. 0.67 µT) for direction.
         */
        public int getZ() {
            return z;
        }
    }
}