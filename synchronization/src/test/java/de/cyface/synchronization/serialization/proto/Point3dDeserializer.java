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
package de.cyface.synchronization.serialization.proto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.cyface.persistence.model.Point3d;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dType;
import de.cyface.persistence.serialization.proto.Formatter;
import de.cyface.protos.model.Accelerations;
import de.cyface.protos.model.Directions;
import de.cyface.protos.model.Rotations;

/**
 * Deserializes sensor data from the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class Point3dDeserializer {

    /**
     * Deserializes acceleration data from the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param entries the data to deserialize
     * @return the deserialized entries
     */
    public static List<Point3d> deserialize(Accelerations entries) {

        final List<Formatter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Formatter.Point3d entry = new Formatter.Point3d(entries.getTimestamp(i), entries.getX(i),
                    entries.getY(i),
                    entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.ACCELERATION);
    }

    /**
     * Deserializes rotation data from the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param entries the data to deserialize
     * @return the deserialized entries
     */
    public static List<Point3d> deserialize(Rotations entries) {

        final List<Formatter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Formatter.Point3d entry = new Formatter.Point3d(entries.getTimestamp(i), entries.getX(i),
                    entries.getY(i),
                    entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.ROTATION);
    }

    /**
     * Deserializes direction data from the {@link MeasurementSerializer#TRANSFER_FILE_FORMAT_VERSION}.
     *
     * @param entries the data to deserialize
     * @return the deserialized entries
     */
    public static List<Point3d> deserialize(Directions entries) {

        final List<Formatter.Point3d> list = new ArrayList<>();
        for (int i = 0; i < entries.getTimestampCount(); i++) {
            final Formatter.Point3d entry = new Formatter.Point3d(entries.getTimestamp(i), entries.getX(i),
                    entries.getY(i),
                    entries.getZ(i));
            list.add(entry);
        }

        return deserialize(list, Point3dType.DIRECTION);
    }

    private static List<Point3d> deserialize(List<Formatter.Point3d> entries, Point3dType type) {

        // The de-offsetter must be initialized once for each location
        final Point3dDeOffsetter deOffsetter = new Point3dDeOffsetter();

        return entries.stream().map(entry -> {

            // The proto serialized comes in a different format and in offset-format
            final Formatter.Point3d offsets = new Formatter.Point3d(entry.getTimestamp(), entry.getX(), entry.getY(),
                    entry.getZ());
            final Formatter.Point3d absolutes = deOffsetter.absolute(offsets);

            return DeFormatter.deFormat(type, absolutes);

        }).collect(Collectors.toList());
    }
}